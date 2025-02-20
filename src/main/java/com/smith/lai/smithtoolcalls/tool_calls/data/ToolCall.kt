package com.smith.lai.smithtoolcalls.tool_calls.data

import kotlinx.serialization.Serializable


@Serializable
data class ToolCall(
    val tool: String,
    val arguments: String
)

@Serializable
data class ToolCallList(val calls: List<ToolCall>)