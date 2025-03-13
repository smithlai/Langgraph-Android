package com.smith.lai.smithtoolcalls.langgraph.node

import android.util.Log
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState

/**
 * 結束節點實現
 * 作為圖流程的終點
 */
class EndNode<S : GraphState>(
    private val completionMessage: String = "Arriving EndNode",
    private val logTag: String = TAG
) : Node<S>() {
    /**
     * 核心處理邏輯
     */
    override suspend fun invoke(state: S): Any? {
        // 基本日誌
        Log.d(logTag, "End node: finalizing execution after ${state.stepCount} steps")

        // 執行時間信息
        val executionTime = state.executionDuration()
        Log.d(logTag, "Execution time: ${executionTime}ms")

        if (state.error != null) {
            Log.e(logTag, "End node: execution completed with error: ${state.error}")
        } else {
            Log.d(logTag, "End node: execution completed successfully: $completionMessage")
        }

        // 返回特殊標記，讓外層可以識別這是結束節點
        return "END"
    }

    companion object {
        private const val TAG = "EndNode"
    }
}