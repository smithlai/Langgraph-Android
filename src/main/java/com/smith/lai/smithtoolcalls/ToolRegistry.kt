package com.smith.lai.smithtoolcalls

import android.util.Log
import com.smith.lai.smithtoolcalls.tools.BaseTool
import com.smith.lai.smithtoolcalls.tools.FinishReason
import com.smith.lai.smithtoolcalls.tools.ProcessingResult
import com.smith.lai.smithtoolcalls.tools.ResponseMetadata
import com.smith.lai.smithtoolcalls.tools.StructuredLLMResponse
import com.smith.lai.smithtoolcalls.tools.TokenUsage
import com.smith.lai.smithtoolcalls.tools.ToolAnnotation
import com.smith.lai.smithtoolcalls.tools.ToolCallInfo
import com.smith.lai.smithtoolcalls.tools.ToolFollowUpMetadata
import com.smith.lai.smithtoolcalls.tools.ToolResponse
import com.smith.lai.smithtoolcalls.tools.ToolResponseType
import com.smith.lai.smithtoolcalls.tools.llm_adapter.BaseLLMToolAdapter
import com.smith.lai.smithtoolcalls.tools.llm_adapter.Llama3_2_3B_LLMToolAdapter
import io.github.classgraph.ClassGraph
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.koin.core.annotation.Single
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation

@Single
class ToolRegistry {
    private val tools = mutableMapOf<String, BaseTool<*, *>>()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // 新增：保存當前 Translator 實例
    private var translator: BaseLLMToolAdapter? = null

    /**
     * 設置 Translator
     */
    fun setLLMToolAdapter(newTranslator: BaseLLMToolAdapter) {
        this.translator = newTranslator
    }

    /**
     * 獲取當前 Translator
     * 如果未設置，默認創建 Llama3_2_Translator
     */
    fun getTranslator(): BaseLLMToolAdapter {
        if (translator == null) {
            translator = Llama3_2_3B_LLMToolAdapter()
        }
        return translator!!
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
            Class.forName(it.name).kotlin as KClass<out BaseTool<*, *>>
        }
        register(toolClasses)
    }

    fun getTool(name: String): BaseTool<*, *>? = tools[name]

    fun getToolNames(): List<String> = tools.keys.toList()

    fun getTools(): List<BaseTool<*, *>> = tools.values.toList()

    fun createSystemPrompt() : String{
        return translator?.createSystemPrompt(getTools()) ?: ""
    }

    fun generateCallId(): String {
        val timestamp = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString().replace("-", "")
        return "call_${timestamp}_${uuid}"
    }

    /**
     * 轉換LLM的原始回應為結構化格式
     * 使用當前設置的 Translator 來解析回應
     */
    fun convertToStructured(llmResponse: String): StructuredLLMResponse {
        try {
            // 使用 Translator 解析回應
            return getTranslator().parseResponse(llmResponse)
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
     */
    suspend fun processLLMResponse(llmResponse: String): ProcessingResult {
        try {
            // 將LLM回應轉換為結構化格式
            val structuredResponse = convertToStructured(llmResponse)
            println("structuredResponse:" + structuredResponse.toolCalls.size)
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
                        output = "Error processing response: ${e.message}",
                        followUpMetadata = ToolFollowUpMetadata(requiresFollowUp = false)
                    )
                )
            )
        }
    }
    @OptIn(InternalSerializationApi::class)
    private suspend fun executeTools(toolCalls: List<ToolCallInfo>): List<ToolResponse<*>> {
        val toolResponses = mutableListOf<ToolResponse<*>>()

        for (toolCall in toolCalls) {
            val tool = getTool(toolCall.function.name)
            if (tool == null) {
                // 處理找不到工具的情況
                val errorResponse = ToolResponse(
                    id = toolCall.id,
                    type = ToolResponseType.ERROR,
                    output = "Tool ${toolCall.function.name} not found",
                    // 默認的後續處理元數據
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
                    // 默認後續處理元數據
                    followUpMetadata = ToolFollowUpMetadata(requiresFollowUp = true)
                )
                toolResponses.add(errorResponse)
            }
        }

        return toolResponses
    }


//    /**
//     * 處理原始回應並執行工具
//     * 為了向後兼容保留此方法，但內部使用新的處理流程
//     */
//    @OptIn(InternalSerializationApi::class)
//    suspend fun handleToolExecution(response: String): List<ToolResponse<*>> {
//        try {
//            val processingResult = processLLMResponse(response)
//            return processingResult.toolResponses
//        } catch (e: Exception) {
//            Log.e("ToolRegistry", e.toString())
//            throw e
//        }
//    }

//    /**
//     * 將工具回應格式化為用於發送回LLM的JSON字符串
//     */
//    fun formatToolResponsesToJson(toolResponses: List<ToolResponse<*>>): String {
//        return buildString {
//            append("[")
//            toolResponses.forEachIndexed { index, response ->
//                if (index > 0) append(",")
//                append("""
//                    {
//                        "id": "${response.id}",
//                        "type": "${response.type}",
//                        "output": ${formatOutputAsJson(response.output)}
//                    }
//                """.trimIndent())
//            }
//            append("]")
//        }
//    }

//    /**
//     * 將輸出格式化為JSON字符串
//     */
//    private fun formatOutputAsJson(output: Any?): String {
//        return when (output) {
//            null -> "null"
//            is Number -> output.toString()
//            is Boolean -> output.toString()
//            is String -> "\"${output.replace("\"", "\\\"")}\""
//            else -> "\"${output.toString().replace("\"", "\\\"")}\""
//        }
//    }
}