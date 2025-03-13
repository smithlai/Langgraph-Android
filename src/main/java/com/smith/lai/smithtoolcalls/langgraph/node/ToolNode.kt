package com.smith.lai.smithtoolcalls.langgraph.node

import android.util.Log
import com.smith.lai.smithtoolcalls.langgraph.model.LLMWithTools
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState
import com.smith.lai.smithtoolcalls.langgraph.state.Message
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
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation

/**
 * 工具節點 - 處理工具調用
 */
class ToolNode<S : GraphState>(
    protected val model: LLMWithTools
) : Node<S>() {
    private val TAG = "ToolNode"
    private val tools = mutableMapOf<String, BaseTool<*, *>>()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 綁定單個工具
     */
    fun bind_tools(tool: BaseTool<*, *>): ToolNode<S> {
        val annotation = tool::class.findAnnotation<ToolAnnotation>() ?:
        throw IllegalArgumentException("Tool must have @Tool annotation")
        tools[annotation.name] = tool
        // 同時也綁定到模型中，保持同步
        model.bind_tools(tool)
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
     * 獲取已綁定的工具
     */
    fun getTools(): List<BaseTool<*, *>> = tools.values.toList()

    /**
     * 根據名稱獲取工具
     */
    fun getTool(name: String): BaseTool<*, *>? = tools[name]

    /**
     * 核心處理邏輯
     */
    override suspend fun invoke(state: S): S {
        // 獲取最後一條包含工具調用的消息
        val messageWithToolCall = state.getLastToolCallsMessage()
            ?: return state.withError("No tool calls to process") as S

        try {
            // 從消息中獲取結構化的LLM回應
            val structuredResponse = messageWithToolCall.structuredLLMResponse
                ?: return state.withError("No structured response in message") as S

            // 使用節點內部的executeTools方法處理工具調用
            val processingResult = processLLMResponse(structuredResponse)

            // 將工具回應消息添加到狀態
            processingResult.toolResponses.forEach { toolResponse ->
                val toolMessage = Message.fromToolResponse(toolResponse)
                state.addMessage(toolMessage)
            }

            return state
        } catch (e: Exception) {
            Log.e(TAG, "Error processing tool calls: ${e.message}", e)
            return state.withError("Error processing tool calls: ${e.message}") as S
        }
    }

    /**
     * 處理結構化LLM回應
     */
    private suspend fun processLLMResponse(structuredResponse: StructuredLLMResponse): ProcessingResult {
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
     * 生成調用ID
     */
    private fun generateCallId(): String {
        val timestamp = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString().replace("-", "")
        return "call_${timestamp}_${uuid}"
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

            // 優先從本地工具映射中尋找工具
            val tool = getTool(toolCall.function.name) ?: model.getTool(toolCall.function.name)

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
}