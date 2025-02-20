package com.smith.lai.smithtoolcalls.tool_calls.data

abstract class BaseTool {
    abstract suspend fun invoke(input: String): String
}