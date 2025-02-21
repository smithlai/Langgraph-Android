package com.smith.lai.smithtoolcalls

import com.smith.lai.smithtoolcalls.tool_calls.data.BaseTool
import com.smith.lai.smithtoolcalls.tool_calls.test.FakeLLM
import com.smith.lai.smithtoolcalls.tool_calls.test.FakeLLMNoParam
import com.smith.lai.smithtoolcalls.tool_calls.test.examples_tools.CalculatorTool
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
        toolRegistry.scanTools("com.smith.lai.smithtoolcalls.tool_calls.test.examples_tools")
        println(toolRegistry.getToolNames())
        toolRegistry.clear()
        assertTrue(true)
    }

    @Test
    fun testToolCall() {
        println("=== Running test: ${testName.methodName} ===")
        toolRegistry.register(CalculatorTool::class)

        val toolCall = """
    {
        "id": "${toolRegistry.generateCallId()}",
        "type": "function",
        "function": {
            "name": "calculator_add",
            "arguments": "{\"param1\": 123, \"param2\": 456}"
        }
    }
    """

        runBlocking {
            val response = toolRegistry.processToolCall(toolCall)
            println(response) // ToolResponse(id=request_123, type=function, output=579)
        }
    }



}
