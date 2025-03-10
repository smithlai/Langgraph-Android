package com.smith.lai.smithtoolcalls.langgraph.nodes

import android.util.Log
import com.smith.lai.smithtoolcalls.langgraph.model.LLMWithTools
import com.smith.lai.smithtoolcalls.langgraph.node.Node
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState
import com.smith.lai.smithtoolcalls.langgraph.state.MessageRole

/**
 * 通用工具節點 - 可用於任何實現 GraphState 接口的狀態
 */
object ToolNodes {
    private const val DEBUG_TAG = "ToolNodes"

    /**
     * 創建通用工具節點，支持任何GraphState實現
     */
    fun <S : GraphState> createToolNode(llmwithtool: LLMWithTools): Node<S> {
        return object : Node<S>() {
            override suspend fun invoke(state: S): S {
                Log.d(DEBUG_TAG, "Tool node: processing with hasToolCalls=${state.hasToolCalls}")

                // 檢查必要條件
                if (!state.hasToolCalls || state.structuredLLMResponse == null) {
                    Log.e(DEBUG_TAG, "Tool node: no tool calls or structured response available")
                    return state.withError("No tool calls to process").withCompleted(true) as S
                }

                try {
                    // 直接使用結構化回應處理工具調用
                    val processingResult = llmwithtool.processLLMResponse(state.structuredLLMResponse!!)

                    // 檢查解析結果
                    if (processingResult.toolResponses.isEmpty()) {
                        Log.w(DEBUG_TAG, "Tool node: no valid tool responses found in result")
                        state.setHasToolCalls(false)
                        return state.withCompleted(true) as S
                    }

                    Log.d(DEBUG_TAG, "Tool node: processing ${processingResult.toolResponses.size} tool responses")
                    val outputs = processingResult.toolResponses.map { "${it.id}: ${it.output}" }
                    Log.d(DEBUG_TAG, "Tool outputs: ${outputs.joinToString("\n")}")

                    // 添加工具響應到狀態
                    state.toolResponses.addAll(processingResult.toolResponses)

                    // 創建後續提示
                    val followUpPrompt = processingResult.buildFollowUpPrompt()

                    // 添加工具消息作為用戶消息
                    state.addMessage(MessageRole.TOOL, followUpPrompt)

                    // 重置工具調用標誌
                    state.setHasToolCalls(false)

                    // 檢查是否應終止流程
                    if (processingResult.shouldTerminateFlow()) {
                        Log.d(DEBUG_TAG, "Tool node: tool requested to terminate flow")
                        return state.withCompleted(true) as S
                    }

                    return state as S
                } catch (e: Exception) {
                    Log.e(DEBUG_TAG, "Tool node: error processing tool calls", e)
                    return state.withError("Error processing tool calls: ${e.message}").withCompleted(true) as S
                }
            }
        }
    }
}