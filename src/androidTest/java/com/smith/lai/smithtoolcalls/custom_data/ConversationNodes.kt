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
     * 創建LLM節點，負責生成回應並處理工具調用
     * 支持帶有歷史的初始狀態
     */
    fun createLLMNode(model: SmolLM, toolRegistry: ToolRegistry): Node<ConversationState> {
        return object : Node<ConversationState> {

            // 最大跟踪消息數
            private val maxTrackedMessages = 5

            // 使用 LinkedHashSet 實現有序且唯一的 ID 集合
            // LinkedHashSet 維護插入順序，允許按照添加順序進行迭代
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
                                Log.v(DEBUG_TAG, "Added USER: ${message.content.take(50)}...")
                            }
                            MessageRole.ASSISTANT -> {
                                model.addAssistantMessage(message.content)
                                Log.v(DEBUG_TAG, "Added ASSISTANT: ${message.content.take(50)}...")
                            }
                            MessageRole.TOOL -> {
                                model.addUserMessage(message.content)
                                Log.v(DEBUG_TAG, "Added TOOL as USER: ${message.content.take(50)}...")
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

                        // 添加助手消息
                        state.addMessage(MessageRole.ASSISTANT, assistantResponse)

                        // 處理工具調用
                        val processingResult = toolRegistry.processLLMResponse(assistantResponse)
                        state.processingResult = processingResult
                        state.hasToolCalls = processingResult.toolResponses.isNotEmpty()
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
     * 創建工具節點，負責執行工具調用
     */
    fun createToolNode(toolRegistry: ToolRegistry): Node<ConversationState> {
        return object : Node<ConversationState> {
            override suspend fun invoke(state: ConversationState): ConversationState {
                Log.d(DEBUG_TAG, "Tool node: processing with hasToolCalls=${state.hasToolCalls}")

                val processingResult = state.processingResult
                if (processingResult == null || !state.hasToolCalls) {
                    Log.e(DEBUG_TAG, "Tool node: no processing result available or no tool calls")
                    return state.withError("No tool calls to process").withCompleted(true)
                }

                // 處理工具響應
                val toolResponses = processingResult.toolResponses
                if (toolResponses.isEmpty()) {
                    Log.d(DEBUG_TAG, "Tool node: no tool responses to process")
                    return state.withCompleted(true)
                }

                Log.d(DEBUG_TAG, "Tool node: processing ${toolResponses.size} tool responses\n${toolResponses.map { it.output.toString() }.joinToString { it }}")

                // 添加工具響應
                state.toolResponses.addAll(toolResponses)

                // 創建後續提示
                val followUpPrompt = "工具執行完畢，請繼續對話"

                // 添加工具消息
                state.addMessage(MessageRole.TOOL, followUpPrompt)

                // 重置工具調用標誌
                state.hasToolCalls = false

                return state
            }
        }
    }

    /**
     * 創建簡單的格式化節點，負責對最終回應進行格式化
     */
    fun createFormatterNode(prefix: String = "ANSWER: "): Node<ConversationState> {
        return object : Node<ConversationState> {
            override suspend fun invoke(state: ConversationState): ConversationState {
                Log.d(DEBUG_TAG, "Formatting final response")

                val response = state.finalResponse.ifEmpty {
                    state.getLastAssistantMessage() ?: ""
                }

                // 格式化响应
                state.finalResponse = "$prefix$response"

                return state
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
                    state.finalResponse = assistantResponse

                    return state.withCompleted(true)
                } catch (e: Exception) {
                    return state.withError("LLM error: ${e.message}").withCompleted(true)
                }
            }
        }
    }

    /**
     * 創建消息處理節點，用於驗證消息狀態
     */
    fun createMessageProcessorNode(): Node<ConversationState> {
        return object : Node<ConversationState> {
            override suspend fun invoke(state: ConversationState): ConversationState {
                if (state.messages.isEmpty()) {
                    Log.e(DEBUG_TAG, "Message processor: no messages available")
                    return state.withError("No messages available").withCompleted(true)
                }

                // 確保最後一條消息是來自用戶的
                val lastMessage = state.messages.last()
                if (lastMessage.role != MessageRole.USER) {
                    Log.w(DEBUG_TAG, "Message processor: last message is not from user")
                    // 可根據需求決定是否為錯誤
                }

                return state
            }
        }
    }

    /**
     * 使用GenericNodes中的泛型實現創建開始節點
     */
    fun createStartNode(): Node<ConversationState> {
        return GenericNodes.createStartNode<ConversationState>(
            logTag = DEBUG_TAG
        )
    }

    /**
     * 使用GenericNodes中的泛型實現創建結束節點
     */
    fun createEndNode(completionMessage: String): Node<ConversationState> {
        return GenericNodes.createEndNode<ConversationState>(
            completionMessage = completionMessage,
            getDuration = { it.executionDuration() },
            getFinalResponse = { it.finalResponse },
            getLastMessage = { it.getLastAssistantMessage() },
            setFinalResponse = { state, response -> state.finalResponse = response },
            logTag = DEBUG_TAG
        )
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
        createToolNode: (ToolRegistry) -> Node<S>,
        createMemoryNode: (() -> Node<S>)? = null
    ): LangGraph<S> {
        // Create graph builder
        val graphBuilder = LangGraph<S>()

        // Add nodes - 使用預設值創建標準節點
        val startNode = GenericNodes.createStartNode<S>()
        val endNode = GenericNodes.createEndNode<S>("Conversation agent completed")
        val memoryNode = createMemoryNode?.invoke() ?: createPassThroughNode()

        graphBuilder.addNode(NodeNames.START, startNode)
        graphBuilder.addNode(NodeNames.END, endNode)
        graphBuilder.addNode("memory", memoryNode)
        graphBuilder.addNode("llm", createLLMNode(model, toolRegistry))
        graphBuilder.addNode("tool", createToolNode(toolRegistry))

        // Set entry point
//        graphBuilder.setEntryPoint(NodeNames.START)

        // Set completion checker
//        graphBuilder.setCompletionChecker { state -> state.completed }

        // Add edges
        graphBuilder.addEdge(NodeNames.START, "memory")
        graphBuilder.addEdge("memory", "llm")

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