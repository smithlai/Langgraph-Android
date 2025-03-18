package com.smith.lai.toolcalls.langgraph.node

import android.util.Log
import com.smith.lai.toolcalls.langgraph.model.LLMWithTools
import com.smith.lai.toolcalls.langgraph.state.GraphState
import com.smith.lai.toolcalls.langgraph.state.Message

/**
 * LLM 節點 - 處理對LLM的調用
 * 只生成響應，完全不接觸狀態
 */
class LLMNode<S : GraphState>(protected val model: LLMWithTools) : Node<S>() {
    private val TAG = "LLMNode"

    /**
     * 核心處理邏輯 - 只專注於使用LLM生成回應
     */
    override suspend fun invoke(state: S): List<Message> {
        Log.d(TAG, "Generating LLM response")

        // 使用LLM處理消息並生成回應
        return listOf(model.invoke(state.messages))
    }
}