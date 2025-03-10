package com.smith.lai.smithtoolcalls.langgraph.node

import android.util.Log
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState

/**
 * 開始節點實現
 * 作為圖流程的入口點
 *
 * @param logTag 日誌標籤
 */
class StartNode<S : GraphState>(
    private val logTag: String = TAG
) : Node<S> {
    override suspend fun invoke(state: S): S {
        Log.d(logTag, "Start node: initializing graph execution")

        // 重置步驟計數
        (state as GraphState).incrementStep()

        return state
    }

    companion object {
        private const val TAG = "StartNode"
    }
}