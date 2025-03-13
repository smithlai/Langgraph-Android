package com.smith.lai.smithtoolcalls.langgraph.node

import android.util.Log
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState

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
    override suspend fun invoke(state: S): Any? {
        Log.d(logTag, "Start node: initializing graph execution")
        // the beginning 沒有特定輸出
        return null
    }

    companion object {
        private const val TAG = "StartNode"
    }
}