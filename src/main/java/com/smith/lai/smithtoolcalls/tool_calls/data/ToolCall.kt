package com.smith.lai.smithtoolcalls.tool_calls.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


@Serializable
data class ToolCall(
    val tool: String,
    val arguments: JsonElement
//    Example:
//    {
//        "tool": "tool_example1",
//        "arguments": { "param1": 123, "param2": "abc" }
//    }
)

@Serializable
data class ToolCallList(val calls: List<ToolCall>)