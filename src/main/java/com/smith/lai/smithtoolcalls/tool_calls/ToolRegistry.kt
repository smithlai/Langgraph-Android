package com.smith.lai.smithtoolcalls
import android.util.Log
import com.smith.lai.smithtoolcalls.tool_calls.tools.BaseTool
import com.smith.lai.smithtoolcalls.tool_calls.tools.FinishReason
import com.smith.lai.smithtoolcalls.tool_calls.tools.ProcessingResult
import com.smith.lai.smithtoolcalls.tool_calls.tools.ResponseMetadata
import com.smith.lai.smithtoolcalls.tool_calls.tools.StructuredLLMResponse
import com.smith.lai.smithtoolcalls.tool_calls.tools.TokenUsage
import com.smith.lai.smithtoolcalls.tool_calls.tools.ToolAnnotation
import com.smith.lai.smithtoolcalls.tool_calls.tools.ToolCallInfo
import com.smith.lai.smithtoolcalls.tool_calls.tools.ToolCallsArray
import com.smith.lai.smithtoolcalls.tool_calls.tools.ToolResponse
import com.smith.lai.smithtoolcalls.tool_calls.tools.ToolResponseType
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.reflect.KClass
import kotlin.reflect.full.*
import io.github.classgraph.ClassGraph
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import java.util.UUID

@Single
class ToolRegistry {
    private val tools = mutableMapOf<String, BaseTool<*, *>>()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun unregister(name: String) {
        tools.remove(name)
    }

    fun clear() {
        tools.clear()
    }

    fun register(tool: BaseTool<*, *>) {
        val annotation = tool::class.findAnnotation<ToolAnnotation>() ?:
        throw IllegalArgumentException("Tool must have @Tool annotation")
        tools[annotation.name] = tool
    }

    fun register(toolClass: KClass<out BaseTool<*, *>>) {
        val instance = toolClass.createInstance()
        register(instance)
    }

    fun register(toolClasses: List<KClass<out BaseTool<*, *>>>) {
        toolClasses.forEach { register(it) }
    }

    fun scanTools(packageName: String) {
        val scanResult = ClassGraph()
            .enableClassInfo()
            .enableAnnotationInfo()
            .whitelistPackages(packageName)
            .scan()

        val toolClassInfos = scanResult.getClassesWithAnnotation(ToolAnnotation::class.java.name)
        val toolClasses = toolClassInfos.map {
            @Suppress("UNCHECKED_CAST")
            Class.forName(it.name).kotlin as KClass<out BaseTool<*,*>>
        }
        register(toolClasses)
    }

    fun getTool(name: String): BaseTool<*, *>? = tools[name]

    fun getToolNames(): List<String> = tools.keys.toList()

    fun getTools(): List<BaseTool<*, *>> = tools.values.toList()

    // 创建适合Llama 3.2的系统提示
    fun createSystemPrompt(): String {
        val toolSchemas = tools.values.map { it.getJsonSchema() }
        return """
You are an AI assistant with access to the following tools:

${if (tools.size == 0) "(No tool available)" else Json.encodeToString(toolSchemas)}

To use a tool, respond with a JSON object in the following format:
{
    "tool_calls": [
        {
            "id": "call_xxxx",
            "type": "function",
            "function": {
                "name": "tool_name",
                "arguments": "{\"param1\": value1, \"param2\": value2}"
            }
        }
    ]
}

The arguments should be a valid JSON string matching the tool's parameter schema.

You can reply user's request with suitable tools listed above while you need, don't use any tool not listed above.
If there is no tools available, reply user's request directly.
"""
    }

    fun generateCallId(): String {
        val timestamp = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString().replace("-", "")
        return "call_${timestamp}_${uuid}"
    }

    /**
     * 轉換LLM的原始回應為結構化格式
     * 使用現有的ToolCallsArray和ToolCallArguments
     */
    fun convertToStructured(llmResponse: String): StructuredLLMResponse {
        try {
            // 檢查是否是JSON格式
            val trimmedResponse = llmResponse.trim()
            if (!trimmedResponse.startsWith("{") || !trimmedResponse.endsWith("}")) {
                // 如果不是JSON格式，視為直接文本回應
                return StructuredLLMResponse(
                    content = llmResponse,
                    metadata = ResponseMetadata(
                        tokenUsage = estimateTokenUsage(llmResponse),
                        finishReason = FinishReason.STOP.value
                    )
                )
            }

            // 嘗試解析為工具調用格式
            try {
                val toolCallsArray = json.decodeFromString<ToolCallsArray>(trimmedResponse)
                val toolCalls = toolCallsArray.toToolCallsList()

                return StructuredLLMResponse(
                    toolCalls = toolCalls,
                    metadata = ResponseMetadata(
                        tokenUsage = estimateTokenUsage(llmResponse),
                        finishReason = FinishReason.TOOL_CALLS.value
                    )
                )
            } catch (e: Exception) {
                // 如果不是標準工具調用格式，可能是其他JSON格式的回應
                Log.d("ToolRegistry", "Not a standard tool call format: ${e.message}")
                return StructuredLLMResponse(
                    content = llmResponse,
                    metadata = ResponseMetadata(
                        tokenUsage = estimateTokenUsage(llmResponse),
                        finishReason = FinishReason.STOP.value
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("ToolRegistry", "Error converting LLM response: ${e.message}")
            // 出現任何異常，返回原始文本作為內容
            return StructuredLLMResponse(
                content = llmResponse,
                metadata = ResponseMetadata(
                    finishReason = FinishReason.ERROR.value
                )
            )
        }
    }

    /**
     * 根據回應文本估算token使用情況
     * 注意：這是一個簡單的估算，實際token數可能不同
     */
    private fun estimateTokenUsage(response: String): TokenUsage {
        // Todo: overwrite with JNI
        // 一個粗略的估算：大約每4個字符等於1個token
        val tokens = (response.length / 4) + 1
        return TokenUsage(
            completionTokens = tokens,
            totalTokens = tokens
        )
    }

    /**
     * 處理LLM回應的整個流程：從原始回應到工具執行結果
     * 優化：使用已解析的結構化回應來執行工具，避免重複解析
     */
    @OptIn(InternalSerializationApi::class)
    suspend fun processLLMResponse(llmResponse: String): ProcessingResult {
        try {
            // 將LLM回應轉換為結構化格式
            val structuredResponse = convertToStructured(llmResponse)

            // 如果不包含工具調用，創建一個直接回應
            if (!structuredResponse.hasToolCalls()) {
                return ProcessingResult(
                    structuredResponse = structuredResponse,
                    toolResponses = listOf(
                        ToolResponse(
                            id = generateCallId(),
                            type = ToolResponseType.DIRECT_RESPONSE,
                            output = structuredResponse.content
                        )
                    ),
                    requiresFollowUp = false
                )
            }

            // 使用已解析的結構化回應執行工具
            val toolResponses = executeTools(structuredResponse.toolCalls)

            return ProcessingResult(
                structuredResponse = structuredResponse,
                toolResponses = toolResponses,
                requiresFollowUp = toolResponses.none { it.type == ToolResponseType.DIRECT_RESPONSE }
            )
        } catch (e: Exception) {
            Log.e("ToolRegistry", "Error processing LLM response: ${e.message}")
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
                        output = "Error processing response: ${e.message}"
                    )
                ),
                requiresFollowUp = false
            )
        }
    }

    /**
     * 使用已解析的工具調用信息執行工具
     */
    @OptIn(InternalSerializationApi::class)
    private suspend fun executeTools(toolCalls: List<ToolCallInfo>): List<ToolResponse<*>> {
        return toolCalls.map { toolCall ->
            val tool = getTool(toolCall.function.name)
            if (tool == null) {
                return@map ToolResponse(
                    id = toolCall.id,
                    type = ToolResponseType.ERROR,
                    output = "Tool ${toolCall.function.name} not found"
                )
            }
            try {
                val parameterType = tool.getParameterType()
                    ?: return@map ToolResponse(
                        id = toolCall.id,
                        type = ToolResponseType.ERROR,
                        output = "Could not determine parameter type for tool"
                    )

                val arguments = json.decodeFromString(
                    parameterType.serializer(),
                    toolCall.function.arguments
                )

                @Suppress("UNCHECKED_CAST")
                val result = (tool as BaseTool<Any, Any>).invoke(arguments)

                return@map ToolResponse(
                    id = toolCall.id,
                    output = result
                )
            } catch (e: Exception) {
                return@map ToolResponse(
                    id = toolCall.id,
                    type = ToolResponseType.ERROR,
                    output = "Error executing tool: ${e.message}"
                )
            }
        }
    }

    /**
     * 處理原始JSON回應並執行工具
     * 為了向後兼容保留此方法，但內部使用新的處理流程
     */
    @OptIn(InternalSerializationApi::class)
    suspend fun handleToolExecution(response: String): List<ToolResponse<*>> {
        try {
            val processingResult = processLLMResponse(response)
            return processingResult.toolResponses
        } catch (e: Exception) {
            Log.e("ToolRegistry", e.toString())
            throw e
        }
    }

    /**
     * 將工具回應格式化為用於發送回LLM的JSON字符串
     */
    fun formatToolResponsesToJson(toolResponses: List<ToolResponse<*>>): String {
        return buildString {
            append("[")
            toolResponses.forEachIndexed { index, response ->
                if (index > 0) append(",")
                append("""
                    {
                        "id": "${response.id}",
                        "type": "${response.type}",
                        "output": ${formatOutputAsJson(response.output)}
                    }
                """.trimIndent())
            }
            append("]")
        }
    }

    /**
     * 將輸出格式化為JSON字符串
     */
    private fun formatOutputAsJson(output: Any?): String {
        return when (output) {
            null -> "null"
            is Number -> output.toString()
            is Boolean -> output.toString()
            is String -> "\"${output.replace("\"", "\\\"")}\""
            else -> "\"${output.toString().replace("\"", "\\\"")}\""
        }
    }
}