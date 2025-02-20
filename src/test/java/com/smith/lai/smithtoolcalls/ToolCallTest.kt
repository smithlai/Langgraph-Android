package com.smith.lai.smithtoolcalls

import com.smith.lai.smithtoolcalls.tool_calls.test.FakeLLM
import com.smith.lai.smithtoolcalls.tool_calls.test.FakeLLMNoParam
import com.smith.lai.smithtoolcalls.tool_calls.test.examples_tools.ToolExample1
import com.smith.lai.smithtoolcalls.tool_calls.test.examples_tools.ToolExample2
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.Rule
import org.junit.rules.TestName

// 測試類別
class ToolCallTest {
    @Rule
    @JvmField
    val testName = TestName()

    private val toolRegistry = ToolRegistry()


    @Test
    fun testRegister() {
        println("=== Running test: ${testName.methodName} ===")
        toolRegistry.autoRegister(listOf(ToolExample1::class, ToolExample2::class))
        val a = toolRegistry.listTools()
        a.forEach {
            println(it.first +"->"+it.second)
        }
        toolRegistry.clear()
        assertTrue(true)
    }
    @Test
    fun testRegister2() {
        println("=== Running test: ${testName.methodName} ===")
        toolRegistry.autoRegister("com.smith.lai.smithtoolcalls.tool_calls.data.examples_tools")
        val a = toolRegistry.listTools()
        a.forEach {
            println(it.first +"->"+it.second)
        }
        toolRegistry.clear()
        assertTrue(true)
    }
    @Test
    fun testSystemPrompts() {
        println("=== Running test: ${testName.methodName} ===")
        toolRegistry.autoRegister("com.smith.lai.smithtoolcalls.tool_calls.test.examples_tools")
        val sysprompt = toolRegistry.createSystemPrompt()

        println("testSystemPrompts " + sysprompt)
        toolRegistry.clear()
        assertTrue(true)
    }

    @Test
    fun testLLM() {
        println("=== Running test: ${testName.methodName} ===")
        val toolRegistry = ToolRegistry()
        toolRegistry.autoRegister("com.smith.lai.smithtoolcalls.tool_calls.test.examples_tools")

        val fakeLLM = FakeLLM()
        val system = toolRegistry.createSystemPrompt()
        val response = runBlocking {
            fakeLLM.generateResponse(system, "this is a test")
        }
        val result = runBlocking {
            println(response)
            toolRegistry.processLLMOutput(response)  // 解析並執行工具
        }
        print(result+"\n")
        toolRegistry.clear()
    }

    @Test
    fun testLLMNoParam() {
        println("=== Running test: ${testName.methodName} ===")
        val toolRegistry = ToolRegistry()
        toolRegistry.autoRegister("com.smith.lai.smithtoolcalls.tool_calls.test.examples_tools")

        val fakeLLM = FakeLLMNoParam()
        val system = toolRegistry.createSystemPrompt()
        val response = runBlocking {
            fakeLLM.generateResponse(system, "this is a test")
        }
        val result = runBlocking {
            println(response)
            toolRegistry.processLLMOutput(response)  // 解析並執行工具
        }
        print(result+"\n")
        toolRegistry.clear()
    }


}
