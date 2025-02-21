package com.smith.lai.smithtoolcalls

import com.smith.lai.smithtoolcalls.dummy_tools.CalculatorTool
import com.smith.lai.smithtoolcalls.dummy_tools.TranslatorTool
import com.smith.lai.smithtoolcalls.dummy_tools.WeatherTool
import com.smith.lai.smithtoolcalls.tool_calls.data.ToolCallsArray
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.rules.TestName

// 模拟LLM的工具调用输出
class FakeLLM {
    // 模拟LLM生成工具调用JSON
    fun generateToolCall(systemPrompt: String, userQuery: String): String {
        // 在实际应用中，这里会调用真实的LLM API
        // 现在我们只是返回一个假的固定响应
        return """
        {
            "tool_calls": [
                {
                    "id": "call_123456789",
                    "type": "function",
                    "function": {
                        "name": "calculator_add",
                        "arguments": "{\"param1\": 42, \"param2\": 58}"
                    }
                }
            ]
        }
        """
    }

    // 模拟LLM处理工具调用结果并给出最终回答
    fun generateFinalResponse(systemPrompt: String, userQuery: String, toolResponse: String): String {
        // 在实际应用中，这里会将工具调用结果传给LLM并获取最终回答
        // 现在我们只是返回一个假的固定回答
        return "根据计算结果，42 + 58 = 100。所以答案是100。"
    }
}

// 完整的LLM+工具调用流程测试
class LLMToolCallTest {
    @Rule
    @JvmField
    val testName = TestName()

    private lateinit var toolRegistry: ToolRegistry
    private lateinit var fakeLLM: FakeLLM

    @Before
    fun setup() {
        println("====== Running test: ${testName.methodName} ======")
        toolRegistry = ToolRegistry()
        // 注册计算器工具
        toolRegistry.register(CalculatorTool::class)
        fakeLLM = FakeLLM()
    }

    @Test
    fun testCompleteToolCallFlow() {
        // 1. 准备系统提示，包含工具信息
        val systemPrompt = toolRegistry.createSystemPrompt()
        println("===== 系统提示 =====")
        println(systemPrompt)

        // 2. 用户查询
        val userQuery = "请计算42加58等于多少？"
        println("\n===== 用户查询 =====")
        println(userQuery)

        // 3. 模拟LLM生成工具调用
        val llmToolCallOutput = fakeLLM.generateToolCall(systemPrompt, userQuery)
        println("\n===== LLM工具调用输出 =====")
        println(llmToolCallOutput)

        // 4. 处理工具调用并获取结果
        val toolResponses = runBlocking {
            val toolCalls = Json.decodeFromString<ToolCallsArray>(llmToolCallOutput)
            toolCalls.tool_calls.map { toolRegistry.processToolCallInternal(it) }
        }

        // 将工具响应转换为LLM期望的格式
        val formattedToolResponses = """
        [
            ${toolResponses.joinToString(",") { Json.encodeToString(it) }}
        ]
        """
        println("\n===== 工具调用结果 =====")
        println(formattedToolResponses)

        // 5. 将工具调用结果传回LLM生成最终回答
        val finalResponse = fakeLLM.generateFinalResponse(systemPrompt, userQuery, formattedToolResponses)
        println("\n===== LLM最终回答 =====")
        println(finalResponse)

        // 6. 验证
        assertEquals(100, toolResponses.first().output.toInt())
    }

    @Test
    fun testMultipleToolCalls() {
        // 注册更多工具
        toolRegistry.register(WeatherTool::class)
        toolRegistry.register(TranslatorTool::class)

        // 模拟LLM进行多个工具调用的场景
        val multiToolCallJson = """
        {
            "tool_calls": [
                {
                    "id": "call_calc_123",
                    "type": "function",
                    "function": {
                        "name": "calculator_add",
                        "arguments": "{\"param1\": 10, \"param2\": 20}"
                    }
                },
                {
                    "id": "call_weather_456",
                    "type": "function",
                    "function": {
                        "name": "get_weather",
                        "arguments": "{\"location\": \"Taipei\", \"unit\": \"celsius\"}"
                    }
                },
                {
                    "id": "translate_text_789",
                    "type": "function",
                    "function": {
                        "name": "translate_text",
                        "arguments": "{\"text\": \"xxxxxxxxxx\", \"targetLanguage\": \"zh\"}"
                    }
                }
            ]
        }
        """

        // 处理多个工具调用
        val responses = runBlocking {
            toolRegistry.processToolCalls(multiToolCallJson)
        }

        println("\n===== 多工具调用结果 =====")
        responses.forEach { println(it) }

        // 验证第一个工具（计算器）的结果
        assertEquals("30", responses.first().output)
    }
    @After
    fun clear(){
        toolRegistry.clear()
        println("====== ${testName.methodName} End ======")
    }
}

