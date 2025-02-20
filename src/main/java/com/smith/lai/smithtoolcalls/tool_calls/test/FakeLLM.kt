package com.smith.lai.smithtoolcalls.tool_calls.test

import com.smith.lai.smithtoolcalls.ToolRegistry
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*

// Fake LLM 輸出格式
data class LLMResponse(val tool: String?, val arguments: String?)

class FakeLLM {
    suspend fun generateResponse(systemprompt:String, userinput:String): String {
        delay(500)  // 模擬延遲
        // 模擬的 LLM 輸出，會生成一個 ToolCall 的 JSON 格式
        return """
{
    "tool": "tool_example1",
    "arguments": "param1"
}
"""
    }
}

suspend fun processLLMOutput(output: String, toolRegistry: ToolRegistry): String {
    return try {
        val llmResponse = Json.decodeFromString<LLMResponse>(output)
        if (llmResponse.tool != null && llmResponse.arguments != null) {
            val tool = toolRegistry.getTool(llmResponse.tool)
            if (tool != null) {
                tool.invoke(llmResponse.arguments) // 呼叫工具
            } else {
                "錯誤：找不到工具 ${llmResponse.tool}"
            }
        } else {
            "LLM 回應: ${output}"
        }
    } catch (e: Exception) {
        "解析錯誤: ${e.message}"
    }
}


