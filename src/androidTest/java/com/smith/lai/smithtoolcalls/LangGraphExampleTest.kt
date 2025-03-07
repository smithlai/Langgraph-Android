package com.smith.lai.smithtoolcalls

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smith.lai.smithtoolcalls.custom_data.ConversationState
import com.smith.lai.smithtoolcalls.custom_data.ConversationNodes
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
        const val DEBUG_TAG = "LangGraphExample"
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

    @Test
    fun test_001_ConversationalAgent() {
        toolRegistry.register(CalculatorTool::class)
        toolRegistry.register(WeatherTool::class)

        runBlocking {
            try {
                // 加載模型和設置工具
                loadModel()
                toolRegistry.setLLMToolAdapter(Llama3_2_3B_LLMToolAdapter())

                // 創建圖
                val graph = ConversationNodes.createConversationalAgent<ConversationState>(
                    model = smolLM,
                    toolRegistry = toolRegistry,
                    createLLMNode = ConversationNodes::createLLMNode,
                    createToolNode = ConversationNodes::createToolNode
                )

                // 創建初始狀態 - 使用消息而非查詢
                val initialState = ConversationState().apply {
                    addUserInput("What's 125 + 437 and what's the weather in San Francisco?")
                }

                // 執行圖 - 直接傳入狀態
                val result = graph.run(initialState)

                // 記錄和驗證結果
                Log.d(DEBUG_TAG, "Execution completed in ${result.executionDuration()}ms")
                Log.d(DEBUG_TAG, "Steps: ${result.stepCount}")
                Log.d(DEBUG_TAG, "Final response length: ${result.finalResponse.length}")

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

    @After
    fun clear() {
        toolRegistry.clear()
        smolLM.close()
        Log.i(DEBUG_TAG, "========================================")
        Log.i(DEBUG_TAG, "TEST COMPLETED: ${testName.methodName}")
        Log.i(DEBUG_TAG, "========================================")
    }
}