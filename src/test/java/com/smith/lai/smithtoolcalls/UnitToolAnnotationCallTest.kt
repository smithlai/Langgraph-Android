package com.smith.lai.smithtoolcalls

import com.smith.lai.smithtoolcalls.tools.ToolResponseType
import com.smith.lai.smithtoolcalls.tools.example_tools.CalculatorInput
import com.smith.lai.smithtoolcalls.tools.example_tools.CalculatorTool
import com.smith.lai.smithtoolcalls.tools.example_tools.TextReverseInput
import com.smith.lai.smithtoolcalls.tools.example_tools.TextReverseTool
import com.smith.lai.smithtoolcalls.tools.example_tools.ToolToday
import com.smith.lai.smithtoolcalls.tools.example_tools.WeatherTool
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.rules.TestName
import org.junit.runners.MethodSorters
import kotlin.reflect.cast

// 測試類別
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UnitToolAnnotationCallTest {
    companion object{
        val TOOL_PACKAGE= "com.smith.lai.smithtoolcalls.tools.example_tools"

    }
    @get:Rule
    val testName = TestName()

    private val toolRegistry = ToolRegistry()

    @Before
    fun setup(){
        println("vvvvvvvv Running test: ${testName.methodName} vvvvvvvv")
    }
    @Test
    fun test_000_TestRegister() {
        println("* ${testName.methodName} step 1")
        toolRegistry.register(CalculatorTool::class)
        toolRegistry.register(TextReverseTool::class)
        toolRegistry.register(WeatherTool::class)
        var toolnames = toolRegistry.getToolNames()
        println("Single Register: " + toolnames.joinToString(","))
        assertTrue(toolnames.size == 3)
        toolRegistry.clear()
        toolnames = toolRegistry.getToolNames()
        assertTrue(toolnames.size == 0)

        println("* ${testName.methodName} step 2")
        toolRegistry.register(listOf(CalculatorTool::class, TextReverseTool::class, WeatherTool::class))
        toolnames = toolRegistry.getToolNames()
        println("List Register: " + toolnames.joinToString(","))
        assertTrue(toolnames.size == 3)
        toolRegistry.clear()
        toolnames = toolRegistry.getToolNames()
        assertTrue(toolnames.size == 0)

        println("* ${testName.methodName} step 3")
        toolRegistry.scanTools(TOOL_PACKAGE)
        toolnames = toolRegistry.getToolNames()
        println("Scan Register: " + toolnames.joinToString(","))
        assertTrue(toolnames.size > 3)
        toolRegistry.clear()
        toolnames = toolRegistry.getToolNames()
        assertTrue(toolnames.size == 0)

    }
    @Test
    fun test_001_testNoTool() {
        val toolCall = "[invalid_tool()]"
//        val toolCall = """
//{
//    "tool_calls": [
//        {
//            "id": "${toolRegistry.generateCallId()}",
//            "type": "function",
//            "function": {
//                "name": "invalid_tool",
//                "arguments": "{}"
//            }
//        }
//    ]
//}
//"""


        runBlocking {
            val processingResult = toolRegistry.processLLMResponse(toolCall.toString())
            val toolResponses = processingResult.toolResponses
            println("${toolResponses.first().output}")
            println("${toolResponses.first().type}")
            assertTrue(toolResponses.first().type == ToolResponseType.ERROR)
        }
    }
    @Test
    fun test_002_testToolDate() {

        toolRegistry.scanTools(TOOL_PACKAGE)
        val tool = ToolToday()
        val toolCall = "[${tool.getToolAnnotation().name}()]"
//        val toolCall = """
//{
//    "tool_calls": [
//        {
//            "id": "${toolRegistry.generateCallId()}",
//            "type": "function",
//            "function": {
//                "name": "${tool.getToolAnnotation().name}",
//                "arguments": "{}"
//            }
//        }
//    ]
//}
//"""

        runBlocking {
            val answer = tool.getReturnType()?.cast(tool.invoke(Unit))
            val processingResult = toolRegistry.processLLMResponse(toolCall)
            val toolResponses = processingResult.toolResponses
            println("${toolResponses.first().output} == $answer")
            assertTrue(toolResponses.first().output == answer)
        }
    }
    @Test
    fun test_003_testTextReverseCall() {

        toolRegistry.scanTools(TOOL_PACKAGE)
        val testinput = "Hello World Kotlin"
        val tool = TextReverseTool()
        val toolCall = "[${tool.getToolAnnotation().name}(text=$testinput)]"
//        val toolCall = """
//{
//    "tool_calls": [
//        {
//            "id": "${toolRegistry.generateCallId()}",
//            "type": "function",
//            "function": {
//                "name": "${tool.getToolAnnotation().name}",
//                "arguments": "{\"text\": \"$testinput\"}"
//            }
//        }
//    ]
//}
//"""

        runBlocking {
            val answer = tool.getReturnType()?.cast(tool.invoke(TextReverseInput(testinput)))
            val processingResult = toolRegistry.processLLMResponse(toolCall)
            val toolResponses = processingResult.toolResponses
            println("$testinput => ${toolResponses.first().output}(${answer})")
            assertTrue(toolResponses.first().output == answer)
        }
    }
    @Test
    fun test_004_testCalculatorCall() {
        toolRegistry.scanTools(TOOL_PACKAGE)
        val tool = CalculatorTool()
        val toolCall = "[${tool.getToolAnnotation().name}(param1=42, param2=58)]"
//        val toolCall = """
//{
//    "tool_calls": [
//        {
//            "id": "${toolRegistry.generateCallId()}",
//            "type": "function",
//            "function": {
//                "name": "${tool.getToolAnnotation().name}",
//                "arguments": "{\"param1\": 42, \"param2\": 58}"
//            }
//        }
//    ]
//}
//"""

        runBlocking {
            val answer = tool.getReturnType()?.cast(tool.invoke(CalculatorInput(42,58)))
            val processingResult = toolRegistry.processLLMResponse(toolCall)
            val toolResponses = processingResult.toolResponses
            println("42 + 58 = ${toolResponses.first().output}($answer)")
            assertTrue(toolResponses.first().output == answer)
        }
    }

    @After
    fun clear(){
        toolRegistry.clear()
        println("^^^^^^^^  ${testName.methodName} End ^^^^^^^^")
    }


}
