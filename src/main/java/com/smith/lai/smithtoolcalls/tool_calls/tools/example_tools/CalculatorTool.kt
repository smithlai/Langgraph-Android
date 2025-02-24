package com.smith.lai.smithtoolcalls.tool_calls.tools.example_tools

import com.smith.lai.smithtoolcalls.tool_calls.tools.BaseTool
import com.smith.lai.smithtoolcalls.tool_calls.tools.Tool
import kotlinx.serialization.Serializable


@Serializable
data class CalculatorInput(
    val param1: Int,
    val param2: Int
)

@Tool(
    name = "calculator_add",
    description = "Add two numbers together",
    returnDescription = "The sum of the two numbers"
)
class CalculatorTool : BaseTool<CalculatorInput, Int>() {
    override suspend fun invoke(input: CalculatorInput): Int {
        val result = input.param1 + input.param2
        return result
    }
}