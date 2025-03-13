package com.smith.lai.smithtoolcalls.langgraph.node

import android.util.Log
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState
import com.smith.lai.smithtoolcalls.langgraph.state.Message

/**
 * 開始節點實現
 * 作為圖流程的入口點
 */
class StartNode<S : GraphState>(
    private val logTag: String = TAG
) : Node<S>() {
    /**
     * 核心處理邏輯
     */
    override suspend fun invoke(state: S): List<Message> {
        Log.d(logTag, "Start node: initializing graph execution")
        return listOf()
    }

    companion object {
        private const val TAG = "StartNode"
    }
}