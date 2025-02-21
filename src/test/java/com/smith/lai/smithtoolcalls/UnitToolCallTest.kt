package com.smith.lai.smithtoolcalls

import com.smith.lai.smithtoolcalls.dummy_tools.CalculatorTool
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestName

// 測試類別
class UnitToolCallTest {
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
        toolRegistry.scanTools("com.smith.lai.smithtoolcalls")
        val toolnames = toolRegistry.getToolNames()
        println(toolnames.joinToString(","))
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
            println("123 + 456 = ${response.output}")
            assertTrue(response.output== "579")
        }
    }
    @After
    fun clear(){
        toolRegistry.clear()
        println("====== ${testName.methodName} End ======")
    }


}
