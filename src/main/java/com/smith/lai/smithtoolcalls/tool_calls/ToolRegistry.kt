package com.smith.lai.smithtoolcalls
import com.smith.lai.smithtoolcalls.tool_calls.data.BaseTool
import com.smith.lai.smithtoolcalls.tool_calls.data.ToolAnnotation
import com.smith.lai.smithtoolcalls.tool_calls.data.ToolCall
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.reflect.KClass
import kotlin.reflect.full.*
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
        return tools.entries.joinToString("\n") { (name, tool) ->
            val description = descriptions[name] ?: "No description available"
            val parameters = getToolParameters(tool)
            val para_desc = if (parameters?.isNotEmpty() == true){
                "[${parameters.joinToString(", ")}]"
            }else{
                "No parameters available"
            }
            """ - {"name": "$name", "description": "$description", "parameters": $para_desc}"""
        }
    }
    fun createSystemPrompt(): String {
        val toolSchema = generateToolSchema()
        return """
你是一個智能助手，可以使用以下工具來執行任務：
${toolSchema}

每個工具有一個名稱、一個描述和一組參數。參數可以是數字、字串等類型，請根據每個工具的參數描述來提供正確的參數格式。

當你需要執行某個工具時，請生成一個 JSON 物件，包含以下欄位：
- "tool": 工具的名稱
- "arguments": 工具需要的參數（可以是字串、數字或其他 JSON 支援的格式）

例如，如果你想使用名為 "calculation_tool_add" 的工具來處理 42 + 43，你應該這樣構建 JSON：
{"tool": "calculation_tool_add", "arguments": [42, 43]}

根據上述工具名稱、描述和參數類型，請選擇合適的工具並輸出相應的 JSON 格式呼叫。
    """.trimIndent()
    }

    // see if the data class parameter is with @Serializable
    @OptIn(InternalSerializationApi::class)
    private fun serializerForType(kType: KType?):  KSerializer<out Any>? {
        val kClass = kType?.classifier as? KClass<*>
        return kClass?.serializerOrNull()
    }

    fun getToolParameters(tool: BaseTool<*>): List<String>? {
        val toolClass = tool::class
        val parameter1Type = toolClass.supertypes.firstOrNull()?.arguments?.firstOrNull()?.type
        val kClass = parameter1Type?.classifier as? KClass<*>
        return kClass?.declaredMemberProperties?.map { it.name + ": " + it.returnType.classifier.toString() }
    }

    suspend fun processLLMOutput(output: String): String {
        return try {
            val toolCall = Json.decodeFromString<ToolCall>(output)
            val tool = getTool(toolCall.tool) ?: return "錯誤：找不到工具 ${toolCall.tool}"

            val toolClass = tool::class
            val parameter1Type = toolClass.supertypes.first().arguments.first().type

            if (parameter1Type == null || parameter1Type == Unit::class.createType()) {
                @Suppress("UNCHECKED_CAST")
                return (tool as BaseTool<Unit>).invoke(Unit)
            }else {
                val serializer =
                    serializerForType(parameter1Type) ?: return "錯誤：找不到適用的序列化器"

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