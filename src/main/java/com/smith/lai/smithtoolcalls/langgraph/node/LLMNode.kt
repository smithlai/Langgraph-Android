package com.smith.lai.smithtoolcalls.langgraph.node

import com.smith.lai.smithtoolcalls.langgraph.model.LLMWithTools
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState

/**
 * LLM 節點 - 處理對LLM的調用
 */
class LLMNode<S : GraphState>(private val model: LLMWithTools) : Node<S>() {
    override suspend fun invoke(state: S): S {
        try {
            // 檢查錯誤或空消息列表
            if (state.error != null || state.messages.isEmpty()) {
                return state.withCompleted(true) as S
            }

            // 直接將整個消息列表傳給 LLM 處理
            val responseMessage = model.invoke(state.messages)

            // 將回應添加到狀態
            state.addMessage(responseMessage)

            // 更新狀態完成標誌 (如果沒有工具調用則標記為完成)
            val hasToolCalls = responseMessage.hasToolCalls()
            return state.withCompleted(!hasToolCalls) as S

        } catch (e: Exception) {
            return state.withError("Error in LLM node: ${e.message}").withCompleted(true) as S
        }
    }
}