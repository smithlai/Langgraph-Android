package com.smith.lai.smithtoolcalls

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smith.lai.smithtoolcalls.custom_data.ConversationAgent
import com.smith.lai.smithtoolcalls.custom_data.MyCustomState
import com.smith.lai.smithtoolcalls.langgraph.state.MessageRole
import com.smith.lai.smithtoolcalls.tools.example_tools.CalculatorTool
import com.smith.lai.smithtoolcalls.tools.example_tools.WeatherTool
import com.smith.lai.smithtoolcalls.tools.llm_adapter.Llama3_2_3B_LLMToolAdapter
import io.shubham0204.smollm.SmolLM
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4::class)
class LangGraphExampleTest {

    companion object {
        const val MODEL_PATH = "/data/local/tmp/llm/Llama-3.2-3B-Instruct-uncensored-Q4_K_M.gguf"
        const val DEBUG_TAG = "EnhancedLangGraph"
    }

    @get:Rule
    val testName = TestName()

    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    val packageName = "${appContext.packageName}"

    val toolRegistry = ToolRegistry()
    private val smolLM = SmolLM()

    suspend fun loadModel() {
        smolLM.close()
        val file = File(MODEL_PATH)
        Log.d(DEBUG_TAG, "${file.path} exists? ${file.exists()}")
        try {
            smolLM.create(
                file.path,
                0.1f,
                0.0f,
                false,
                2048
            )
            Log.d(DEBUG_TAG, "Model loaded successfully")
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "Failed to load model: ${e.message}", e)
        }
    }

    @Before
    fun setup() {
        Log.i(DEBUG_TAG, "========================================")
        Log.i(DEBUG_TAG, "STARTING TEST: ${testName.methodName}")
        Log.i(DEBUG_TAG, "========================================")
    }

//    @Test
    fun test_001_EnhancedConversationalAgent() {
        toolRegistry.register(CalculatorTool::class)
        toolRegistry.register(WeatherTool::class)

        runBlocking {
            try {
                // 加載模型和設置工具
                loadModel()
                toolRegistry.setLLMToolAdapter(Llama3_2_3B_LLMToolAdapter())

                // 使用新的統一代理創建圖
                val graph = ConversationAgent.createExampleWithTools<MyCustomState>(
                    model = smolLM,
                    toolRegistry = toolRegistry
                )

                // 創建初始狀態
                val initialState = MyCustomState().apply {
                    addMessage(MessageRole.USER, "What's 125 + 437 and what's the weather in San Francisco?")
                }

                // 執行圖 - 直接傳入狀態
                val result = graph.run(initialState)

                // 記錄和驗證結果
                Log.d(DEBUG_TAG, "Execution completed in ${result.executionDuration()}ms")
                Log.d(DEBUG_TAG, "Steps: ${result.stepCount}")
                val final_response = result.getLastAssistantMessage() ?: ""
                Log.d(DEBUG_TAG, "Final response (${final_response.length}): ${final_response}")

                Assert.assertTrue("Execution should complete", result.completed)
                Assert.assertNull("Should have no errors", result.error)

                // 檢查工具使用
                var usedCalculator = false
                var usedWeather = false

                for (response in result.toolResponses) {
                    val output = response.output.toString()

                    if (output == "562") {
                        usedCalculator = true
                    }

                    if (output.contains("San Francisco")) {
                        usedWeather = true
                    }
                }

                Log.d(DEBUG_TAG, "Used calculator: $usedCalculator")
                Log.d(DEBUG_TAG, "Used weather: $usedWeather")

                Assert.assertTrue("Should have used calculator", usedCalculator)
                Assert.assertTrue("Should have used weather tool", usedWeather)

            } catch (e: Exception) {
                Log.e(DEBUG_TAG, "Test failed", e)
                throw e
            }
        }
    }

    @Test
    fun test_002_SimpleConversationalAgent() {
        runBlocking {
            try {
                // 加載模型
                loadModel()
                toolRegistry.setLLMToolAdapter(Llama3_2_3B_LLMToolAdapter())

                // 使用簡單代理創建圖 - 不使用工具
                val graph = ConversationAgent.createExampleWithoutTools<MyCustomState>(
                    model = smolLM,
                    toolRegistry = toolRegistry
                )

                // 創建初始狀態
                val initialState = MyCustomState().apply {
                    addMessage(MessageRole.USER,"Tell me a short joke about programming")
                }

                // 執行圖
                val result = graph.run(initialState)

                // 驗證結果
                Log.d(DEBUG_TAG, "Simple agent execution completed in ${result.executionDuration()}ms")
                Log.d(DEBUG_TAG, "Steps: ${result.stepCount}")
                val final_response = result.getLastAssistantMessage() ?: ""
                Log.d(DEBUG_TAG, "Final response: ${final_response}")

                Assert.assertTrue("Execution should complete", result.completed)
                Assert.assertNull("Should have no errors", result.error)
                Assert.assertTrue("Should have response", final_response.isNotEmpty())
            } catch (e: Exception) {
                Log.e(DEBUG_TAG, "Test failed", e)
                throw e
            }
        }
    }

    @Test
    fun test_003_MultiTurnConversation() {
        toolRegistry.register(CalculatorTool::class)

        runBlocking {
            try {
                // 加載模型和設置工具
                loadModel()
                toolRegistry.setLLMToolAdapter(Llama3_2_3B_LLMToolAdapter())

                // 創建對話代理
                val graph = ConversationAgent.createExampleWithTools<MyCustomState>(
                    model = smolLM,
                    toolRegistry = toolRegistry
                )

                // 創建初始狀態
                val state = MyCustomState().apply {
                    addMessage(MessageRole.USER, "What is 42 + 17?")
                }

                // 第一輪對話
                var result = graph.run(state)
                Log.d(DEBUG_TAG, "Turn 1 response: ${result.getLastAssistantMessage()}")
                Assert.assertTrue("First turn should complete", result.completed)

                // 檢查是否使用了計算器
                val usedCalculator = result.toolResponses.any {
                    it.output.toString() == "59"
                }
                Assert.assertTrue("Should have used calculator", usedCalculator)

                // 第二輪對話 - 繼續使用相同狀態
                result.setHasToolCalls(false) // 重置工具調用標誌
                result.setStructuredLLMResponse(null) // 重置原始響應
                result.withCompleted(false) // 重置完成標誌
                result.addMessage(MessageRole.USER,"Now multiply that by 3")

                // 執行第二輪
                result = graph.run(result)
                Log.d(DEBUG_TAG, "Turn 2 response: ${result.getLastAssistantMessage()}")
                Assert.assertTrue("Second turn should complete", result.completed)

                // 檢查第二輪回應
                val finalResponse = result.getLastAssistantMessage() ?: ""
                Assert.assertTrue("Response should contain '177'",
                    finalResponse.contains("177") || result.toolResponses.any { it.output.toString() == "177" })

            } catch (e: Exception) {
                Log.e(DEBUG_TAG, "Test failed", e)
                throw e
            }
        }
    }

    @After
    fun clear() {
        toolRegistry.clear()
        smolLM.close()
        Log.i(DEBUG_TAG, "========================================")
        Log.i(DEBUG_TAG, "TEST COMPLETED: ${testName.methodName}")
        Log.i(DEBUG_TAG, "========================================")
    }
}