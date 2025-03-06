package com.smith.lai.smithtoolcalls.langgraph.nodes

import android.util.Log
import com.smith.lai.smithtoolcalls.langgraph.GraphState
import com.smith.lai.smithtoolcalls.langgraph.node.Node

/**
 * 通用節點實現 - 提供泛型節點創建函數
 */
object GenericNodes {
    private const val TAG = "GenericNodes"

    /**
     * 創建通用的開始節點
     * 純粹的入口點，不對狀態內容做任何假設
     *
     * @param logTag 日誌標籤
     */
    fun <S : GraphState> createStartNode(
        logTag: String = TAG
    ): Node<S> {
        return object : Node<S> {
            override suspend fun invoke(state: S): S {
                Log.d(logTag, "Start node: initializing graph execution")

                // 重置步驟計數
                (state as GraphState).incrementStep()

                return state
            }
        }
    }

    /**
     * 創建通用的結束節點
     *
     * @param completionMessage 完成消息
     * @param getDuration 從狀態中獲取執行時間的函數 (可選)
     * @param getFinalResponse 從狀態中獲取最終回應的函數 (可選)
     * @param getLastMessage 從狀態中獲取最後一條助手消息的函數 (可選)
     * @param setFinalResponse 設置狀態中的最終回應的函數 (可選)
     * @param logTag 日誌標籤
     */
    fun <S : GraphState> createEndNode(
        completionMessage: String,
        getDuration: ((S) -> Long)? = null,
        getFinalResponse: ((S) -> String)? = null,
        getLastMessage: ((S) -> String?)? = null,
        setFinalResponse: ((S, String) -> Unit)? = null,
        logTag: String = TAG
    ): Node<S> {
        return object : Node<S> {
            override suspend fun invoke(state: S): S {
                // 基本日誌
                Log.d(logTag, "End node: finalizing execution after ${state.stepCount} steps")

                // 如果提供了時間計算函數，則使用
                if (getDuration != null) {
                    val duration = getDuration(state)
                    Log.d(logTag, "Execution time: ${duration}ms")
                }

                if (state.error != null) {
                    Log.e(logTag, "End node: execution completed with error: ${state.error}")
                } else {
                    Log.d(logTag, "End node: execution completed successfully: $completionMessage")
                }

                // 處理最終回應 (如果提供了相關函數)
                if (getFinalResponse != null && getLastMessage != null && setFinalResponse != null) {
                    val finalResponse = getFinalResponse(state)
                    if (finalResponse.isEmpty()) {
                        val lastMessage = getLastMessage(state)
                        if (lastMessage != null) {
                            setFinalResponse(state, lastMessage)
                            Log.d(logTag, "End node: setting final response from last message")
                        }
                    }
                }

                return state.withCompleted(true) as S
            }
        }
    }

    /**
     * 創建通用的通過節點 - 不執行任何操作，只是將狀態傳遞到下一個節點
     */
    fun <S : GraphState> createPassThroughNode(
        description: String = "pass-through",
        logTag: String = TAG
    ): Node<S> {
        return object : Node<S> {
            override suspend fun invoke(state: S): S {
                Log.d(logTag, "Pass-through node ($description): state forwarded")
                return state
            }
        }
    }
}