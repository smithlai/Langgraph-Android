package com.smith.lai.smithtoolcalls.langgraph.node

import com.smith.lai.smithtoolcalls.langgraph.model.LLMWithTools
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState

/**
 * 工具節點 - 處理工具調用
 */
class ToolNode<S : GraphState>(protected val model: LLMWithTools) : Node<S>() {

    /**
     * 核心處理邏輯
     */
    override suspend fun invoke(state: S): S {
        // 獲取最後一條包含工具調用的消息
        val messageWithToolCall = state.getLastToolCallsMessage()!!

        // 處理工具調用，獲取工具回應消息列表
        val toolMessages = model.processToolCalls(messageWithToolCall, state.messages)

        // 將所有工具回應消息添加到狀態
        toolMessages.forEach { toolMessage ->
            state.addMessage(toolMessage)
        }

        // 工具處理後，不標記為完成，因為通常需要回到LLM節點
        return state
    }

    /**
     * 檢查是否可以處理該狀態
     */
    override fun canProcess(state: S): Boolean {
        return state.getLastToolCallsMessage() != null
    }

    /**
     * 處理跳過的情況
     */
    override fun skipProcessing(state: S): S {
        return state.withError("No tool calls to process").withCompleted(true) as S
    }
}