package com.smith.lai.smithtoolcalls
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

    @OptIn(InternalSerializationApi::class)
    fun serializeParameter(kType: KType? ): KSerializer<out Any>? {
        return (kType?.classifier as? KClass<*>)?.serializerOrNull()
    }

    // 处理单个工具调用
    suspend fun processToolCall(callJson: String): ToolResponse {
        return try {

            try {
                // ToolCallArguments (單 Tool)
                val toolCall = Json.decodeFromString<ToolCallArguments>(callJson)
                processToolCallInternal(toolCall)
            } catch (e: Exception) {
                // ToolCallsArray (多 Tools)
                val toolCalls = Json.decodeFromString<ToolCallsArray>(callJson)
                if (toolCalls.tool_calls.isEmpty()) {
                    throw IllegalArgumentException("No tool calls found in input")
                }
                // 处理第一个工具调用
                processToolCallInternal(toolCalls.tool_calls.first())
            }
        } catch (e: Exception) {
            throw IllegalStateException("Error processing tool call: ${e.message}")
        }
    }

    // 处理工具调用的内部方法
    suspend fun processToolCallInternal(toolCall: ToolCallArguments): ToolResponse {
        val tool = getTool(toolCall.function.name) ?:
        throw IllegalArgumentException("Tool not found: ${toolCall.function.name}")

        val toolClass = tool::class
        val parameterType = toolClass.supertypes.first().arguments.first().type
        val serializer = serializeParameter(parameterType) ?:
        throw IllegalStateException("No serializer found for tool parameters")

        @Suppress("UNCHECKED_CAST")
        val parameters = Json.decodeFromString(serializer, toolCall.function.arguments)
        val result = (tool as BaseTool<Any>).invoke(parameters)

        return ToolResponse(
            id = toolCall.id,
            output = result
        )
    }

    // 批量处理多个工具调用
    suspend fun processToolCalls(callsJson: String): List<ToolResponse> {
        return try {
            val toolCalls = Json.decodeFromString<ToolCallsArray>(callsJson)
            toolCalls.tool_calls.map { processToolCallInternal(it) }
        } catch (e: Exception) {
            throw IllegalStateException("Error processing tool calls: ${e.message}")
        }
    }

    fun generateCallId(): String {
        val timestamp = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString().replace("-", "")
        return "call_${timestamp}_${uuid}"
    }
}