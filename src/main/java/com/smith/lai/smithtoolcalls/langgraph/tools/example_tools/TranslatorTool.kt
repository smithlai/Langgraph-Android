package com.smith.lai.smithtoolcalls.langgraph.tools.example_tools

import com.smith.lai.smithtoolcalls.langgraph.response.ToolFollowUpMetadata
import com.smith.lai.smithtoolcalls.langgraph.tools.BaseTool
import com.smith.lai.smithtoolcalls.langgraph.tools.ToolAnnotation
import kotlinx.serialization.Serializable

@Serializable
data class TranslatorInput(
    val text: String,
    val targetLanguage: String
)

@ToolAnnotation(
    name = "translate_text",
    description = "Translate text to another language",
    returnDescription = "The translated text"
)
class TranslatorTool : BaseTool<TranslatorInput, String>() {
    override suspend fun invoke(input: TranslatorInput): String {
        // 模拟翻译API调用
        val result = when (input.targetLanguage.lowercase()) {
            "chinese", "zh" -> "这是翻译后的文本"
            "french", "fr" -> "C'est le texte traduit"
            "spanish", "es" -> "Este es el texto traducido"
            else -> "This is the translated text"
        }
        return result
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