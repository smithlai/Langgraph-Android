package com.smith.lai.smithtoolcalls.tool_calls.test.examples_tools

import com.smith.lai.smithtoolcalls.tool_calls.data.BaseTool
import com.smith.lai.smithtoolcalls.tool_calls.data.ToolAnnotation
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
data class Example2Params(val param1: Int, val param2: String)

@ToolAnnotation(name = "tool_example2", description = "tools for example2")
class ToolExample2 : BaseTool<Example1Params>() {
    override suspend fun invoke(input: Example1Params): String {
        delay(1000)
        return "tool_example2 回應: param1=${input.param1}, param2=${input.param2}\n"
    }
}

