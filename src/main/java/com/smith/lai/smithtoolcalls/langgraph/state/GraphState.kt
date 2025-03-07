package com.smith.lai.smithtoolcalls.langgraph.state


/**
 * 泛型狀態接口，定義狀態必須具備的基本功能
 */
interface GraphState {
    val completed: Boolean
    val error: String?
    val stepCount: Int

    fun withCompleted(isCompleted: Boolean = true): GraphState
    fun withError(errorMessage: String): GraphState
    fun incrementStep(): GraphState
}

/**
 * 通用狀態條件
 */
object StateConditions {
    /**
     * 檢查狀態是否有工具調用
     * 根據命名約定查找hasToolCalls屬性
     */
    fun <S> hasToolCalls(): (S) -> Boolean = { state ->
        // 使用反射嘗試獲取hasToolCalls屬性
        try {
            val method = state!!.javaClass.getMethod("getHasToolCalls")
            method.invoke(state) as? Boolean ?: false
        } catch (e: Exception) {
            try {
                // 嘗試直接訪問hasToolCalls字段
                val field = state!!.javaClass.getDeclaredField("hasToolCalls")
                field.isAccessible = true
                field.get(state) as? Boolean ?: false
            } catch (e: Exception) {
                // 如果無法獲取，則返回false
                false
            }
        }
    }

    /**
     * 檢查狀態是否有錯誤
     */
    fun <S> hasError(): (S) -> Boolean = { state ->
        when (state) {
            is GraphState -> state.error != null
            else -> false
        }
    }

    /**
     * 檢查狀態是否已完成
     */
    fun <S> isComplete(): (S) -> Boolean = { state ->
        when (state) {
            is GraphState -> state.completed
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