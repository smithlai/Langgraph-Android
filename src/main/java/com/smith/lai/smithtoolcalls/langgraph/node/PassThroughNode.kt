package com.smith.lai.smithtoolcalls.langgraph.node

import android.util.Log
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState

/**
 * 通過節點實現 - 不執行任何操作，只是將狀態傳遞到下一個節點
 *
 * @param description 節點描述
 * @param logTag 日誌標籤
 */
class PassThroughNode<S : GraphState>(
    private val description: String = "pass-through",
    private val logTag: String = TAG
) : Node<S>() {
    override suspend fun invoke(state: S): S {
        Log.d(logTag, "Pass-through node ($description): state forwarded")
        return state
    }

    companion object {
        private const val TAG = "PassThroughNode"
    }
}