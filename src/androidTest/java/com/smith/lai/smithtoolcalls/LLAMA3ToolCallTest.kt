package com.smith.lai.smithtoolcalls

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smith.lai.smithtoolcalls.dummy_tools.CalculatorTool
import io.shubham0204.smollm.SmolLM
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.io.File


@RunWith(AndroidJUnit4::class)
class LLAMA3ToolCallTest {
    @get:Rule
    val testName = TestName()

    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    val package_name ="${appContext.packageName}"
    val MODEL_PATH = "/data/local/tmp/llm/Llama-3.2-3B-Instruct-uncensored-Q4_K_M.gguf"
    private lateinit var toolRegistry: ToolRegistry
    // 模拟LLM的工具调用输出
    private val smolLM = SmolLM()



    suspend fun loadModel() {
        // clear resources occupied by the previous model
        smolLM.close()
        val file = File(MODEL_PATH)
        print("${file.path} : ${file.exists()}\n")
        try {
            smolLM.create(
                file.path,
                0.05f,
                1.0f,
                false,
                2048
            )
            println("Model loaded")
        } catch (e: Exception) {
            Log.e("aaaaa", e.message.toString())

        }

    }

    @Before
    fun setup() {
        println("====== Running test: ${testName.methodName} ======")
        toolRegistry = ToolRegistry()
    }
    @Test
    fun testRegister() {

        toolRegistry.register(CalculatorTool::class)

        //        toolRegistry.scanTools("com.smith.lai.smithtoolcalls.dummy_tools") // not work in instrument test
        val toolnames = toolRegistry.getToolNames()
        println(toolnames.joinToString(","))
        Assert.assertTrue(toolnames.size > 0)
    }

//    @Test
    fun testLLM() {

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
            Log.e("${testName.methodName}","LLM Response: $response")
            Assert.assertTrue(response.length > 0)
        }
    }

    @Test
    fun testLlamaToolCalls() {

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
            smolLM.addUserMessage("请计算123加456等于多少？")

            // 3. 获取助手响应（期望包含工具调用）
            val firstResponse = StringBuilder()
            smolLM.getResponse("").collect {
                firstResponse.append(it)
            }
            Log.d("${testName.methodName}", "Assistant's First Response: ${firstResponse}")

            // 4. 處理工具調用並獲取結果
            val toolResponses = toolRegistry.handleToolExecution(firstResponse.toString())

            Log.d("${testName.methodName}", "${toolResponses}")

            // 5. 將工具執行結果添加到對話
            if (toolResponses.isNotEmpty()) {
                // 格式化工具執行結果
                Assert.assertTrue(toolResponses.get(0).output == "579")
            }else{
                Assert.assertTrue(false)
            }
        }
    }


    @After
    fun clear(){
        toolRegistry.clear()
        smolLM.close()
        println("====== ${testName.methodName} End ======")
    }
//
//    @Test
//    fun testCompleteToolCallFlow() {
//        // 1. 准备系统提示，包含工具信息
//        val systemPrompt = toolRegistry.createSystemPrompt()
//        println("===== 系统提示 =====")
//        println(systemPrompt)
//
//        // 2. 用户查询
//        val userQuery = "请计算42加58等于多少？"
//        println("\n===== 用户查询 =====")
//        println(userQuery)
//
//        // 3. 模拟LLM生成工具调用
//        val llmToolCallOutput = fakeLLM.generateToolCall(systemPrompt, userQuery)
//        println("\n===== LLM工具调用输出 =====")
//        println(llmToolCallOutput)
//
//        // 4. 处理工具调用并获取结果
//        val toolResponses = runBlocking {
//            val toolCalls = Json.decodeFromString<ToolCallsArray>(llmToolCallOutput)
//            toolCalls.tool_calls.map { toolRegistry.processToolCallInternal(it) }
//        }
//
//        // 将工具响应转换为LLM期望的格式
//        val formattedToolResponses = """
//        [
//            ${toolResponses.joinToString(",") { Json.encodeToString(it) }}
//        ]
//        """
//        println("\n===== 工具调用结果 =====")
//        println(formattedToolResponses)
//
//        // 5. 将工具调用结果传回LLM生成最终回答
//        val finalResponse = fakeLLM.generateFinalResponse(systemPrompt, userQuery, formattedToolResponses)
//        println("\n===== LLM最终回答 =====")
//        println(finalResponse)
//
//        // 6. 验证
//        assertEquals(100, toolResponses.first().output.toInt())
//    }
//
//    @Test
//    fun testMultipleToolCalls() {
//        // 注册更多工具
//        toolRegistry.register(WeatherTool::class)
//        toolRegistry.register(TranslatorTool::class)
//
//        // 模拟LLM进行多个工具调用的场景
//        val multiToolCallJson = """
//        {
//            "tool_calls": [
//                {
//                    "id": "call_calc_123",
//                    "type": "function",
//                    "function": {
//                        "name": "calculator_add",
//                        "arguments": "{\"param1\": 10, \"param2\": 20}"
//                    }
//                },
//                {
//                    "id": "call_weather_456",
//                    "type": "function",
//                    "function": {
//                        "name": "get_weather",
//                        "arguments": "{\"location\": \"Taipei\", \"unit\": \"celsius\"}"
//                    }
//                },
//                {
//                    "id": "translate_text_789",
//                    "type": "function",
//                    "function": {
//                        "name": "translate_text",
//                        "arguments": "{\"text\": \"xxxxxxxxxx\", \"targetLanguage\": \"zh\"}"
//                    }
//                }
//            ]
//        }
//        """
//
//        // 处理多个工具调用
//        val responses = runBlocking {
//            toolRegistry.processToolCalls(multiToolCallJson)
//        }
//
//        println("\n===== 多工具调用结果 =====")
//        responses.forEach { println(it) }
//
//        // 验证第一个工具（计算器）的结果
//        assertEquals("30", responses.first().output)
//    }
//    @After
//    fun clear(){
//        toolRegistry.clear()
//        println("====== ${testName.methodName} End ======")
//    }
}

