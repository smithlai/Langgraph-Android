package com.smith.lai.smithtoolcalls.langgraph.model

import android.util.Log
import com.smith.lai.smithtoolcalls.langgraph.tools.BaseTool
import com.smith.lai.smithtoolcalls.langgraph.response.FinishReason
import com.smith.lai.smithtoolcalls.langgraph.tools.ToolAnnotation
import com.smith.lai.smithtoolcalls.langgraph.model.adapter.BaseLLMToolAdapter
import com.smith.lai.smithtoolcalls.langgraph.response.LLMResponseMetadata
import com.smith.lai.smithtoolcalls.langgraph.response.StructuredLLMResponse
import com.smith.lai.smithtoolcalls.langgraph.state.Message
import com.smith.lai.smithtoolcalls.langgraph.state.MessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
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
                metadata = LLMResponseMetadata(
                    finishReason = FinishReason.ERROR.value
                )
            )
        }
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