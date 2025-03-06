package com.smith.lai.smithtoolcalls

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smith.lai.smithtoolcalls.custom_data.ConversationState
import com.smith.lai.smithtoolcalls.custom_data.ConversationNodes
import com.smith.lai.smithtoolcalls.custom_data.MessageRole
import com.smith.lai.smithtoolcalls.langgraph.*
import com.smith.lai.smithtoolcalls.langgraph.node.Node
import com.smith.lai.smithtoolcalls.langgraph.nodes.GenericNodes
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
                    //memory node
                    //createStartNode = { LangGraphNodes.createStartNode() },
                    //createEndNode = { msg -> LangGraphNodes.createEndNode(msg) }
                )

                // 創建初始狀態 - 更接近Python風格
                val initialState = ConversationState().apply {
                    query = "What's 125 + 437 and what's the weather in San Francisco?"
                    // 直接使用狀態, 不再傳遞字符串查詢
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

    @Test
    fun test_002_CustomNodeGraph() {
        toolRegistry.register(CalculatorTool::class)

        runBlocking {
            try {
                // 加載模型和設置工具
                loadModel()
                toolRegistry.setLLMToolAdapter(Llama3_2_3B_LLMToolAdapter())

                // 使用自定義配置創建圖
                val graph = StateGraphFactory.createCustomGraph<ConversationState> {
                    // 添加節點
                    addNode("llm", ConversationNodes.createLLMNode(smolLM, toolRegistry))
                    addNode("tool", ConversationNodes.createToolNode(toolRegistry))
                    addNode("formatter", ConversationNodes.createFormatterNode())
                    addNode("start", ConversationNodes.createStartNode())
                    addNode("end", ConversationNodes.createEndNode("Custom agent completed"))

                    // 設置入口點和完成檢查器
                    setEntryPoint("start")
                    setCompletionChecker { it.completed }

                    // 配置流程
                    addEdge("start", "llm")

                    // 條件邊
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

                // 創建初始狀態 - 更接近Python風格
                val initialState = ConversationState().apply {
                    query = "What is 42 + 17?"
                }

                // 執行圖 - 直接傳入狀態
                val result = graph.run(initialState)

                // 驗證結果
                val finalResponse = result.finalResponse
                Assert.assertTrue("Response should start with ANSWER:", finalResponse.startsWith("ANSWER:"))

                // 檢查計算器結果
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
                // 加載模型和設置工具
                loadModel()
                toolRegistry.setLLMToolAdapter(Llama3_2_3B_LLMToolAdapter())

                // 使用更接近Python風格的節點定義
                val chatbotNode = object : Node<ConversationState> {
                    override suspend fun invoke(state: ConversationState): ConversationState {
                        try {
                            // 檢查已有錯誤
                            if (state.error != null) {
                                Log.e(DEBUG_TAG, "Chatbot node: previous error detected: ${state.error}")
                                return state.withCompleted(true)
                            }

                            // 獲取查詢並添加用戶消息
                            val query = state.query
                            if (query.isNotEmpty() &&
                                (state.messages.isEmpty() ||
                                        state.messages.last().role != MessageRole.USER ||
                                        state.messages.last().content != query)) {
                                state.addMessage(MessageRole.USER, query)
                            }

                            // 處理模型響應
                            val systemPrompt = toolRegistry.createSystemPrompt()
                            smolLM.addSystemPrompt(systemPrompt)

                            // 添加消息歷史
                            for (message in state.messages) {
                                when (message.role) {
                                    MessageRole.USER -> smolLM.addUserMessage(message.content)
                                    MessageRole.ASSISTANT -> smolLM.addAssistantMessage(message.content)
                                    MessageRole.TOOL -> smolLM.addUserMessage(message.content)
                                    else -> {} // 忽略其他類型
                                }
                            }

                            // 生成響應
                            val responseBuilder = StringBuilder()
                            smolLM.getResponse().collect { responseBuilder.append(it) }
                            val response = responseBuilder.toString()

                            // 添加助手消息
                            state.addMessage(MessageRole.ASSISTANT, response)

                            // 處理工具調用
                            val processingResult = toolRegistry.processLLMResponse(response)
                            val hasToolCalls = processingResult.toolResponses.isNotEmpty()

                            // 更新狀態
                            state.processingResult = processingResult
                            state.hasToolCalls = hasToolCalls

                            return state
                        } catch (e: Exception) {
                            Log.e(DEBUG_TAG, "Chatbot node error", e)
                            return state.withError("Chatbot error: ${e.message}").withCompleted(true)
                        }
                    }
                }

                val toolsNode = object : Node<ConversationState> {
                    override suspend fun invoke(state: ConversationState): ConversationState {
                        Log.d(DEBUG_TAG, "Tools node: processing state")
                        val processingResult = state.processingResult
                        if (processingResult == null || !state.hasToolCalls) {
                            return state.withError("No tool calls to process").withCompleted(true)
                        }

                        // 添加工具響應
                        val toolResponses = processingResult.toolResponses
                        state.toolResponses.addAll(toolResponses)

                        // 添加工具消息
                        for (response in toolResponses) {
                            val toolContent = "Tool '${response.id}' output: ${response.output}"
                            state.addMessage(MessageRole.TOOL, toolContent)
                        }

                        // 重置工具調用狀態
                        state.hasToolCalls = false

                        return state
                    }
                }

                // 構建圖 - 最接近Python風格的方法
                val graph = LangGraph<ConversationState>().apply {
                    // 添加節點
                    addNode("chatbot", chatbotNode)
                    addNode("tools", toolsNode)
                    addNode("start", GenericNodes.createStartNode<ConversationState>())
                    addNode("end", GenericNodes.createEndNode<ConversationState>("Python-style graph completed"))

                    // 設置入口點和完成檢查器
                    setEntryPoint("start")
                    setCompletionChecker { !it.hasToolCalls }

                    // 定義邊
                    addEdge("start", "chatbot")

                    // 定義條件邊
                    addConditionalEdge(
                        "chatbot",
                        mapOf(
                            { state: ConversationState -> state.hasToolCalls } to "tools"
                        ),
                        defaultTarget = "end"
                    )
                    addEdge("tools", "chatbot")

                    // 編譯
                    compile()
                }

                // 創建初始狀態 - 更接近Python風格
                val initialState = ConversationState().apply {
                    // 可以使用預先定義的消息歷史
                    // addMessage(MessageRole.USER, "Hello!")
                    // addMessage(MessageRole.ASSISTANT, "Hi there! How can I help you?")

                    // 設置當前的查詢
                    query = "What's the weather in Seattle? Also, what is 45 + 27?"
                }

                // 執行圖 - 直接傳入狀態
                val result = graph.run(initialState)

                // 驗證結果
                Assert.assertTrue("Execution should complete", result.completed)
                Assert.assertNull("Should have no errors", result.error)

                // 檢查工具使用
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

                // 檢查所有消息
                Log.d(DEBUG_TAG, "Messages history:")
                for (msg in result.messages) {
                    Log.d(DEBUG_TAG, "${msg.role}: ${msg.content.take(50)}...")
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
    fun test_004_StateWithHistory() {
        toolRegistry.register(CalculatorTool::class)

        runBlocking {
            try {
                // 加載模型和設置工具
                loadModel()
                toolRegistry.setLLMToolAdapter(Llama3_2_3B_LLMToolAdapter())

                // 創建簡單圖
                val graph = StateGraphFactory.createSimpleAgent<ConversationState>(
                    model = smolLM,
                    toolRegistry = toolRegistry,
                    createLLMNode = ConversationNodes::createSimpleLLMNode
                )

                // 創建帶有消息歷史的初始狀態 - 完全符合Python風格
                val initialState = ConversationState().apply {
                    // 添加預先存在的對話歷史
                    addMessage(MessageRole.USER, "Hello, my name is David.")
                    addMessage(MessageRole.ASSISTANT, "Hi David! How can I help you today?")

                    // 設置當前查詢
                    query = "What is 15 + 27?"
                }

                // 執行圖
                val result = graph.run(initialState)

                // 驗證結果
                Assert.assertTrue("Execution should complete", result.completed)
                Assert.assertNull("Should have no errors", result.error)

                // 檢查消息歷史
                Assert.assertEquals("Should have 4 messages", 4, result.messages.size)
                Assert.assertEquals("First message should be from user", MessageRole.USER, result.messages[0].role)
                Assert.assertTrue("First message should contain name", result.messages[0].content.contains("David"))

                // 檢查回應是否包含計算結果
                val finalResponse = result.finalResponse
                val containsResult = finalResponse.contains("42") ||
                        finalResponse.contains("15 + 27") ||
                        finalResponse.contains("addition")

                Assert.assertTrue("Response should mention the calculation result", containsResult)

                // 記錄最終消息
                for (msg in result.messages) {
                    Log.d(DEBUG_TAG, "${msg.role}: ${msg.content.take(50)}...")
                }

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