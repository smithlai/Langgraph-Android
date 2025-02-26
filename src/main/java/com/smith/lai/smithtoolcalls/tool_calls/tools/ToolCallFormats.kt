package com.smith.lai.smithtoolcalls.tool_calls.tools

import kotlinx.serialization.Serializable


@Serializable
data class ToolCallInfo(
    val id: String,
    val type: String = "function", // Llama 3.2 使用 "function"
    val function: FunctionCall
)

@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String // JSON string of arguments
)

@Serializable
data class ToolCallsArray(
    val tool_calls: List<ToolCallInfo>? = null,
    val id: String? = null,
    val type: String? = null,
    val function: FunctionCall? = null
) {
    fun toToolCallsList(): List<ToolCallInfo> {
        return when {
            // 如果收到工具調用數組格式
            tool_calls != null -> tool_calls

            // 如果收到單個工具調用格式
            id != null && function != null -> listOf(
                ToolCallInfo(
                    id = id,
                    type = type ?: "function",
                    function = function
                )
            )

            // 如果格式無效，返回空列表
            else -> emptyList()
        }
    }
}
