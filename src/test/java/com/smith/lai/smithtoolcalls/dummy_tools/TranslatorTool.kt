package com.smith.lai.smithtoolcalls.dummy_tools

import com.smith.lai.smithtoolcalls.tool_calls.data.BaseTool
import com.smith.lai.smithtoolcalls.tool_calls.data.Tool
import kotlinx.serialization.Serializable

@Serializable
data class TranslatorInput(
    val text: String,
    val targetLanguage: String
)

@Tool(
    name = "translate_text",
    description = "Translate text to another language",
    returnDescription = "The translated text"
)
class TranslatorTool : BaseTool<TranslatorInput>() {
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
}