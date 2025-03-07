package com.smith.lai.smithtoolcalls.custom_data

import android.util.Log
import com.smith.lai.smithtoolcalls.ToolRegistry
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState
import com.smith.lai.smithtoolcalls.langgraph.LangGraph
import com.smith.lai.smithtoolcalls.langgraph.state.StateConditions
import com.smith.lai.smithtoolcalls.langgraph.node.Node
import com.smith.lai.smithtoolcalls.langgraph.node.NodeNames
import com.smith.lai.smithtoolcalls.langgraph.nodes.GenericNodes
import com.smith.lai.smithtoolcalls.langgraph.nodes.GenericNodes.createPassThroughNode
import io.shubham0204.smollm.SmolLM

/**
 * 預定義的節點創建函數集合，用於創建特定於ConversationState的節點
 */
object ConversationNodes {
    const val DEBUG_TAG = "LangGraphNodes"

    /**
     * 創建 LLM 節點，負責生成回應
     * 使用 ToolRegistry 的現有方法檢測工具調用
     */
    fun createLLMNode(model: SmolLM, toolRegistry: ToolRegistry): Node<ConversationState> {
        return object : Node<ConversationState> {
            // 最大跟踪消息數
            private val maxTrackedMessages = 5

            // 使用 LinkedHashSet 實現有序且唯一的 ID 集合
            private val processedMessageIds = LinkedHashSet<String>(maxTrackedMessages)

            override suspend fun invoke(state: ConversationState): ConversationState {
                try {
                    // 檢查已有錯誤
                    if (state.error != null) {
                        Log.e(DEBUG_TAG, "LLM node: previous error detected: ${state.error}")
                        return state.withCompleted(true)
                    }

                    // 檢查消息是否為空
                    if (state.messages.isEmpty()) {
                        Log.e(DEBUG_TAG, "LLM node: no messages to process")
                        return state.withError("No messages to process").withCompleted(true)
                    }

                    // 防止上下文溢出-僅處理最近消息
                    val messagesToProcess = state.messages.takeLast(maxTrackedMessages)

                    // 檢查是否需要添加系統提示
                    if (processedMessageIds.isEmpty()) {
                        val systemPrompt = toolRegistry.createSystemPrompt()
                        Log.d(DEBUG_TAG, "LLM node: Adding SystemPrompt(${systemPrompt.length})")
                        model.addSystemPrompt(systemPrompt)
                    }

                    // 添加新的消息
                    var newMessagesAdded = 0
                    for (message in messagesToProcess) {
                        // 檢查消息是否已處理過
                        if (processedMessageIds.contains(message.id)) {
                            continue
                        }

                        // 添加新消息到模型
                        when (message.role) {
                            MessageRole.USER -> {
                                model.addUserMessage(message.content)
                                Log.v(DEBUG_TAG, "Added USER: ${message.content}...")
                            }
                            MessageRole.ASSISTANT -> {
                                model.addAssistantMessage(message.content)
                                Log.v(DEBUG_TAG, "Added ASSISTANT: ${message.content}...")
                            }
                            MessageRole.TOOL -> {
                                model.addUserMessage(message.content)
                                Log.v(DEBUG_TAG, "Added TOOL as USER: ${message.content}...")
                            }
                            else -> {} // 忽略系統消息
                        }

                        // 添加消息 ID 到處理集合
                        processedMessageIds.add(message.id)

                        // 如果超過最大跟踪數，移除最舊的
                        if (processedMessageIds.size > maxTrackedMessages) {
                            processedMessageIds.iterator().next()?.let {
                                processedMessageIds.remove(it)
                            }
                        }

                        newMessagesAdded++
                    }

                    Log.d(DEBUG_TAG, "LLM node: Added $newMessagesAdded new messages to context")

                    // 只有當有新消息時才生成回應
                    if (newMessagesAdded > 0) {
                        // 生成回應
                        Log.d(DEBUG_TAG, "LLM node: generating response...")
                        val response = StringBuilder()
                        model.getResponse().collect {
                            response.append(it)
                        }

                        val assistantResponse = response.toString()
                        Log.d(DEBUG_TAG, "LLM node: response generated: (${assistantResponse.length} chars)\n$assistantResponse")

                        // 添加助手消息, 並標記為已處理
                        state.addMessage(MessageRole.ASSISTANT, assistantResponse)
                        processedMessageIds.add(state.messages.last().id)

                        // 儲存原始回應供 Tool 節點使用
                        state.rawLLMResponse = assistantResponse

                        // 使用 ToolRegistry 的方法檢測工具調用
                        val structuredResponse = toolRegistry.convertToStructured(assistantResponse)
                        state.hasToolCalls = structuredResponse.hasToolCalls()

                        Log.d(DEBUG_TAG, "LLM node: detected tool calls = ${state.hasToolCalls}")

                    } else {
                        Log.d(DEBUG_TAG, "LLM node: no new messages, skipping response generation")
                        state.hasToolCalls = false
                    }

                    return state.withCompleted(!state.hasToolCalls)
                } catch (e: Exception) {
                    Log.e(DEBUG_TAG, "LLM node: error processing state", e)
                    return state.withError("LLM node error: ${e.message}").withCompleted(true)
                }
            }
        }
    }

    /**
     * 創建工具節點，負責解析工具調用意圖並執行工具
     */
    fun createToolNode(toolRegistry: ToolRegistry): Node<ConversationState> {
        return object : Node<ConversationState> {
            override suspend fun invoke(state: ConversationState): ConversationState {
                Log.d(DEBUG_TAG, "Tool node: processing with hasToolCalls=${state.hasToolCalls}")

                // 檢查必要條件
                if (!state.hasToolCalls || state.rawLLMResponse.isNullOrEmpty()) {
                    Log.e(DEBUG_TAG, "Tool node: no tool calls or raw response available")
                    return state.withError("No tool calls to process").withCompleted(true)
                }

                try {
                    // 使用 ToolRegistry 處理 LLM 回應，包含解析和執行工具
                    val processingResult = toolRegistry.processLLMResponse(state.rawLLMResponse!!)

                    // 檢查解析結果
                    if (processingResult.toolResponses.isEmpty()) {
                        Log.w(DEBUG_TAG, "Tool node: no valid tool responses found in result")
                        state.hasToolCalls = false
                        return state.withCompleted(true)
                    }

                    Log.d(DEBUG_TAG, "Tool node: processing ${processingResult.toolResponses.size} tool responses")
                    val outputs = processingResult.toolResponses.map { "${it.id}: ${it.output}" }
                    Log.d(DEBUG_TAG, "Tool outputs: ${outputs.joinToString("\n")}")

                    // 添加工具響應到狀態
                    state.toolResponses.addAll(processingResult.toolResponses)

                    // 創建後續提示
                    // 使用 ProcessingResult 的方法生成更智能的提示
                    val followUpPrompt = processingResult.buildFollowUpPrompt()

                    // 添加工具消息作為用戶消息
                    state.addMessage(MessageRole.TOOL, followUpPrompt)

                    // 重置工具調用標誌
                    state.hasToolCalls = false

                    // 檢查是否應終止流程
                    if (processingResult.shouldTerminateFlow()) {
                        Log.d(DEBUG_TAG, "Tool node: tool requested to terminate flow")
                        return state.withCompleted(true)
                    }

                    return state
                } catch (e: Exception) {
                    Log.e(DEBUG_TAG, "Tool node: error processing tool calls", e)
                    return state.withError("Error processing tool calls: ${e.message}").withCompleted(true)
                }
            }
        }
    }


    /**
     * 創建簡化版LLM節點，用於不需要工具調用的簡單對話
     * 支持帶有歷史的初始狀態
     */
    fun createSimpleLLMNode(model: SmolLM, toolRegistry: ToolRegistry): Node<ConversationState> {
        return object : Node<ConversationState> {
            override suspend fun invoke(state: ConversationState): ConversationState {
                try {
                    // 檢查消息是否為空
                    if (state.messages.isEmpty()) {
                        Log.e(DEBUG_TAG, "Simple LLM node: no messages to process")
                        return state.withError("No messages to process").withCompleted(true)
                    }

                    // 添加系統提示
                    model.addSystemPrompt(toolRegistry.createSystemPrompt())

                    // 添加所有消息歷史
                    for (message in state.messages) {
                        when (message.role) {
                            MessageRole.USER -> model.addUserMessage(message.content)
                            MessageRole.ASSISTANT -> model.addAssistantMessage(message.content)
                            MessageRole.TOOL -> model.addUserMessage(message.content)
                            else -> {} // 忽略系統消息
                        }
                    }

                    // 生成回應
                    val response = StringBuilder()
                    model.getResponse().collect {
                        response.append(it)
                    }
                    val assistantResponse = response.toString()

                    // 添加助手消息並設置為最終回應
                    state.addMessage(MessageRole.ASSISTANT, assistantResponse)

                    return state.withCompleted(true)
                } catch (e: Exception) {
                    return state.withError("LLM error: ${e.message}").withCompleted(true)
                }
            }
        }
    }

    /**
     * Creates a standard conversation agent with LLM, Tool, and memory handling
     *
     * @param model LLM model to use
     * @param toolRegistry Tool registry for tool execution
     * @param createLLMNode function to create an LLM node for state type S
     * @param createToolNode function to create a tool node for state type S
     * @param createMemoryNode function to create a memory node for state type S (optional)
     */
    fun <S : GraphState> createConversationalAgent(
        model: SmolLM,
        toolRegistry: ToolRegistry,
        createLLMNode: (SmolLM, ToolRegistry) -> Node<S>,
        createToolNode: (ToolRegistry) -> Node<S>
//        createMemoryNode: (() -> Node<S>)? = null
    ): LangGraph<S> {
        // Create graph builder
        val graphBuilder = LangGraph<S>()

        // Add nodes - 使用預設值創建標準節點
        val startNode = GenericNodes.createStartNode<S>()
        val endNode = GenericNodes.createEndNode<S>("Conversation agent completed")
//        val memoryNode = createMemoryNode?.invoke() ?: createPassThroughNode()

        graphBuilder.addNode(NodeNames.START, startNode)
        graphBuilder.addNode(NodeNames.END, endNode)
//        graphBuilder.addNode("memory", memoryNode)
        graphBuilder.addNode("llm", createLLMNode(model, toolRegistry))
        graphBuilder.addNode("tool", createToolNode(toolRegistry))

        // Set entry point
//        graphBuilder.setEntryPoint(NodeNames.START)

        // Set completion checker
//        graphBuilder.setCompletionChecker { state -> state.completed }

        // Add edges
//        graphBuilder.addEdge(NodeNames.START, "memory")
//        graphBuilder.addEdge("memory", "llm")
        graphBuilder.addEdge(NodeNames.START, "llm")
        // Conditional edges
        graphBuilder.addConditionalEdges(
            "llm",
            mapOf(
                StateConditions.hasToolCalls<S>() to "tool",
                StateConditions.isComplete<S>() to NodeNames.END
            ),
            defaultTarget = NodeNames.END
        )

        graphBuilder.addEdge("tool", "llm")

        // Compile and return the graph
        return graphBuilder.compile()
    }
}