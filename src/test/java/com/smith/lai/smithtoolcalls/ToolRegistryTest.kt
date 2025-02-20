package com.smith.lai.smithtoolcalls

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
// 測試類別
class ToolRegistryTest {
//    private val toolRegistry = ToolRegistry().apply { autoRegister_fortest() }
//
//    class DummyLlamaCpp {
//        fun chat(systemPrompt: String, userInput: String): String {
//            // 這裡應該是 llama.cpp 的推理邏輯
//            return "System: $systemPrompt\n\nUser: $userInput\n\nAI:"
//        }
//    }
//
//    @Test
//    fun testAutoRegister() {
//        val tools = toolRegistry.listTools()
//        assertTrue(tools.any { it.first == "tool_example1" })
//        assertTrue(tools.any { it.first == "tool_example2" })
//    }
//
//    @Test
//    fun testSystemPrompt() {
//        val systemPrompt = toolRegistry.createSystemPrompt()
//        println(systemPrompt)
//        // 傳入 LLM（使用 `llama.cpp` 的 API）
//        val llama = DummyLlamaCpp() // 假設這是你的 `llama.cpp` 封裝
//        val response = llama.chat(systemPrompt, "今天的天氣如何？")
//        assertEquals("", response)
//    }
//
//
//    @Test
//    fun testProcessLLMOutput_ValidTool(): Unit = runBlocking {
//        val result = processLLMOutput("CALL_TOOL: calculation_tool | test", toolRegistry)
//        assertEquals("計算結果: 4", result)
//    }
//
//    @Test
//    fun testProcessLLMOutput_InvalidTool() = runBlocking {
//        val result = processLLMOutput("CALL_TOOL: unknown_tool | test", toolRegistry)
//        assertEquals("錯誤：找不到工具 unknown_tool", result)
//    }
//
//    @Test
//    fun testProcessLLMOutput_NoToolCall() = runBlocking {
//        val result = processLLMOutput("這是一個普通回應", toolRegistry)
//        assertEquals("LLM 回應: 這是一個普通回應", result)
//    }
}
