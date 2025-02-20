package com.smith.lai.smithtoolcalls.tool_calls.test

import com.smith.lai.smithtoolcalls.ToolRegistry
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*


class FakeLLM {
    suspend fun generateResponse(systemprompt:String, userinput:String): String {
        delay(500)  // 模擬延遲
        // 模擬的 LLM 輸出，會生成一個 ToolCall 的 JSON 格式
        return """
{
    "tool": "tool_example1",
    "arguments": { "param1": 123, "param2": "abc" }
}
"""
    }
}

class FakeLLMNoParam {
    suspend fun generateResponse(systemprompt:String, userinput:String): String {
        delay(500)  // 模擬延遲
        // 模擬的 LLM 輸出，會生成一個 ToolCall 的 JSON 格式
        return """
{
    "tool": "tool_example_noparam",
    "arguments": { "param1": 123, "param2": "abc" }
}
"""
    }
}

