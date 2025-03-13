package com.smith.lai.smithtoolcalls.langgraph.tools

import kotlinx.serialization.Serializable


@Serializable
data class ToolCallInfo(
    val id: String,
    val type: String = "function",
    val name: String,
    val arguments: String,
    var executed: Boolean = false
)
