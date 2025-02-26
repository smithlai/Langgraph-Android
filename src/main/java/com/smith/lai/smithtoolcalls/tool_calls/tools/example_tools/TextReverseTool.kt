package com.smith.lai.smithtoolcalls.tool_calls.tools.example_tools

import com.smith.lai.smithtoolcalls.tool_calls.tools.BaseTool
import com.smith.lai.smithtoolcalls.tool_calls.tools.ToolAnnotation
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
}