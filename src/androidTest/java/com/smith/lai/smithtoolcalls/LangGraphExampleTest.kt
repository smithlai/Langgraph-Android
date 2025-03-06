package com.smith.lai.smithtoolcalls

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smith.lai.smithtoolcalls.custom_data.ConversationState
import com.smith.lai.smithtoolcalls.custom_data.LangGraphNodeFuncitons
import com.smith.lai.smithtoolcalls.langgraph.*
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
                // Load model and setup tools
                loadModel()
                toolRegistry.setLLMToolAdapter(Llama3_2_3B_LLMToolAdapter())

                // Create graph using factory with reusable node creators
                val graph = StateGraphFactory.createConversationalAgent<ConversationState>(
                    model = smolLM,
                    toolRegistry = toolRegistry,
                    createLLMNode = LangGraphNodeFuncitons::createLLMNode,
                    createToolNode = LangGraphNodeFuncitons::createToolNode,
                    createStartNode = { LangGraphNodeFuncitons.createStartNode() },
                    createEndNode = { msg -> LangGraphNodeFuncitons.createEndNode(msg) }
                )

                // Run with a test query
                val query = "What's 125 + 437 and what's the weather in San Francisco?"
                Log.d(DEBUG_TAG, "Running graph with query: $query")

                val initialState = ConversationState(query = query)
                val result = graph.run(initialState)

                // Log and verify results
                Log.d(DEBUG_TAG, "Execution completed in ${result.executionDuration()}ms")
                Log.d(DEBUG_TAG, "Steps: ${result.stepCount}")

                // 获取最终响应
                val finalResponse = result.finalResponse
                Log.d(DEBUG_TAG, "Final response length: ${finalResponse.length}")

                Assert.assertTrue("Execution should complete", result.completed)
                Assert.assertNull("Should have no errors", result.error)

                // 检查工具使用
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
    fun test_002_CustomNodeGraph() {
        toolRegistry.register(CalculatorTool::class)

        runBlocking {
            try {
                // Load model and setup tools
                loadModel()
                toolRegistry.setLLMToolAdapter(Llama3_2_3B_LLMToolAdapter())

                // Create graph with custom configuration
                val graph = StateGraphFactory.createCustomGraph<ConversationState> {
                    // Add nodes using predefined node creators
                    addNode("llm", LangGraphNodeFuncitons.createLLMNode(smolLM, toolRegistry))
                    addNode("tool", LangGraphNodeFuncitons.createToolNode(toolRegistry))
                    addNode("formatter", LangGraphNodeFuncitons.createFormatterNode())
                    addNode("start", LangGraphNodeFuncitons.createStartNode())
                    addNode("end", LangGraphNodeFuncitons.createEndNode("Custom agent completed"))

                    // Set entry point and completion checker
                    setEntryPoint("start")
                    setCompletionChecker { it.completed }

                    // Configure flow
                    addEdge("start", "llm")

                    // Conditional edges
                    addConditionalEdge(
                        "llm",
                        mapOf(
                            ConversationState.HasToolCalls to "tool",
                            { state: ConversationState -> state.completed } to "formatter"
                        ),
                        defaultTarget = "formatter"
                    )

                    addEdge("tool", "llm")
                    addEdge("formatter", "end")
                }

                // Run with a test query
                val query = "What is 42 + 17?"
                val initialState = ConversationState(query = query)
                val result = graph.run(initialState)

                // Verify results
                val finalResponse = result.finalResponse
                Assert.assertTrue("Response should start with ANSWER:", finalResponse.startsWith("ANSWER:"))

                // 检查计算器结果
                var hasCalculatorResult = false
                for (response in result.toolResponses) {
                    val output = response.output.toString()

                    if (output == "59") {
                        hasCalculatorResult = true
                        break
                    }
                }

                Assert.assertTrue("Should have calculator result", hasCalculatorResult)

            } catch (e: Exception) {
                Log.e(DEBUG_TAG, "Test failed", e)
                throw e
            }
        }
    }

    @Test
    fun test_003_PythonStyleGraph() {
        toolRegistry.register(CalculatorTool::class)
        toolRegistry.register(WeatherTool::class)

        runBlocking {
            try {
                // Load model and setup tools
                loadModel()
                toolRegistry.setLLMToolAdapter(Llama3_2_3B_LLMToolAdapter())

                // Create graph builder directly (most Python-like approach)
                val graph = LangGraph<ConversationState>().apply {
                    // Add nodes using predefined node creators
                    addNode("chatbot", LangGraphNodeFuncitons.createLLMNode(smolLM, toolRegistry))
                    addNode("tools", LangGraphNodeFuncitons.createToolNode(toolRegistry))
                    addNode("start", LangGraphNodeFuncitons.createStartNode())
                    addNode("end", LangGraphNodeFuncitons.createEndNode("Python-style agent completed"))

                    // Set entry point and completion checker
                    setEntryPoint("start")
                    setCompletionChecker { it.completed }

                    // Define edges
                    addEdge("start", "chatbot")

                    // Use conditional edges
                    addConditionalEdge(
                        "chatbot",
                        mapOf(
                            ConversationState.HasToolCalls to "tools"
                        ),
                        defaultTarget = "end"
                    )
                    addEdge("tools", "chatbot")

                    // Compile
                    compile()
                }

                // Run query
                val query = "What's the weather in Seattle? Also, what is 45 + 27?"
                Log.d(DEBUG_TAG, "Running pure Python-style graph with query: $query")

                val initialState = ConversationState(query = query)
                val result = graph.run(initialState)

                // Verify results
                Assert.assertTrue("Execution should complete", result.completed)
                Assert.assertNull("Should have no errors", result.error)

                // 检查工具使用
                var usedCalculator = false
                var usedWeather = false

                for (response in result.toolResponses) {
                    val output = response.output.toString()

                    if (output == "72") {
                        usedCalculator = true
                    }

                    if (output.contains("Seattle")) {
                        usedWeather = true
                    }
                }

                Assert.assertTrue("Should have used weather tool", usedWeather)
                Assert.assertTrue("Should have used calculator", usedCalculator)

            } catch (e: Exception) {
                Log.e(DEBUG_TAG, "Test failed", e)
                throw e
            }
        }
    }

    @Test
    fun test_004_SimplifiedAgent() {
        toolRegistry.register(CalculatorTool::class)

        runBlocking {
            try {
                // Load model and setup tools
                loadModel()
                toolRegistry.setLLMToolAdapter(Llama3_2_3B_LLMToolAdapter())

                // 最簡化的圖創建 - 只定義必要的節點，使用簡化的LLM節點
                val graph = StateGraphFactory.createSimpleAgent<ConversationState>(
                    model = smolLM,
                    toolRegistry = toolRegistry,
                    createLLMNode = LangGraphNodeFuncitons::createSimpleLLMNode
                    // 使用預設的開始和結束節點
                )

                // Run with a test query
                val query = "What is 42 + 17? Please calculate it."
                val initialState = ConversationState(query = query)
                val result = graph.run(initialState)

                // Verify results
                Assert.assertTrue("Execution should complete", result.completed)
                Assert.assertNull("Should have no errors", result.error)
                Assert.assertFalse("Final response should not be empty", result.finalResponse.isEmpty())

                // Simple agent doesn't execute tools, but should mention the calculation
                val containsResult = result.finalResponse.contains("59") ||
                        result.finalResponse.contains("42 + 17") ||
                        result.finalResponse.contains("forty-two plus seventeen")

                Assert.assertTrue("Response should mention the calculation or result", containsResult)

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