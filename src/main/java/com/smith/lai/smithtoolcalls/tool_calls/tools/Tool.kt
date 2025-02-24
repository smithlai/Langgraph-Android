package com.smith.lai.smithtoolcalls.tool_calls.tools

import kotlinx.serialization.Serializable

@Serializable
data class ToolCallArguments(
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
    val tool_calls: List<ToolCallArguments>? = null,
    val id: String? = null,
    val type: String? = null,
    val function: FunctionCall? = null
) {
    fun toToolCallsList(): List<ToolCallArguments> {
        return when {
            // 如果收到工具調用數組格式
            tool_calls != null -> tool_calls

            // 如果收到單個工具調用格式
            id != null && function != null -> listOf(
                ToolCallArguments(
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

enum class ToolResponseType(val value: String) {
    FUNCTION("function"),
    DIRECT_RESPONSE("direct_response"),
    ERROR("error");

    override fun toString(): String = value

    companion object {
        fun fromString(value: String): ToolResponseType {
            return values().find { it.value == value } ?: FUNCTION
        }
    }
}

@Serializable
data class ToolResponse<T>(
    val id: String,
    val type: ToolResponseType = ToolResponseType.FUNCTION,
    val output: T
)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Tool(
    val name: String,
    val description: String,
    val returnDescription: String = ""
)