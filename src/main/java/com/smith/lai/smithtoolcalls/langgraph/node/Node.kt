package com.smith.lai.smithtoolcalls.langgraph.node

import android.util.Log
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState
import com.smith.lai.smithtoolcalls.langgraph.state.Message

/**
 * 節點接口 - 定義圖中節點的基本行為
 * 節點只產生結果，不接觸狀態
 */
abstract class Node<S : GraphState> {
    companion object {
        /**
         * 標準節點常量
         */
        object NodeNames {
            const val START = "__start__"
            const val END = "__end__"
            const val TOOLS = "tools"
        }
    }

    private val TAG = "Node"

    /**
     * 執行節點處理流程 - 框架內部使用
     * 包含錯誤處理，將節點輸出轉發給外部
     */
    suspend fun process(state: S): List<Message> {
        try {
            // 執行核心處理邏輯
            Log.d(TAG, "${this.javaClass.simpleName}: 開始處理")

            // 調用核心邏輯獲取結果
            val result = invoke(state)

            Log.d(TAG, "${this.javaClass.simpleName}: 處理完成")
            return result
        } catch (e: Exception) {
            // 處理異常
            Log.e(TAG, "${this.javaClass.simpleName}: 處理出錯: ${e.message}", e)
            return listOf()
        }
    }

    /**
     * 核心處理邏輯 - 由子類實現
     * 子類只需關注核心業務邏輯，不接觸狀態
     */
    protected abstract suspend fun invoke(state: S): List<Message>
}