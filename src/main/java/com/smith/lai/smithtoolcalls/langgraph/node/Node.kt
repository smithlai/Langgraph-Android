package com.smith.lai.smithtoolcalls.langgraph.node

import android.util.Log
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState

/**
 * 節點接口 - 定義圖中節點的基本行為
 * 節點處理特定類型的狀態並返回更新後的狀態
 */
abstract class Node<S> {
    companion object{
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
     * 包含完整的前置檢查、核心邏輯和錯誤處理
     */
    suspend fun process(state: S): S {
        // 检查状态是否已有错误
        if (state is GraphState && state.error != null) {
            Log.d(TAG, "${this.javaClass.simpleName}: 状态已有错误，跳过处理")
            return state
        }

        // 执行节点特定的前置检查
//        if (!canProcess(state)) {
//            Log.d(TAG, "${this.javaClass.simpleName}: 前置检查未通过，跳过处理")
//            return skipProcessing(state)
//        }

        try {
            // 执行核心处理逻辑
            Log.d(TAG, "${this.javaClass.simpleName}: 开始处理")
            val result = invoke(state)
            Log.d(TAG, "${this.javaClass.simpleName}: 处理完成")
            return result
        } catch (e: Exception) {
            // 处理异常
            Log.e(TAG, "${this.javaClass.simpleName}: 处理出错: ${e.message}", e)
            if (state is GraphState) {
                return state.withError("Error in ${this.javaClass.simpleName}: ${e.message}")
                    .withCompleted(true) as S
            }
            throw e
        }
    }

    /**
     * 核心處理邏輯 - 由子類實現
     * 子類只需關注核心業務邏輯，無需處理錯誤和前置檢查
     */
    protected abstract suspend fun invoke(state: S): S

//    /**
//     * 檢查是否可以處理該狀態
//     * 子類可以覆寫以提供額外的檢查邏輯
//     */
//    protected open fun canProcess(state: S): Boolean {
//        return true
//    }

    /**
     * 處理跳過的情況
     * 當canProcess返回false時調用
     */
//    protected open fun skipProcessing(state: S): S {
//        if (state is GraphState) {
//            return state.withCompleted(true) as S
//        }
//        return state
//    }
}