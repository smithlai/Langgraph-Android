package com.smith.lai.smithtoolcalls
import android.util.Log
import com.smith.lai.smithtoolcalls.tool_calls.data.BaseTool
import com.smith.lai.smithtoolcalls.tool_calls.data.Tool
import com.smith.lai.smithtoolcalls.tool_calls.data.ToolCallArguments
import com.smith.lai.smithtoolcalls.tool_calls.data.ToolCallsArray
import com.smith.lai.smithtoolcalls.tool_calls.data.ToolResponse
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.reflect.KClass
import kotlin.reflect.full.*
import io.github.classgraph.ClassGraph
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull
import java.util.UUID
import kotlin.reflect.KType

@Single
class ToolRegistry {
    private val tools = mutableMapOf<String, BaseTool<*>>()

    fun unregister(name: String) {
        tools.remove(name)
    }

    fun clear() {
        tools.clear()
    }

    fun register(tool: BaseTool<*>) {
        val annotation = tool::class.findAnnotation<Tool>() ?:
        throw IllegalArgumentException("Tool must have @Tool annotation")
        tools[annotation.name] = tool
    }

    fun register(toolClass: KClass<out BaseTool<*>>) {
        val instance = toolClass.createInstance()
        register(instance)
    }

    fun register(toolClasses: List<KClass<out BaseTool<*>>>) {
        toolClasses.forEach { register(it) }
    }

    fun scanTools(packageName: String) {
        val scanResult = ClassGraph()
            .enableClassInfo()
            .enableAnnotationInfo()
            .whitelistPackages(packageName)
            .scan()

        val toolClassInfos = scanResult.getClassesWithAnnotation(Tool::class.java.name)
        val toolClasses = toolClassInfos.map {
            @Suppress("UNCHECKED_CAST")
            Class.forName(it.name).kotlin as KClass<out BaseTool<*>>
        }
        register(toolClasses)
    }

    fun getTool(name: String): BaseTool<*>? = tools[name]

    fun getToolNames(): List<String> = tools.keys.toList()

    fun getTools(): List<BaseTool<*>> = tools.values.toList()

    // 创建适合Llama 3.2的系统提示
    fun createSystemPrompt(): String {
        val toolSchemas = tools.values.map { it.getJsonSchema() }
        return """
You are an AI assistant with access to the following tools:

${Json.encodeToString(toolSchemas)}

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
""".trimIndent()
    }

    fun generateCallId(): String {
        val timestamp = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString().replace("-", "")
        return "call_${timestamp}_${uuid}"
    }

    @OptIn(InternalSerializationApi::class)
    suspend fun handleToolExecution(response: String): List<ToolResponse> {
        try {
            // 解析 LLM 的響應為 ToolCallsArray
            val toolCalls = Json.decodeFromString<ToolCallsArray>(response)
            // 處理每個工具調用
            return toolCalls.tool_calls.map { toolCall ->
                Log.e("handleToolExecution", toolCall.toString())
                val tool = getTool(toolCall.function.name)
                if (tool == null) {
                    ToolResponse(
                        id = toolCall.id,
                        output = "Error: Tool ${toolCall.function.name} not found"
                    )
                } else {
                    try {
                        // 獲取參數類型
                        val parameterType = tool.getParameterType()
                            ?: return@map ToolResponse(
                                id = toolCall.id,
                                output = "Error: Could not determine parameter type for tool"
                            )

                        // 使用參數類型的序列化器解析參數
                        val serializer = parameterType.serializer()
                        val arguments = Json.decodeFromString(serializer, toolCall.function.arguments)

                        // 執行工具
                        @Suppress("UNCHECKED_CAST")
                        val result = (tool as BaseTool<Any>).invoke(arguments)

                        ToolResponse(
                            id = toolCall.id,
                            output = result
                        )
                    } catch (e: Exception) {
                        ToolResponse(
                            id = toolCall.id,
                            output = "Error executing tool: ${e.message}"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ToolRegistry",e.toString())
            return emptyList()
        }
    }
}