package com.smith.lai.toolcalls.langgraph.node

import android.util.Log
import com.smith.lai.toolcalls.langgraph.model.LLMWithTools
import com.smith.lai.toolcalls.langgraph.state.GraphState
import com.smith.lai.toolcalls.langgraph.state.Message

/**
 * LLM 節點 - 處理對LLM的調用
 * 支持流式生成以更新UI
 */
class LLMNode<S : GraphState>(
    protected val model: LLMWithTools,
    private var onProgressCallback: (suspend (String) -> Unit)? = null
) : Node<S>() {
    private val TAG = "LLMNode"

    /**
     * 設置進度回調
     * 允許在節點創建後更新回調
     */
    fun setProgressCallback(callback: (suspend (String) -> Unit)?) {
        onProgressCallback = callback
        Log.d(TAG, "Progress callback has been ${if (callback == null) "cleared" else "set"}")
    }

    /**
     * 核心處理邏輯 - 直接使用LLMWithTools的流式回應功能
     */
    override suspend fun invoke(state: S): List<Message> {
        Log.d(TAG, "Generating LLM response with streaming ${if(onProgressCallback != null) "and callback" else "without callback"}")

        // 使用LLM處理消息並生成回應，傳遞流式回調
        val response = model.invoke(state.messages, onProgressCallback)

        // 直接返回LLM生成的消息
        return listOf(response)
    }
}