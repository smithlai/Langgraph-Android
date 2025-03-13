package com.smith.lai.smithtoolcalls.langgraph.node

import com.smith.lai.smithtoolcalls.langgraph.model.LLMWithTools
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState

/**
 * LLM 節點 - 處理對LLM的調用
 */
class LLMNode<S : GraphState>(protected val model: LLMWithTools) : Node<S>() {

    /**
     * 核心處理邏輯
     */
    override suspend fun invoke(state: S): S {
        // 使用LLM處理消息並生成回應
        val responseMessage = model.invoke(state.messages)

        // 添加回應到狀態
        state.addMessage(responseMessage)

        // 檢查是否有工具調用並設置狀態
        val hasToolCalls = responseMessage.hasToolCalls()
        return state.withCompleted(!hasToolCalls) as S
    }

    /**
     * 檢查是否可以處理該狀態
     */
    override fun canProcess(state: S): Boolean {
        return state.messages.isNotEmpty()
    }
}