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
    description = "Add two integers together",
    returnDescription = "The sum of the two integers"
)
class CalculatorTool : BaseTool<CalculatorInput, Int>() {
    override suspend fun invoke(input: CalculatorInput): Int {
        val result = input.param1 + input.param2
        return result
    }
    override fun getFollowUpMetadata(response: Int): ToolFollowUpMetadata {
        return ToolFollowUpMetadata(
            requiresFollowUp = true,
            shouldTerminateFlow = false,
            customFollowUpPrompt = followup_prompt(response.toString())
        )
    }
}