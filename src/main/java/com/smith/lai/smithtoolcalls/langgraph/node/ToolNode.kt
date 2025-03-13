package com.smith.lai.smithtoolcalls.langgraph.node

import android.util.Log
import com.smith.lai.smithtoolcalls.langgraph.model.LLMWithTools
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState

/**
 * 工具節點 - 處理工具調用
 */
class ToolNode<S : GraphState>(private val model: LLMWithTools) : Node<S>() {
    private val TAG = "ToolNode"

    override suspend fun invoke(state: S): S {
        try {
            // 獲取最後一條包含工具調用的消息
            val messageWithToolCall = state.getLastToolCallsMessage()

            if (messageWithToolCall == null) {
                Log.w(TAG, "No tool calls message found")
                return state.withError("No tool calls to process").withCompleted(true) as S
            }

            // 處理工具調用，獲取工具回應消息列表
            val toolMessages = model.processToolCalls(messageWithToolCall, state.messages)

            if (toolMessages.isEmpty()) {
                return state.withCompleted(true) as S
            }

            // 將所有工具回應消息添加到狀態
            for (toolMessage in toolMessages) {
                state.addMessage(toolMessage)
            }

            // 工具處理後，不標記為完成，因為通常需要回到LLM節點
            return state as S

        } catch (e: Exception) {
            Log.e(TAG, "Error processing tool call: ${e.message}", e)
            return state.withError("Error processing tool call: ${e.message}").withCompleted(true) as S
        }
    }
}