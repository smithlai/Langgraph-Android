package com.smith.lai.toolcalls.langgraph.node

import android.util.Log
import com.smith.lai.toolcalls.langgraph.response.ToolFollowUpMetadata
import com.smith.lai.toolcalls.langgraph.response.ToolResponse
import com.smith.lai.toolcalls.langgraph.state.GraphState
import com.smith.lai.toolcalls.langgraph.state.Message
import com.smith.lai.toolcalls.langgraph.tools.BaseTool
import com.smith.lai.toolcalls.langgraph.tools.ToolAnnotation
import com.smith.lai.toolcalls.langgraph.tools.ToolCallInfo
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation

/**
 * 工具節點 - 處理工具調用
 * 只執行工具，不接觸狀態
 */
class ToolNode<S : GraphState>(
    toolsList: List<BaseTool<*, *>> = emptyList()
) : Node<S>() {
    private val TAG = "ToolNode"
    private val tools: MutableMap<String, BaseTool<*, *>> = mutableMapOf()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    init {
        // 在初始化時直接綁定工具列表
        if (toolsList.isNotEmpty()) {
            bindToolsList(toolsList)
        }
    }

    /**
     * 綁定工具列表，初始化工具映射
     */
    private fun bindToolsList(toolsList: List<BaseTool<*, *>>) {
        toolsList.forEach { tool ->
            val annotation = tool::class.findAnnotation<ToolAnnotation>() ?:
            throw IllegalArgumentException("Tool must have @Tool annotation")
            tools[annotation.name] = tool
            Log.d(TAG, "Bound tool: ${annotation.name}")
        }
    }

    /**
     * 綁定單個工具
     */
    fun bind_tools(tool: BaseTool<*, *>): ToolNode<S> {
        val annotation = tool::class.findAnnotation<ToolAnnotation>() ?:
        throw IllegalArgumentException("Tool must have @Tool annotation")
        tools[annotation.name] = tool
        Log.d(TAG, "Bound tool: ${annotation.name}")
        return this
    }

    /**
     * 綁定工具類
     */
    fun bind_tools(toolClass: KClass<out BaseTool<*, *>>): ToolNode<S> {
        val instance = toolClass.createInstance()
        bind_tools(instance)
        return this
    }

    /**
     * 綁定多個工具
     */
    fun bind_tools(toolList: List<BaseTool<*, *>>): ToolNode<S> {
        toolList.forEach { bind_tools(it) }
        return this
    }

    /**
     * 根據名稱獲取工具
     */
    fun getTool(name: String): BaseTool<*, *>? = tools[name]

    /**
     * 核心處理邏輯 - 只執行工具，不接觸狀態
     */
    override suspend fun invoke(state: S): List<Message> {
        Log.d(TAG, "Executing tools")

        // 獲取最後一條包含工具調用的消息
        val messageWithToolCall = state.getLastToolCallsMessage()
            ?: throw IllegalStateException("No message with tool calls found in the state")

        // 從消息中獲取結構化的LLM回應
        val structuredLLMResponse = messageWithToolCall.structuredLLMResponse
            ?: throw IllegalStateException("No structured LLM response found in the message with tool calls")

        // 處理工具調用
        val toolResponses = executeTools(structuredLLMResponse.toolCalls)

        val messageList = mutableListOf<Message>()
        toolResponses.forEach { response ->
            // Only add a tool message if the tool requires follow-up
            if (response.followUpMetadata.requiresFollowUp) {
                val toolMessage = Message.fromToolResponse(response)
                messageList.add(toolMessage)
            } else {
                // For tools that don't require follow-up, log but don't add message
                Log.d(
                    TAG,
                    "Tool ${response.id} does not require follow-up, skipping message creation"
                )
            }
        }

        // 返回工具響應列表
        return messageList
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

            // 從本地工具映射中尋找工具
            val tool = getTool(toolCall.name)

            if (tool == null) {
                // 處理找不到工具的情況
                val errorResponse = ToolResponse(
                    id = toolCall.id,
                    output = "Tool ${toolCall.name} not found",
                    followUpMetadata = ToolFollowUpMetadata(requiresFollowUp = false)
                )
                toolResponses.add(errorResponse)
                continue
            }

            try {
                val parameterType = tool.getParameterType()
                    ?: throw IllegalStateException("Could not determine parameter type for tool")

                val arguments = json.decodeFromString(
                    parameterType.serializer(),
                    toolCall.arguments
                )

                // 執行工具
                @Suppress("UNCHECKED_CAST")
                val result = (tool as BaseTool<Any, Any>).invoke(arguments)

                // 獲取後續處理元數據
                @Suppress("UNCHECKED_CAST")
                val metadata = tool.getFollowUpMetadata(result)

                // Log the follow-up metadata
                Log.d(TAG, "Tool ${toolCall.name} follow-up metadata: requiresFollowUp=${metadata.requiresFollowUp}")

                // 創建包含後續處理元數據的工具回應
                val response = ToolResponse(
                    id = toolCall.id,
                    output = result,
                    followUpMetadata = metadata
                )
                toolResponses.add(response)

            } catch (e: Exception) {
                Log.e(TAG, "ToolNode Error: ${e.message}", e)
                // 處理執行錯誤
                val errorResponse = ToolResponse(
                    id = toolCall.id,
                    output = "Error executing tool: ${e.message}",
                    followUpMetadata = ToolFollowUpMetadata(requiresFollowUp = false)
                )
                toolResponses.add(errorResponse)
            }
        }

        return toolResponses
    }

    /**
     * 生成調用ID
     */
    private fun generateCallId(): String {
        val timestamp = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString().replace("-", "")
        return "call_${timestamp}_${uuid}"
    }
}