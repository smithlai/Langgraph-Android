package com.smith.lai.smithtoolcalls.langgraph.state

import android.util.Log

/**
 * 通用狀態條件工具類
 * 用於外層流程控制
 */
object StateConditions {
    private const val TAG = "StateConditions"

    /**
     * 檢查狀態是否有工具調用
     */
    fun <S> hasToolCalls(): (S) -> Boolean = { state ->
        when (state) {
            is GraphState -> {
                val lastMessage = state.getLastAssistantMessage()
                val hasToolCalls = lastMessage?.hasToolCalls() == true

                if (hasToolCalls) {
                    Log.d(TAG, "狀態檢查: 發現工具調用")
                }

                hasToolCalls
            }
            else -> false
        }
    }

    /**
     * 檢查狀態是否有錯誤
     */
    fun <S> hasError(): (S) -> Boolean = { state ->
        when (state) {
            is GraphState -> {
                val hasError = state.error != null

                if (hasError) {
                    Log.d(TAG, "狀態檢查: 發現錯誤 - ${state.error}")
                }

                hasError
            }
            else -> false
        }
    }

    /**
     * 檢查狀態是否已完成
     */
    fun <S> isComplete(): (S) -> Boolean = { state ->
        when (state) {
            is GraphState -> {
                val isComplete = state.completed

                if (isComplete) {
                    Log.d(TAG, "狀態檢查: 流程已完成")
                }

                isComplete
            }
            else -> false
        }
    }

    /**
     * 檢查是否應繼續執行LLM-Tool循環
     * 如果沒有工具調用或已標記為完成，則不繼續
     */
    fun <S> shouldContinueLLMToolLoop(): (S) -> Boolean = { state ->
        when (state) {
            is GraphState -> {
                val lastMessage = state.getLastAssistantMessage()
                val hasToolCalls = lastMessage?.hasToolCalls() == true
                val isNotComplete = !state.completed

                val shouldContinue = hasToolCalls && isNotComplete
                Log.d(TAG, "狀態檢查: 是否繼續LLM-Tool循環 = $shouldContinue")

                shouldContinue
            }
            else -> false
        }
    }

    /**
     * 強制標記狀態為已完成
     * 用於確保狀態在某些條件下被標記為完成
     */
    fun <S> markAsComplete(): (S) -> Boolean = { state ->
        when (state) {
            is GraphState -> {
                state.withCompleted(true)
                Log.d(TAG, "狀態操作: 強制標記為已完成")
                true
            }
            else -> false
        }
    }

    /**
     * 組合多個條件，所有條件必須為真
     */
    fun <S> all(vararg conditions: (S) -> Boolean): (S) -> Boolean = { state ->
        conditions.all { it(state) }
    }

    /**
     * 組合多個條件，任一條件為真
     */
    fun <S> any(vararg conditions: (S) -> Boolean): (S) -> Boolean = { state ->
        conditions.any { it(state) }
    }

    /**
     * 否定條件
     */
    fun <S> not(condition: (S) -> Boolean): (S) -> Boolean = { state ->
        !condition(state)
    }
}