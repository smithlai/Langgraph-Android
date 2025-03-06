package com.smith.lai.smithtoolcalls

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smith.lai.smithtoolcalls.langgraph.*
import com.smith.lai.smithtoolcalls.langgraph.node.EndNode
import com.smith.lai.smithtoolcalls.langgraph.node.Node
import com.smith.lai.smithtoolcalls.langgraph.node.StateGraph
import com.smith.lai.smithtoolcalls.langgraph.node.LLMNode
import com.smith.lai.smithtoolcalls.langgraph.node.MemoryNode
import com.smith.lai.smithtoolcalls.langgraph.node.NodeTypes
import com.smith.lai.smithtoolcalls.langgraph.node.StartNode
import com.smith.lai.smithtoolcalls.langgraph.node.ToolNode
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

                // Create graph using factory
                val graph = StateGraphFactory.createConversationalAgent(smolLM, toolRegistry)

                // Run with a test query
                val query = "What's 125 + 437 and what's the weather in San Francisco?"
                Log.d(DEBUG_TAG, "Running graph with query: $query")

                val result = graph.run(query)

                // Log and verify results
                Log.d(DEBUG_TAG, "Execution completed in ${result.executionDuration()}ms")
                Log.d(DEBUG_TAG, "Steps: ${result.stepCount}")
                Log.d(DEBUG_TAG, "Final response length: ${result.finalResponse.length}")

                Assert.assertTrue("Execution should complete", result.completed)
                Assert.assertNull("Should have no errors", result.error)

                // Check tool usage
                val usedCalculator = result.toolResponses.any { it.output.toString() == "562" }
                val usedWeather = result.toolResponses.any {
                    it.output.toString().contains("San Francisco")
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

    //    @Test
    fun test_002_CustomNodeGraph() {
        toolRegistry.register(CalculatorTool::class)

        runBlocking {
            try {
                // Load model and setup tools
                loadModel()
                toolRegistry.setLLMToolAdapter(Llama3_2_3B_LLMToolAdapter())

                // Create custom formatter node
                class FormatterNode : Node<StateGraph> {
                    override suspend fun invoke(state: StateGraph): StateGraph {
                        Log.d(DEBUG_TAG, "Formatting final response")

                        val response = state.finalResponse.ifEmpty {
                            state.messages.lastOrNull { it.role == MessageRole.ASSISTANT }?.content ?: ""
                        }

                        val formatted = "ANSWER: $response"
                        return state.copy(finalResponse = formatted)
                    }
                }

                // Create graph with custom configuration using string-based API
                val graph = StateGraphFactory.createCustomGraph {
                    // Add nodes
                    addNode("llm", LLMNode(smolLM, toolRegistry))
                    addNode("tool", ToolNode(toolRegistry))
                    addNode("formatter", FormatterNode())

                    // Configure flow
                    addEdge(NodeTypes.START, "llm")

                    // Conditional edges
                    addConditionalEdge(
                        "llm",
                        mapOf(
                            StateConditions.hasToolCalls to "tool",
                            StateConditions.isComplete to "formatter"
                        ),
                        defaultTarget = "formatter"
                    )

                    addEdge("tool", "llm")
                    addEdge("formatter", NodeTypes.END)
                }

                // Run with a test query
                val query = "What is 42 + 17?"
                val result = graph.run(query)

                // Verify results
                Assert.assertTrue("Response should start with ANSWER:", result.finalResponse.startsWith("ANSWER:"))
                Assert.assertTrue("Should have calculator result", result.toolResponses.any { it.output.toString() == "59" })

            } catch (e: Exception) {
                Log.e(DEBUG_TAG, "Test failed", e)
                throw e
            }
        }
    }

    //    @Test
    fun test_003_PythonStyleGraph() {
        toolRegistry.register(CalculatorTool::class)
        toolRegistry.register(WeatherTool::class)

        runBlocking {
            try {
                // Load model and setup tools
                loadModel()
                toolRegistry.setLLMToolAdapter(Llama3_2_3B_LLMToolAdapter())

                // Create graph builder directly (most Python-like approach)
                val graphBuilder = StateGraphBuilder()

                // Add nodes
                graphBuilder.addNode("chatbot", LLMNode(smolLM, toolRegistry))
                graphBuilder.addNode("tools", ToolNode(toolRegistry))

                // Define edges
                graphBuilder.addEdge(NodeTypes.START, "chatbot")

                // Use conditional edges
                graphBuilder.addConditionalEdge(
                    "chatbot",
                    mapOf(
                        StateConditions.hasToolCalls to "tools"
                    ),
                    defaultTarget = NodeTypes.END
                )
                graphBuilder.addEdge("tools", "chatbot")

                // Compile graph
                val graph = graphBuilder.compile()

                // Run query
                val query = "What's the weather in Seattle? Also, what is 45 + 27?"
                Log.d(DEBUG_TAG, "Running pure Python-style graph with query: $query")

                val result = graph.run(query)

                // Verify results
                Assert.assertTrue("Execution should complete", result.completed)
                Assert.assertNull("Should have no errors", result.error)

                // Check tool usage
                val usedCalculator = result.toolResponses.any { it.output.toString() == "72" }
                val usedWeather = result.toolResponses.any {
                    it.output.toString().contains("Seattle")
                }

                Assert.assertTrue("Should have used weather tool", usedWeather)
                Assert.assertTrue("Should have used calculator", usedCalculator)

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