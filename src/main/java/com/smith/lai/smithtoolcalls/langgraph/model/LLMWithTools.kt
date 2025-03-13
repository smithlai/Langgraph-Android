package com.smith.lai.smithtoolcalls.langgraph.model

import android.util.Log
import com.smith.lai.smithtoolcalls.tools.BaseTool
import com.smith.lai.smithtoolcalls.tools.FinishReason
import com.smith.lai.smithtoolcalls.tools.ProcessingResult
import com.smith.lai.smithtoolcalls.tools.ResponseMetadata
import com.smith.lai.smithtoolcalls.tools.StructuredLLMResponse
import com.smith.lai.smithtoolcalls.tools.ToolAnnotation
import com.smith.lai.smithtoolcalls.tools.ToolCallInfo
import com.smith.lai.smithtoolcalls.tools.ToolFollowUpMetadata
import com.smith.lai.smithtoolcalls.tools.ToolResponse
import com.smith.lai.smithtoolcalls.tools.ToolResponseType
import com.smith.lai.smithtoolcalls.langgraph.model.adapter.BaseLLMToolAdapter
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState
import com.smith.lai.smithtoolcalls.langgraph.state.Message
import com.smith.lai.smithtoolcalls.langgraph.state.MessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation

/**
 * 帶有工具的LLM實現，整合了處理狀態和工具調用的高層次功能
 */
abstract class LLMWithTools(
    private val adapter: BaseLLMToolAdapter,
    private val TAG: String = "LLMWithTools"
) {
    private val tools = mutableMapOf<String, BaseTool<*, *>>()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // 追踪工具提示是否已添加
    private var isToolPromptAdded = false
    // 追踪初始訊息是否已添加
    private var messagesInitialized = false

    fun bind_tools(tool: BaseTool<*, *>) {
        val annotation = tool::class.findAnnotation<ToolAnnotation>() ?:
        throw IllegalArgumentException("Tool must have @Tool annotation")
        tools[annotation.name] = tool
        // 當修改工具時重置提示狀態
        isToolPromptAdded = false
    }

    fun bind_tools(toolClass: KClass<out BaseTool<*, *>>) {
        val instance = toolClass.createInstance()
        bind_tools(instance)
    }

    fun bind_tools(tools: List<BaseTool<*, *>>) {
        tools.forEach { bind_tools(it) }
    }

    fun getTool(name: String): BaseTool<*, *>? = tools[name]

    fun getToolNames(): List<String> = tools.keys.toList()

    fun getTools(): List<BaseTool<*, *>> = tools.values.toList()

    /**
     * 創建工具提示
     */
    fun createToolPrompt(): String {
        return adapter?.createToolPrompt(getTools()) ?: ""
    }

    /**
     * 添加工具提示到模型並標記為已添加
     */
    fun addToolPrompt() {
        if (!isToolPromptAdded) {
            val systemPrompt = createToolPrompt()
            Log.d(TAG, "Adding ToolPrompt(${systemPrompt.length})")
            addSystemMessage(systemPrompt)
            isToolPromptAdded = true
        }
    }

    /**
     * 生成調用ID
     */
    fun generateCallId(): String {
        val timestamp = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString().replace("-", "")
        return "call_${timestamp}_${uuid}"
    }

    /**
     * 轉換LLM的原始回應為結構化格式
     */
    fun convertToStructured(llmResponse: String): StructuredLLMResponse {
        try {
            return adapter.parseLLMResponse(llmResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting LLM response: ${e.message}")
            return StructuredLLMResponse(
                content = llmResponse,
                metadata = ResponseMetadata(
                    finishReason = FinishReason.ERROR.value
                )
            )
        }
    }

    /**
     * 處理LLM回應的整個流程：從原始回應到工具執行結果
     */
    suspend fun processLLMResponse(llmResponse: String): ProcessingResult {
        val structuredResponse = convertToStructured(llmResponse)
        return processLLMResponse(structuredResponse)
    }

    /**
     * 處理結構化LLM回應
     */
    suspend fun processLLMResponse(structuredResponse: StructuredLLMResponse): ProcessingResult {
        try {
            Log.d(TAG, "Processing structured response with ${structuredResponse.toolCalls.size} tool calls")

            // 如果不包含工具調用，創建一個直接回應
            if (!structuredResponse.hasToolCalls()) {
                return ProcessingResult(
                    structuredResponse = structuredResponse,
                    toolResponses = listOf(
                        ToolResponse(
                            id = generateCallId(),
                            type = ToolResponseType.DIRECT_RESPONSE,
                            output = structuredResponse.content,
                            followUpMetadata = ToolFollowUpMetadata(requiresFollowUp = false)
                        )
                    )
                )
            }

            // 執行工具
            val toolResponses = executeTools(structuredResponse.toolCalls)
            return ProcessingResult(
                structuredResponse = structuredResponse,
                toolResponses = toolResponses
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error processing LLM response: ${e.message}")
            // 處理異常，返回錯誤回應
            val errorResponse = StructuredLLMResponse(
                content = "Error processing response: ${e.message}",
                metadata = ResponseMetadata(finishReason = FinishReason.ERROR.value)
            )
            return ProcessingResult(
                structuredResponse = errorResponse,
                toolResponses = listOf(
                    ToolResponse(
                        id = generateCallId(),
                        type = ToolResponseType.ERROR,
                        output = "Error processing response: ${e.message}",
                        followUpMetadata = ToolFollowUpMetadata(requiresFollowUp = false)
                    )
                )
            )
        }
    }

    /**
     * 執行工具調用
     */
    @OptIn(InternalSerializationApi::class)
    private suspend fun executeTools(toolCalls: List<ToolCallInfo>): List<ToolResponse<*>> {
        val toolResponses = mutableListOf<ToolResponse<*>>()

        for (toolCall in toolCalls) {
            if (toolCall.executed)
                continue
            toolCall.executed = true
            val tool = getTool(toolCall.function.name)
            if (tool == null) {
                // 處理找不到工具的情況
                val errorResponse = ToolResponse(
                    id = toolCall.id,
                    type = ToolResponseType.ERROR,
                    output = "Tool ${toolCall.function.name} not found",
                    followUpMetadata = ToolFollowUpMetadata(requiresFollowUp = true)
                )
                toolResponses.add(errorResponse)
                continue
            }

            try {
                val parameterType = tool.getParameterType()
                    ?: throw IllegalStateException("Could not determine parameter type for tool")

                val arguments = json.decodeFromString(
                    parameterType.serializer(),
                    toolCall.function.arguments
                )

                // 執行工具
                @Suppress("UNCHECKED_CAST")
                val result = (tool as BaseTool<Any, Any>).invoke(arguments)

                // 獲取後續處理元數據
                @Suppress("UNCHECKED_CAST")
                val metadata = tool.getFollowUpMetadata(result)

                // 創建包含後續處理元數據的工具回應
                val response = ToolResponse(
                    id = toolCall.id,
                    output = result,
                    followUpMetadata = metadata
                )
                toolResponses.add(response)

            } catch (e: Exception) {
                // 處理執行錯誤
                val errorResponse = ToolResponse(
                    id = toolCall.id,
                    type = ToolResponseType.ERROR,
                    output = "Error executing tool: ${e.message}",
                    followUpMetadata = ToolFollowUpMetadata(requiresFollowUp = true)
                )
                toolResponses.add(errorResponse)
            }
        }

        return toolResponses
    }

    /**
     * 處理消息列表，生成回應
     * 接收消息列表，處理它們並返回助手的回應消息
     *
     * @param messages 要處理的消息列表
     * @return 助手的回應消息
     */
    suspend fun invoke(messages: List<Message>): Message {
        try {
            // 準備模型 - 確保工具提示已添加
            if (!isToolPromptAdded) {
                addToolPrompt()
            }

            // 重置現有的消息狀態（如果需要）
//            resetModelMessages()

            // 將消息添加到模型
            addMessagesToModel(messages)

            // 生成回應
            val responseText = StringBuilder()
            getResponse().collect { chunk ->
                responseText.append(chunk)
            }
            Log.i(TAG, "LLM Responses: $responseText")

            // 解析回應
            val structuredResponse = convertToStructured(responseText.toString())

            // 創建並返回助手消息
            return Message(
                role = MessageRole.ASSISTANT,
                content = responseText.toString(),
                structuredLLMResponse = structuredResponse
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating response: ${e.message}", e)
            return Message(
                role = MessageRole.ASSISTANT,
                content = "Error generating response: ${e.message}"
            )
        }
    }

    /**
     * 重置模型的消息狀態
     * 根據具體的LLM實現，可能需要清除先前的消息
     */
    open fun resetModelMessages() {
        // 預設實現為空，子類可以根據需要覆蓋此方法
    }

    /**
     * 處理帶有工具調用的消息，並返回工具執行結果消息
     *
     * @param messageWithToolCall 包含工具調用的消息
     * @param previousMessages 對話歷史消息（可選）
     * @return 工具回應消息列表
     */
    suspend fun processToolCalls(messageWithToolCall: Message, previousMessages: List<Message> = emptyList()): List<Message> {
        if (!messageWithToolCall.hasToolCalls()) {
            return listOf(Message(
                role = MessageRole.ASSISTANT,
                content = "No tool calls to process"
            ))
        }

        try {
            val processingResult = processLLMResponse(messageWithToolCall.structuredLLMResponse!!)

            if (processingResult.toolResponses.isEmpty()) {
                return emptyList()
            }

            // 將所有工具調用結果轉換為消息
            return processingResult.toolResponses.map { response ->
                Message.fromToolResponse(response)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing tool calls: ${e.message}", e)
            return listOf(Message(
                role = MessageRole.ASSISTANT,
                content = "Error processing tool calls: ${e.message}"
            ))
        }
    }



    /**
     * 添加消息到模型 - 用於處理工具對話
     */
    fun addMessagesToModel(messages: List<Message>) {
        messages.forEach { message ->
            if (message.queueing) { // 只有當queueing為true時才添加消息到模型
                message.queueing = false
                when (message.role) {
                    MessageRole.SYSTEM -> addSystemMessage(message.content)
                    MessageRole.USER -> addUserMessage(message.content)
                    MessageRole.ASSISTANT -> addAssistantMessage(message.content)
                    MessageRole.TOOL -> addToolMessage(message.content)
                }
                Log.v(TAG,"Add message: ${message.role.name}:${message.content}")
            }
        }
    }

    init {
        runBlocking {
            init_model()
        }
    }

    // 抽象方法，由子類實現
    abstract suspend fun init_model()
    abstract suspend fun close_model()
    abstract fun addSystemMessage(content: String)
    abstract fun addUserMessage(content: String)
    abstract fun addAssistantMessage(content: String)
    abstract fun addToolMessage(content: String)
    abstract fun getResponse(query: String? = null): Flow<String>
}