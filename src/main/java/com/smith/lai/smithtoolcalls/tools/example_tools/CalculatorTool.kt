package com.smith.lai.smithtoolcalls.tools.example_tools

import com.smith.lai.smithtoolcalls.tools.BaseTool
import com.smith.lai.smithtoolcalls.tools.ToolAnnotation
import com.smith.lai.smithtoolcalls.tools.ToolFollowUpMetadata
import kotlinx.serialization.Serializable


@Serializable
data class CalculatorInput(
    val param1: Int,
    val param2: Int
)

@ToolAnnotation(
    name = "calculator_add",
    description = "Add two numbers together",
    returnDescription = "The sum of the two numbers"
)
class CalculatorTool : BaseTool<CalculatorInput, Int>() {
    override suspend fun invoke(input: CalculatorInput): Int {
        val result = input.param1 + input.param2
        return result
    }
    override fun getFollowUpMetadata(response: Int): ToolFollowUpMetadata {
        val customPrompt = """
The tool returned: "$response". 
Based on this information, continue answering the request.
"""

        return ToolFollowUpMetadata(
            requiresFollowUp = true,
            shouldTerminateFlow = false,
            customFollowUpPrompt = customPrompt // 現在使用非空字串
        )
    }
}