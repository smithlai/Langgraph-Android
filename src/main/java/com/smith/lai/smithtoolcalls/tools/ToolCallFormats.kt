package com.smith.lai.smithtoolcalls.tools

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
