package com.smith.lai.smithtoolcalls.custom_data

import com.smith.lai.smithtoolcalls.langgraph.state.GraphState

/**
 * 更新後的會話狀態 - 繼承自AbstractGraphState
 * 可以添加會話特有的額外功能
 */
data class ConversationState(
    // 可以添加會話特有的屬性
    var customProperty: String = ""
) : GraphState() {

    // 可以添加會話特有的方法

    /**
     * 設置自定義屬性
     */
//    fun setCustomProperty(value: String): ConversationState {
//        customProperty = value
//        return this
//    }
}