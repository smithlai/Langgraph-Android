package com.smith.lai.smithtoolcalls.tool_calls.test.examples_tools

import com.smith.lai.smithtoolcalls.tool_calls.data.BaseTool
import com.smith.lai.smithtoolcalls.tool_calls.data.Tool

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
class CalculatorTool : BaseTool<CalculatorInput>() {
    override suspend fun invoke(input: CalculatorInput): String {
        val result = input.param1 + input.param2
        return result.toString()
    }
}

