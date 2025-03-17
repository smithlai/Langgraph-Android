package com.smith.lai.smithtoolcalls

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smith.lai.smithtoolcalls.custom_data.ConversationAgent
import com.smith.lai.smithtoolcalls.custom_data.MyCustomState
import com.smith.lai.smithtoolcalls.langgraph.LangGraph
import com.smith.lai.smithtoolcalls.langgraph.model.SmolLMWithTools
import com.smith.lai.smithtoolcalls.langgraph.state.MessageRole
import com.smith.lai.smithtoolcalls.langgraph.tools.example_tools.CalculatorTool
import com.smith.lai.smithtoolcalls.langgraph.tools.example_tools.WeatherTool
import com.smith.lai.smithtoolcalls.langgraph.model.adapter.Llama3_2_3B_LLMToolAdapter
import com.smith.lai.smithtoolcalls.langgraph.node.LLMNode
import com.smith.lai.smithtoolcalls.langgraph.node.Node.Companion.NodeNames
import com.smith.lai.smithtoolcalls.langgraph.node.ToolNode
import com.smith.lai.smithtoolcalls.langgraph.state.StateConditions
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
    fun test_000_ToolNodeWithDirectBindTools() {
        runBlocking {
            try {
                // 加載模型
                loadModel()

                val llmwithTools = SmolLMWithTools(Llama3_2_3B_LLMToolAdapter(), smolLM)

                // 創建圖的基本結構
                val graphBuilder = LangGraph<MyCustomState>()

                // 創建節點
                val llmNode = LLMNode<MyCustomState>(llmwithTools)
                llmwithTools.bind_tools(CalculatorTool()).bind_tools(WeatherTool())

                val toolNode = ToolNode<MyCustomState>().bind_tools(CalculatorTool())
                    .bind_tools(WeatherTool())


                // 添加節點和邊
                graphBuilder.addStartNode()
                graphBuilder.addEndNode()
                graphBuilder.addNode("llm", llmNode)
                graphBuilder.addNode(NodeNames.TOOLS, toolNode)

                graphBuilder.addEdge(NodeNames.START, "llm")
                graphBuilder.addConditionalEdges(
                    "llm",
                    mapOf(
                        StateConditions.hasToolCalls<MyCustomState>() to NodeNames.TOOLS,
                        StateConditions.isComplete<MyCustomState>() to NodeNames.END
                    ),
                    defaultTarget = NodeNames.END
                )
                graphBuilder.addEdge(NodeNames.TOOLS, "llm")

                // 編譯圖
                val graph = graphBuilder.compile()

                // 創建初始狀態並執行
                val initialState = MyCustomState().apply {
                    addMessage(MessageRole.USER, "What's 42 + 18 and what's the weather in Tokyo?")
                }

                val result = graph.run(initialState)

                // 驗證結果
                Assert.assertTrue("Execution should complete", result.completed)
                Assert.assertNull("Should have no errors", result.error)

                // 檢查是否使用了工具
                val usedCalculator = result.getToolMessages().any {
                    it.toolResponse?.output.toString() == "60"
                }

                val usedWeather = result.getToolMessages().any {
                    it.toolResponse?.output.toString().contains("Tokyo")
                }

                Assert.assertTrue("Should have used calculator", usedCalculator)
                Assert.assertTrue("Should have used weather tool", usedWeather)

                Log.d(DEBUG_TAG, "Final response: ${result.getLastAssistantMessage()?.content}")

            } catch (e: Exception) {
                Log.e(DEBUG_TAG, "Test failed", e)
                throw e
            }
        }
    }

    @Test
    fun test_001_ConversationalWithTools() {
        runBlocking {
            try {
                // 加載模型和設置工具
                loadModel()

                val llmwithTools = SmolLMWithTools(Llama3_2_3B_LLMToolAdapter(), smolLM)

                // 創建工具實例
                val tools = listOf(CalculatorTool(), WeatherTool())

                // 創建圖
                val graph = ConversationAgent.createExampleWithTools<MyCustomState>(
                    model = llmwithTools,
                    tools = tools
                )

                // 創建初始狀態
                val initialState = MyCustomState().apply {
                    addMessage(MessageRole.USER, "What's 125 + 437 and what's the weather in San Francisco?")
                }

                // 執行圖
                val result = graph.run(initialState)

                // 記錄和驗證結果
                Log.d(DEBUG_TAG, "Execution completed in ${result.executionDuration()}ms")
                Log.d(DEBUG_TAG, "Steps: ${result.stepCount}")
                val final_response = result.getLastAssistantMessage()?.content ?: ""
                Log.d(DEBUG_TAG, "Final response (${final_response.length}): ${final_response}")

                Assert.assertTrue("Execution should complete", result.completed)
                Assert.assertNull("Should have no errors", result.error)

                // 檢查工具使用
                var usedCalculator = false
                var usedWeather = false

                for (response in result.getToolMessages()) {
                    val output = response.toolResponse?.output.toString()

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
    fun test_002_ConversationsWithoutTools() {
        runBlocking {
            try {
                loadModel()

                val llmwithTools = SmolLMWithTools(Llama3_2_3B_LLMToolAdapter(), smolLM)
//                llmwithTools.bind_tools(listOf(CalculatorTool::class, WeatherTool::class))

                // 使用新的統一代理創建圖
                val graph = ConversationAgent.createExampleWithoutTools<MyCustomState>(
                    model = llmwithTools
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
                val final_response = result.getLastAssistantMessage()?.content ?: ""
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

        runBlocking {
            try {
                loadModel()
                val llmwithTools = SmolLMWithTools(Llama3_2_3B_LLMToolAdapter(), smolLM)
                llmwithTools.bind_tools(listOf(CalculatorTool(), WeatherTool()))

                // 使用新的統一代理創建圖
                val graph = ConversationAgent.createExampleWithTools<MyCustomState>(
                    model = llmwithTools
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
                val usedCalculator = result.getToolMessages().any {
                    it.toolResponse?.output == "59"
                }
                Assert.assertTrue("Should have used calculator", usedCalculator)

                // 第二輪對話 - 繼續使用相同狀態
                // todo: Failed to answer the correct answer.
                result.addMessage(MessageRole.USER,"Now multiply that by 3")

                // 執行第二輪
                result = graph.run(result)
                Log.d(DEBUG_TAG, "Turn 2 response: ${result.getLastAssistantMessage()}")
                Assert.assertTrue("Second turn should complete", result.completed)

                // 檢查第二輪回應
                val finalResponse = result.getLastAssistantMessage()?.content ?: ""
                Assert.assertTrue("Response should contain '177'",
                    finalResponse.contains("177") || result.getToolMessages().any { it.content.toString() == "177" })

            } catch (e: Exception) {
                Log.e(DEBUG_TAG, "Test failed", e)
                throw e
            }
        }
    }

    @After
    fun clear() {
//        toolRegistry.clear()
        smolLM.close()
        Log.i(DEBUG_TAG, "========================================")
        Log.i(DEBUG_TAG, "TEST COMPLETED: ${testName.methodName}")
        Log.i(DEBUG_TAG, "========================================")
    }
}