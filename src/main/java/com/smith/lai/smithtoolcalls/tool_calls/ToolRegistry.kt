package com.smith.lai.smithtoolcalls
import android.util.Log
import com.smith.lai.smithtoolcalls.tool_calls.tools.BaseTool
import com.smith.lai.smithtoolcalls.tool_calls.tools.Tool
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

    fun unregister(name: String) {
        tools.remove(name)
    }

    fun clear() {
        tools.clear()
    }

    fun register(tool: BaseTool<*, *>) {
        val annotation = tool::class.findAnnotation<Tool>() ?:
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

        val toolClassInfos = scanResult.getClassesWithAnnotation(Tool::class.java.name)
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

    @OptIn(InternalSerializationApi::class)
    suspend fun handleToolExecution(response: String): List<ToolResponse<*>> {
        try {

            // 嘗試確定回應是否是JSON格式的工具調用
            val trimmedResponse = response.trim()
            if (!trimmedResponse.startsWith("{") || !trimmedResponse.endsWith("}")) {
                // 如果不是JSON格式，將回應作為直接回答處理
                return listOf(ToolResponse(
                    id = generateCallId(),
                    type = ToolResponseType.DIRECT_RESPONSE,
                    output = response as Any
                ))
            }

            val json = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

            val toolCalls = json.decodeFromString<ToolCallsArray>(response)
            return toolCalls.toToolCallsList().map { toolCall ->
                val tool = getTool(toolCall.function.name)
                if (tool == null) {
//                    throw IllegalArgumentException("Tool ${toolCall.function.name} not found")
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

//                    val returnType = tool.getReturnType()
//                        ?: throw IllegalStateException("Could not determine return type for tool")

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
        } catch (e: Exception) {
            Log.e("ToolRegistry", e.toString())
            throw e
        }
    }
}