package com.smith.lai.smithtoolcalls
import com.smith.lai.smithtoolcalls.tool_calls.data.BaseTool
import com.smith.lai.smithtoolcalls.tool_calls.data.ToolAnnotation
import com.smith.lai.smithtoolcalls.tool_calls.data.ToolCall
import com.smith.lai.smithtoolcalls.tool_calls.data.ToolCallList
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.reflect.KClass
import kotlin.reflect.full.*
import kotlin.reflect.jvm.isAccessible
import io.github.classgraph.ClassGraph
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializerOrNull
import kotlin.reflect.KType

@Single
class ToolRegistry {
    private val tools = mutableMapOf<String, BaseTool<*>>()
    private val descriptions = mutableMapOf<String, String>()

    fun register(tool: BaseTool<*>, name: String, description: String) {
        tools[name] = tool
        descriptions[name] = description
    }
    fun unregister(name: String) {
        tools.remove(name)
        descriptions.remove(name)
    }
    fun clear() {
        tools.clear()
        descriptions.clear()
    }
    fun getTool(name: String): BaseTool<*>? = tools[name]

    fun listTools(): List<Pair<String, String>> = descriptions.entries.map { it.toPair() }

    // 自動掃描帶 @ToolAnnotation 的類別，並註冊
    fun autoRegister(toolClasses: List<KClass<out BaseTool<*>>>) {
        for (clazz in toolClasses) {
            val annotation = clazz.findAnnotation<ToolAnnotation>()
            if (annotation != null) {
                val instance = clazz.createInstance()
                tools[annotation.name] = instance
                descriptions[annotation.name] = annotation.description
            }
        }
    }

    // **自動掃描並註冊所有帶 @ToolAnnotation 的類別**
    fun autoRegister(packageName: String) {
        val scanResult = ClassGraph()
            .enableClassInfo()
            .enableAnnotationInfo()
            .whitelistPackages(packageName) // 指定要掃描的 package
            .scan()

        val toolClasses = scanResult.getClassesWithAnnotation(ToolAnnotation::class.java.name)

        for (classInfo in toolClasses) {
            val kClass = Class.forName(classInfo.name).kotlin
            val annotation = kClass.findAnnotation<ToolAnnotation>()
            if (annotation != null) {
                val instance = kClass.createInstance() as BaseTool<*>
                tools[annotation.name] = instance
                descriptions[annotation.name] = annotation.description
            }
        }
    }

    private fun generateToolSchema(): String {
        val toolList = listTools().map { (name, description) ->
            """ - {"name": "$name", "description": "$description"}"""
        }
        return toolList.joinToString("\n")
    }
    // 產生 System Prompt
    private fun generateToolDescription(tools: List<Pair<String, String>>): String {
        tools.joinToString("\n") {(name, description) ->
            "- $name: $description"
        }

        return tools.joinToString("\n") { (name, description) -> "- $name: $description" }.trimIndent()
    }

    fun createSystemPrompt(): String {
//        val toolDescriptions = generateToolDescription(listTools())
        val toolSchema = generateToolSchema()
        return "你是一個智能助手，你可以使用以下工具：\n" +
                "${toolSchema}\n" +
                "請用 JSON 格式輸出工具呼叫，例如：\n" +
                "{\"tool\": \"calculation_tool\", \"arguments\": \"42\"}\n"
    }

    @OptIn(InternalSerializationApi::class)
    fun serializerForType(kType: KType?):  KSerializer<out Any>? {
        val kClass = kType?.classifier as? KClass<*>
        return kClass?.serializerOrNull()
    }

    suspend fun processLLMOutput(output: String): String {
        return try {
            val toolCall = Json.decodeFromString<ToolCall>(output)
            val tool = getTool(toolCall.tool) ?: return "錯誤：找不到工具 ${toolCall.tool}"

            val toolClass = tool::class
            val parameterType = toolClass.supertypes.first().arguments.first().type

            if (parameterType == null || parameterType == Unit::class.createType()) {
                @Suppress("UNCHECKED_CAST")
                return (tool as BaseTool<Unit>).invoke(Unit)
            }else {
                val serializer =
                    serializerForType(parameterType) ?: return "錯誤：找不到適用的序列化器"

                // 解析 arguments 並轉型
                val jsonElement = Json.parseToJsonElement(toolCall.arguments.toString())
                val parsedArgs = Json.decodeFromJsonElement(serializer, jsonElement)
                // **正確轉型**
                @Suppress("UNCHECKED_CAST")
                return (tool as BaseTool<Any>).invoke(parsedArgs!!)
            }
        } catch (e: Exception) {
            "錯誤：無法解析 LLM 輸出 -> ${e.localizedMessage}"
        }
    }
}

// 解析 LLM 輸出並執行工具
//public suspend fun processLLMOutput(output: String, toolRegistry: ToolRegistry): String {
//    val regex = Regex("CALL_TOOL: ([a-zA-Z0-9_]+) | (.+)")
//    val match = regex.find(output)
//
//    return if (match != null) {
//        val toolName = match.groupValues[1]
//        val toolInput = match.groupValues[2]
//
//        val tool = toolRegistry.getTool(toolName)
//        if (tool != null) {
//            val method = tool::class.memberFunctions.find { it.name == "execute" }
//            method?.let {
//                it.isAccessible = true
//                return it.callSuspend(tool, toolInput) as String
//            } ?: "錯誤：工具 $toolName 沒有可執行的方法"
//        } else {
//            "錯誤：找不到工具 $toolName"
//        }
//    } else {
//        "LLM 回應: $output"
//    }
//}

//// 擴展函數，支援 suspend 方法調用
//suspend fun kotlin.reflect.KFunction<*>.callSuspend(obj: Any, vararg args: Any?): Any? {
//    return if (isSuspend) {
//        callSuspend(obj, *args)
//    } else {
//        call(obj, *args)
//    }
//}