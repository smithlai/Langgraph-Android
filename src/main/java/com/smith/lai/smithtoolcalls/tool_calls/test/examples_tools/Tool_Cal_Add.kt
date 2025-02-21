package com.smith.lai.smithtoolcalls.tool_calls.test.examples_tools

import com.smith.lai.smithtoolcalls.tool_calls.data.BaseTool
import com.smith.lai.smithtoolcalls.tool_calls.data.ToolAnnotation
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Serializable
data class ToolCalcAddParam(val param1: Int, val param2: Int)

@ToolAnnotation(name = "tool_calc_add", description = "tools for example2")
class ToolCalcAdd : BaseTool<ToolCalcAddParam>() {
    override suspend fun invoke(input: ToolCalcAddParam): String {
        delay(1000)
        val answer = input.param1 + input.param2
        return "tool_calc_add 回應: ${input.param1} + ${input.param2} = $answer\n"
    }
}

