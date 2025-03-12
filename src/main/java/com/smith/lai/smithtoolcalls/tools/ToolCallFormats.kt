package com.smith.lai.smithtoolcalls.tools

import kotlinx.serialization.Serializable


@Serializable
data class ToolCallInfo(
    val id: String,
    val type: String = "function",
    val function: FunctionCall,
    var executed: Boolean = false	//todo
)

@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String
)
