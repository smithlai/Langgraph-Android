package com.smith.lai.smithtoolcalls.tool_calls.test.examples_tools

import com.smith.lai.smithtoolcalls.tool_calls.data.BaseTool
import com.smith.lai.smithtoolcalls.tool_calls.data.Tool
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
data class Example1Params(val param1: Int, val param2: String)

@Tool(name = "tool_example1", description = "tools for example 1")
class ToolExample1 : BaseTool<Example1Params>() {
    override suspend fun invoke(input: Example1Params): String {
        delay(1000)
        return "tool_example1 回應: param1=${input.param1}(${input.param1.javaClass.name}), param2=${input.param2}(${input.param2.javaClass.name})\n"
    }
}

