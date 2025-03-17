package com.smith.lai.smithtoolcalls.langgraph.tools.example_tools

import com.smith.lai.smithtoolcalls.langgraph.response.ToolFollowUpMetadata
import com.smith.lai.smithtoolcalls.langgraph.tools.BaseTool
import com.smith.lai.smithtoolcalls.langgraph.tools.ToolAnnotation
import kotlinx.serialization.Serializable


@Serializable
data class TextReverseInput(
    val text: String
)

@ToolAnnotation(
    name = "text_reverse",
    description = "Reverse the input text",
    returnDescription = "The reversed text"
)
class TextReverseTool : BaseTool<TextReverseInput, String>() {
    override suspend fun invoke(input: TextReverseInput): String {
        return input.text.reversed()
    }
    override fun getFollowUpMetadata(response: String): ToolFollowUpMetadata {
        val customPrompt = """
The tool returned: "$response". 
Based on this information, continue answering the request.
"""

        return ToolFollowUpMetadata(
            requiresFollowUp = true,
//            shouldTerminateFlow = false,
            customFollowUpPrompt = customPrompt // 現在使用非空字串
        )
    }
}