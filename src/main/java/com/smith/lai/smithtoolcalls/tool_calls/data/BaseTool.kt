package com.smith.lai.smithtoolcalls.tool_calls.data

abstract class BaseTool<T> {
    abstract suspend fun invoke(input: T): String
}