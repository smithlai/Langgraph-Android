package com.smith.lai.smithtoolcalls.langgraph.response

enum class FinishReason(val value: String) {
    STOP("stop"),
    TOOL_CALLS("tool_calls"),
    ERROR("error");

    override fun toString(): String = value
}
/**
 * Token使用統計
 */
data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)

