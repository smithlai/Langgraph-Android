package com.smith.lai.smithtoolcalls

import com.smith.lai.smithtoolcalls.tool_calls.data.BaseTool

import com.smith.lai.smithtoolcalls.tool_calls.test.examples_tools.CalculatorTool
import com.smith.lai.smithtoolcalls.tool_calls.test.examples_tools.ToolExample1
import com.smith.lai.smithtoolcalls.tool_calls.test.examples_tools.ToolExample2
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestName

// 測試類別
class ToolCallTest {
    @Rule
    @JvmField
    val testName = TestName()

    private val toolRegistry = ToolRegistry()

    @Before
    fun setup(){
        println("====== Running test: ${testName.methodName} ======")
    }
    @Test
    fun testRegister() {
        toolRegistry.scanTools("com.smith.lai.smithtoolcalls.tool_calls.test.examples_tools")
        val toolnames = toolRegistry.getToolNames()
        assertTrue(toolnames.size > 0)
    }

    @Test
    fun testCalculatorCall() {
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
            assertTrue(response.output== "579")
        }
    }
    @After
    fun clear(){
        toolRegistry.clear()
        println("====== ${testName.methodName} End ======")
    }


}
