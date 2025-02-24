package com.smith.lai.smithtoolcalls.tool_calls.data

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
    val tool_calls: List<ToolCallArguments>
)

@Serializable
data class ToolResponse(
    val id: String,
    val type: String = "function",
    val output: String
)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Tool(
    val name: String,
    val description: String,
    val returnDescription: String = ""
)