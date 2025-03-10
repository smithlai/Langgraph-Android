package com.smith.lai.smithtoolcalls.langgraph.node

import android.util.Log
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState

/**
 * 結束節點實現
 * 作為圖流程的終點
 *
 * @param completionMessage 完成消息
 * @param getDuration 從狀態中獲取執行時間的函數 (可選)
 * @param getFinalResponse 從狀態中獲取最終回應的函數 (可選)
 * @param getLastMessage 從狀態中獲取最後一條助手消息的函數 (可選)
 * @param setFinalResponse 設置狀態中的最終回應的函數 (可選)
 * @param logTag 日誌標籤
 */
class EndNode<S : GraphState>(
    private val completionMessage: String = "Arriving EndNode",
    private val getDuration: ((S) -> Long)? = null,
    private val getFinalResponse: ((S) -> String)? = null,
    private val getLastMessage: ((S) -> String?)? = null,
    private val setFinalResponse: ((S, String) -> Unit)? = null,
    private val logTag: String = TAG
) : Node<S>() {
    override suspend fun invoke(state: S): S {
        // 基本日誌
        Log.d(logTag, "End node: finalizing execution after ${state.stepCount} steps")

        // 如果提供了時間計算函數，則使用
        if (getDuration != null) {
            val duration = getDuration?.invoke(state)
            Log.d(logTag, "Execution time: ${duration}ms")
        }

        if (state.error != null) {
            Log.e(logTag, "End node: execution completed with error: ${state.error}")
        } else {
            Log.d(logTag, "End node: execution completed successfully: $completionMessage")
        }

        // 處理最終回應 (如果提供了相關函數)
        if (getFinalResponse != null && getLastMessage != null && setFinalResponse != null) {
            val finalResponse = getFinalResponse?.invoke(state)
            if (finalResponse!!.isEmpty()) {
                val lastMessage = getLastMessage!!.invoke(state)
                if (lastMessage != null) {
                    setFinalResponse!!.invoke(state, lastMessage)
                    Log.d(logTag, "End node: setting final response from last message")
                }
            }
        }

        return state.withCompleted(true) as S
    }

    companion object {
        private const val TAG = "EndNode"
    }
}