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
 * 帶有工具的LLM實現
 */
abstract class LLMWithTools(
    private val adapter: BaseLLMToolAdapter,
    private val TAG:String = "LLMWithTools"
) {
    private val tools = mutableMapOf<String, BaseTool<*, *>>()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Track whether tool prompt has been added
    private var isToolPromptAdded = false

//    fun unregister(name: String) {
//        tools.remove(name)
//    }
//
//    fun clear() {
//        tools.clear()
//    }

    fun bind_tools(tool: BaseTool<*, *>) {
        val annotation = tool::class.findAnnotation<ToolAnnotation>() ?:
        throw IllegalArgumentException("Tool must have @Tool annotation")
        tools[annotation.name] = tool
        // Reset prompt status when tools are modified
        isToolPromptAdded = false
    }

    fun bind_tools(toolClass: KClass<out BaseTool<*, *>>) {
        val instance = toolClass.createInstance()
        bind_tools(instance)
    }

    //    fun bind_tools(toolClasses: List<KClass<out BaseTool<*, *>>>) {
//        toolClasses.forEach { bind_tools(it) }
//    }
    fun bind_tools(tools: List<BaseTool<*, *>>) {
        tools.forEach { bind_tools(it) }
    }

//    fun scanTools(packageName: String) {
//        val scanResult = ClassGraph()
//            .enableClassInfo()
//            .enableAnnotationInfo()
//            .whitelistPackages(packageName)
//            .scan()
//
//        val toolClassInfos = scanResult.getClassesWithAnnotation(ToolAnnotation::class.java.name)
//        val toolClasses = toolClassInfos.map {
//            @Suppress("UNCHECKED_CAST")
//            Class.forName(it.name).kotlin as KClass<out BaseTool<*, *>>
//        }
//        bind_tools(toolClasses)
//    }

    fun getTool(name: String): BaseTool<*, *>? = tools[name]

    fun getToolNames(): List<String> = tools.keys.toList()

    fun getTools(): List<BaseTool<*, *>> = tools.values.toList()

    fun createToolPrompt() : String{
        return adapter?.createToolPrompt(getTools()) ?: ""
    }

    // New method to add tool prompt to the model and mark as added
    fun addToolPrompt() {
        if (!isToolPromptAdded) {
            val systemPrompt = createToolPrompt()
            Log.d(TAG, "Adding ToolPrompt(${systemPrompt.length})")
            addSystemMessage(systemPrompt)
            isToolPromptAdded = true
        }
    }

    // Check if tool prompt has been added
    fun isToolPromptAdded(): Boolean {
        return isToolPromptAdded
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
            return adapter.parseResponse(llmResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting LLM response: ${e.message}")
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
     * 處理LLM回應的整個流程：從原始回應到工具執行結果
     */
    suspend fun processLLMResponse(llmResponse: String): ProcessingResult {
        val structuredResponse = convertToStructured(llmResponse)
        return processLLMResponse(structuredResponse)
    }
    /**
     * 處理LLM回應的整個流程：從原始回應到工具執行結果
     */
    suspend fun processLLMResponse(structuredResponse: StructuredLLMResponse): ProcessingResult {
        try {
            // 將LLM回應轉換為結構化格式
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
    init{
        runBlocking {
            init_model()
        }
    }
    abstract suspend fun init_model()
    abstract suspend fun close_model()

    abstract fun addSystemMessage(content: String)
    abstract fun addUserMessage(content:String)
    abstract fun addAssistantMessage(content:String)
    abstract fun addToolMessage(content:String)
    abstract fun getResponse(query: String? = null): Flow<String>
}