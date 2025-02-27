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
import com.smith.lai.smithtoolcalls.tool_calls.tools.translator.Llama3_2_3B_LLMToolAdapter
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
    }


//    @Test
    fun test_000_LLMHello() {

        toolRegistry.register(CalculatorTool::class)
        runBlocking {
            runBlocking {
                loadModel()
            }

            val sb = StringBuilder()
            smolLM.addSystemPrompt("""
You are an expert in composing functions. You are given a question and a set of possible functions.
Based on the question, you will need to make one or more function/tool calls to achieve the purpose.
If none of the function can be used, point it out. If the given question lacks the parameters required by the function,
also point it out. You should only return the function call in tools call sections.

If you decide to invoke any of the function(s), you MUST put it in the format of [func_name1(params_name1=params_value1, params_name2=params_value2...), func_name2(params)]
You SHOULD NOT include any other text in the response.

Here is a list of functions in JSON format that you can invoke.

[
    {
        "name": "get_weather",
        "description": "Get weather info for places",
        "parameters": {
            "type": "dict",
            "required": [
                "city"
            ],
            "properties": {
                "city": {
                    "type": "string",
                    "description": "The name of the city to get the weather for"
                },
                "metric": {
                    "type": "string",
                    "description": "The metric for weather. Options are: celsius, fahrenheit",
                    "default": "celsius"
                }
            }
        }
    }
]
            """.trimIndent())
            smolLM.getResponse("What is the weather in SF and Seattle?").collect {
                sb.append(it)
            }

            val response = sb.toString()
            Log.d("${testName.methodName}","LLM Response: $response")
            Assert.assertTrue(response.length > 0)
        }
    }

//    @Test
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

//    @Test
    fun test_002_SystemPromptNoTool() {

        runBlocking {

            runBlocking {
                loadModel()
            }

            // 1. 添加系统提示
            toolRegistry.setTranslator(Llama3_2_3B_LLMToolAdapter())
            val system_prompt = toolRegistry.createSystemPrompt()
            smolLM.addSystemPrompt(system_prompt)
            Log.d("${testName.methodName}", "createSystemPrompt: ${system_prompt}")

            // 2. 添加用户查询
            smolLM.addUserMessage("請問現在幾點？")

            // 3. 获取助手响应（期望包含工具调用）
            val firstResponse = StringBuilder()
            smolLM.getResponse().collect {
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
//    @Test
    fun test_003_LlamaToolDate() {
        val tool = ToolToday()
        toolRegistry.register(ToolToday::class)
        runBlocking {
            // 加载模型
            runBlocking {
                loadModel()
            }

            // 1. 添加系统提示
            toolRegistry.setTranslator(Llama3_2_3B_LLMToolAdapter())
            val system_prompt = toolRegistry.createSystemPrompt()
            smolLM.addSystemPrompt(system_prompt)
            Log.d("${testName.methodName}", "createSystemPrompt: ${system_prompt}")

            // 2. 添加用户查询
            smolLM.addUserMessage("請問今天日期是幾號？")

            // 3. 获取助手响应（期望包含工具调用）
            val firstResponse = StringBuilder()
            smolLM.getResponse().collect {
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
//    @Test
    fun test_004_LlamaCalculator() {
        val tool = CalculatorTool()
        toolRegistry.register(CalculatorTool::class)
        runBlocking {
            // 加载模型
            runBlocking {
                loadModel()
            }

            // 1. 添加系统提示
            toolRegistry.setTranslator(Llama3_2_3B_LLMToolAdapter())
            val system_prompt = toolRegistry.createSystemPrompt()
            smolLM.addSystemPrompt(system_prompt)
            Log.d("${testName.methodName}", "createSystemPrompt: ${system_prompt}")

            // 2. 添加用户查询
            smolLM.addUserMessage("what is 123 + 456?")

            // 3. 获取助手响应（期望包含工具调用）
            val firstResponse = StringBuilder()
            smolLM.getResponse().collect {
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
    suspend fun toolcalls2(smolLM: SmolLM, toolRegistry: ToolRegistry, query: String, tag: String = ""): Boolean {
        // 1. 添加用戶查詢
        smolLM.addUserMessage(query)

        // 2. 獲取助手響應（期望包含工具調用）
        val firstResponse = StringBuilder()
        smolLM.getResponse().collect {
            firstResponse.append(it)
        }
        val assistantResponse = firstResponse.toString()
        Log.d("${testName.methodName}", "Assistant's $tag Response(tool_calls): ${assistantResponse}")

        // 3. 處理工具調用並獲取結果
        val processingResult = toolRegistry.processLLMResponse(assistantResponse)
        val toolResponses = processingResult.toolResponses

        // 確保有工具調用
        if (toolResponses.isNotEmpty() && processingResult.requiresFollowUp) {
            // 4. 構建工具回應 JSON
            val toolResponseJson = processingResult.getToolResponseJson()
            Log.d("${testName.methodName}", "Tool Response JSON ($tag): ${toolResponseJson}")

            // 5. 添加助手的工具調用消息
            smolLM.addAssistantMessage(assistantResponse)

            // 6. 將工具回應發送給助手
//            smolLM.addToolResults(toolResponseJson)

            // 7. 添加明確的用戶提示，提取工具結果的關鍵信息
            val toolOutput = toolResponses.firstOrNull()?.output?.toString() ?: ""
            smolLM.addUserMessage("The tool returned: \"$toolOutput\". Based on this information, continue answering the request.")

            // 8. 獲取助手基於工具結果的回應
            val secondResponse = StringBuilder()
            smolLM.getResponse().collect {
                secondResponse.append(it)
            }

            // 9. 記錄助手的後續回應
            Log.d("${testName.methodName}", "Assistant's $tag Response(Follow-up): ${secondResponse}")
            return true
        } else {
            return false
        }
    }
    @Test
    fun test_005_LlamaToolWithFollowup() {
        val weatherTool = WeatherTool()
        val calctool = CalculatorTool()
        toolRegistry.register(weatherTool::class)
        toolRegistry.register(calctool::class)
        runBlocking {
            // 加載模型
            runBlocking {
                loadModel()
            }

            // 1. 設置translator並添加更明確的系統提示
            val llmToolAdapter = Llama3_2_3B_LLMToolAdapter()

            toolRegistry.setTranslator(llmToolAdapter)
            val system_prompt = toolRegistry.createSystemPrompt()
            smolLM.addSystemPrompt(system_prompt)
            Log.d("${testName.methodName}", "createSystemPrompt: ${system_prompt}")

            var isToolcalled1 = toolcalls2(smolLM, toolRegistry,"What's the weather in San Francisco?", "1st")
            assertTrue(isToolcalled1)
            var isToolcalled2 = toolcalls2(smolLM, toolRegistry,"What is 24 + 26", "2nd")
            assertTrue(isToolcalled2)
        }
    }
    @After
    fun clear(){
        toolRegistry.clear()
        smolLM.close()
        println("====== ${testName.methodName} End ======")
    }

}

