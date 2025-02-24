package com.smith.lai.smithtoolcalls

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smith.lai.smithtoolcalls.tool_calls.tools.ToolResponseType
import com.smith.lai.smithtoolcalls.tool_calls.tools.example_tools.CalculatorInput
import com.smith.lai.smithtoolcalls.tool_calls.tools.example_tools.CalculatorTool
import com.smith.lai.smithtoolcalls.tool_calls.tools.example_tools.TextReverseTool
import com.smith.lai.smithtoolcalls.tool_calls.tools.example_tools.ToolToday
import com.smith.lai.smithtoolcalls.tool_calls.tools.example_tools.WeatherTool
import io.shubham0204.smollm.SmolLM
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import kotlin.reflect.cast

/* Note:
*   Copy model file to MODEL_PATH first
* */

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4::class)
class LLAMA3ToolCallTest {

    companion object{
        val TOOL_PACKAGE= "com.smith.lai.smithtoolcalls.tool_calls.tools.example_tools"
        val MODEL_PATH = "/data/local/tmp/llm/Llama-3.2-3B-Instruct-uncensored-Q4_K_M.gguf"

    }
    @get:Rule
    val testName = TestName()

    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    val package_name ="${appContext.packageName}"

    val toolRegistry = ToolRegistry()
    // 模拟LLM的工具调用输出
    private val smolLM = SmolLM()



    suspend fun loadModel() {
        // clear resources occupied by the previous model
        smolLM.close()
        val file = File(MODEL_PATH)
        print("${file.path} exists? ${file.exists()}\n")
        try {
            smolLM.create(
                file.path,
                0.1f,
                0.0f,
                false,
                2048
            )
            println("Model loaded")
        } catch (e: Exception) {
            Log.e("loadModel", e.message.toString())

        }

    }

    @Before
    fun setup(){
        println("vvvvvvvv Running test: ${testName.methodName} vvvvvvvv")
        println(package_name)

    }


    @Test
    fun test_000_LLMHello() {

        toolRegistry.register(CalculatorTool::class)
        runBlocking {
            runBlocking {
                loadModel()
            }

            val sb = StringBuilder()
            smolLM.getResponse("Hello?").collect {
                sb.append(it)
            }

            val response = sb.toString()
            Log.d("${testName.methodName}","LLM Response: $response")
            Assert.assertTrue(response.length > 0)
        }
    }

    @Test
    fun test_001_TestRegister() {
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
//         //package scan not available in instrumented test
//        println("* ${testName.methodName} step 3: ${TOOL_PACKAGE}")
//        toolRegistry.scanTools(TOOL_PACKAGE)
//        toolnames = toolRegistry.getToolNames()
//        println("Scan Register: " + toolnames.joinToString(",") + "(${toolnames.size})")
//        assertTrue(toolnames.size > 3)
//        toolRegistry.clear()
//        toolnames = toolRegistry.getToolNames()
//        assertTrue(toolnames.size == 0)

    }

    @Test
    fun test_002_SystemPromptNoTool() {

        runBlocking {

            runBlocking {
                loadModel()
            }

            // 1. 添加系统提示
            smolLM.addSystemPrompt(toolRegistry.createSystemPrompt())
            Log.d("${testName.methodName}", "createSystemPrompt: ${toolRegistry.createSystemPrompt()}")

            // 2. 添加用户查询
            smolLM.addUserMessage("請問現在幾點？")

            // 3. 获取助手响应（期望包含工具调用）
            val firstResponse = StringBuilder()
            smolLM.getResponse("").collect {
                firstResponse.append(it)
            }
            Log.d("${testName.methodName}", "Assistant's First Response: ${firstResponse}")

            // 4. 處理工具調用並獲取結果
            val toolResponses = toolRegistry.handleToolExecution(firstResponse.toString())

            // 5. 工具不存在執行結果
            Log.d("${testName.methodName}", "Response: ${toolResponses.first().output}")
            assertTrue(toolResponses.first().type == ToolResponseType.DIRECT_RESPONSE)

        }
    }
    @Test
    fun test_003_LlamaToolDate() {
        val tool = ToolToday()
        toolRegistry.register(ToolToday::class)
        runBlocking {
            // 加载模型
            runBlocking {
                loadModel()
            }

            // 1. 添加系统提示
            smolLM.addSystemPrompt(toolRegistry.createSystemPrompt())
            Log.d("${testName.methodName}", "createSystemPrompt: ${toolRegistry.createSystemPrompt()}")

            // 2. 添加用户查询
            smolLM.addUserMessage("請問今天日期是幾號？")

            // 3. 获取助手响应（期望包含工具调用）
            val firstResponse = StringBuilder()
            smolLM.getResponse("").collect {
                firstResponse.append(it)
            }
            Log.d("${testName.methodName}", "Assistant's First Response: ${firstResponse}")

            // 4. 處理工具調用並獲取結果
            val answer = tool.getReturnType()?.cast(tool.invoke(Unit))
            val toolResponses = toolRegistry.handleToolExecution(firstResponse.toString())

            // 5. 工具執行結果
            Log.d("${testName.methodName}", "Date:${toolResponses.first().output}($answer)")
            assertTrue(toolResponses.first().type == ToolResponseType.FUNCTION)
            assertTrue(toolResponses.first().output == answer)

        }
    }
    @Test
    fun test_004_LlamaCalculator() {
        val tool = CalculatorTool()
        toolRegistry.register(CalculatorTool::class)
        runBlocking {
            // 加载模型
            runBlocking {
                loadModel()
            }

            // 1. 添加系统提示
            smolLM.addSystemPrompt(toolRegistry.createSystemPrompt())
            Log.d("${testName.methodName}", "createSystemPrompt: ${toolRegistry.createSystemPrompt()}")

            // 2. 添加用户查询
            smolLM.addUserMessage("what is 123 + 456?")

            // 3. 获取助手响应（期望包含工具调用）
            val firstResponse = StringBuilder()
            smolLM.getResponse("").collect {
                firstResponse.append(it)
            }
            Log.d("${testName.methodName}", "Assistant's First Response: ${firstResponse}")

            // 4. 處理工具調用並獲取結果
            val answer = tool.getReturnType()?.cast(tool.invoke(CalculatorInput(123, 456)))
            val toolResponses = toolRegistry.handleToolExecution(firstResponse.toString())

            // 5. 工具執行結果
            Log.d("${testName.methodName}", "123+456=${toolResponses.first().output}($answer)")
            assertTrue(toolResponses.first().type == ToolResponseType.FUNCTION)
            assertTrue(toolResponses.first().output == answer)

        }
    }

    @After
    fun clear(){
        toolRegistry.clear()
        smolLM.close()
        println("====== ${testName.methodName} End ======")
    }

}

