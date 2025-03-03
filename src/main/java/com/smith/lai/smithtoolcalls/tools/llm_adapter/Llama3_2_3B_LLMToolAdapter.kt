package com.smith.lai.smithtoolcalls.tools.llm_adapter

import com.smith.lai.smithtoolcalls.tools.BaseTool
import com.smith.lai.smithtoolcalls.tools.FinishReason
import com.smith.lai.smithtoolcalls.tools.FunctionCall
import com.smith.lai.smithtoolcalls.tools.ResponseMetadata
import com.smith.lai.smithtoolcalls.tools.StructuredLLMResponse
import com.smith.lai.smithtoolcalls.tools.TokenUsage
import com.smith.lai.smithtoolcalls.tools.ToolAnnotation
import com.smith.lai.smithtoolcalls.tools.ToolCallInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import android.util.Log
import kotlinx.serialization.json.add
import java.util.UUID
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

class Llama3_2_3B_LLMToolAdapter : BaseLLMToolAdapter() {

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "    " // 4個空格作為縮進
    }


    //  https://www.llama.com/docs/model-cards-and-prompt-formats/llama3_2/
    //  https://www.llama.com/docs/model-cards-and-prompt-formats/llama3_1/#-supported-roles-
    //  Llama3.2 system prompt and role
    override fun createSystemPrompt(tools: List<BaseTool<*, *>>): String {
        val function_definitions = toolSchemas(tools)

        return """
You are an expert in composing functions. You are given a question and a set of possible functions. 
Based on the question, you will need to make one or more function/tool calls to achieve the purpose. 
If none of the function can be used, point it out. If the given question lacks the parameters required by the function,
also point it out. You should only return the function call in tools call sections.

If you decide to invoke any of the function(s), you MUST put it in the format of [func_name1(params_name1=params_value1, params_name2=params_value2...), func_name2(params)]
Even if a function has no parameters, you MUST still include the parentheses: [func_name()]
You SHOULD NOT include any other text in the response.

Here is a list of functions in JSON format that you can invoke.

${if (tools.isEmpty()) "[]" else function_definitions}

When you receive the results of a tool call, you should respond with a helpful answer based on those results.
Do NOT call additional tools unless the user asks a new question that requires different information.
Format your answer in a clear, human-readable way.
"""
    }

    override fun toolSchemas(tools: List<BaseTool<*, *>>): String {
        // 創建一個JsonArray來存儲所有工具的定義
        val toolsArray = buildJsonArray {
            tools.forEach { tool ->
                val toolAnnotation = tool::class.findAnnotation<ToolAnnotation>() ?:
                throw IllegalStateException("Tool annotation not found")

                val parameterType = tool.getParameterType()

                if (parameterType != null) {
                    add(buildJsonObject {
                        // 工具名稱和描述
                        put("name", toolAnnotation.name)
                        put("description", toolAnnotation.description)

                        // 參數定義
                        putJsonObject("parameters") {
                            put("type", "dict")

                            // 獲取必需參數
                            val requiredProperties = mutableListOf<String>()

                            // 遍歷參數類型的所有屬性
                            parameterType.memberProperties.forEach { property ->
                                val propertyName = property.name
                                val isNullable = property.returnType.isMarkedNullable

                                // 如果不可為空，添加到必需參數列表
                                if (!isNullable) {
                                    requiredProperties.add(propertyName)
                                }
                            }

                            // 添加必需參數列表
                            put("required", buildJsonArray {
                                requiredProperties.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                            })

                            // 添加屬性定義
                            putJsonObject("properties") {
                                parameterType.memberProperties.forEach { property ->
                                    val propertyName = property.name

                                    // 獲取屬性類型
                                    val propertyType = getJsonType(property)

                                    // 添加屬性定義
                                    putJsonObject(propertyName) {
                                        put("type", propertyType)
                                        put("description", "The $propertyName parameter")
                                    }
                                }
                            }
                        }

                        // 如果有返回描述，添加它
                        if (toolAnnotation.returnDescription.isNotEmpty()) {
                            put("returnDescription", toolAnnotation.returnDescription)
                        }
                    })
                }
            }
        }

        // 使用格式化的JSON以增加可讀性
        return json.encodeToString(toolsArray)
    }

    override fun parseResponse(response: String): StructuredLLMResponse {
        try {
            // 檢查是否是Llama 3.2格式的工具調用 [func_name(param1=value1, param2=value2)]
            val trimmedResponse = response.trim()

            // 嘗試解析 Llama 3.2 格式
            if (trimmedResponse.startsWith("[") && trimmedResponse.endsWith("]")) {
                val toolCalls = parseToolCalls(trimmedResponse)

                if (toolCalls.isNotEmpty()) {
                    return StructuredLLMResponse(
                        toolCalls = toolCalls,
                        metadata = ResponseMetadata(
                            tokenUsage = estimateTokenUsage(trimmedResponse),
                            finishReason = FinishReason.TOOL_CALLS.value
                        )
                    )
                }
            }

            // 如果不是工具調用格式，視為直接文本回應
            return StructuredLLMResponse(
                content = response,
                metadata = ResponseMetadata(
                    tokenUsage = estimateTokenUsage(response),
                    finishReason = FinishReason.STOP.value
                )
            )
        } catch (e: Exception) {
            Log.e("Llama3_2_Translator", "Error parsing response: ${e.message}")
            // 出現任何異常，返回原始文本作為內容
            return StructuredLLMResponse(
                content = response,
                metadata = ResponseMetadata(
                    finishReason = FinishReason.ERROR.value
                )
            )
        }
    }

    override fun parseToolCalls(response: String): List<ToolCallInfo> {
        val result = mutableListOf<ToolCallInfo>()

        try {
            // 檢查是否是 Llama 3.2 格式
            val trimmedResponse = response.trim()
            if (!trimmedResponse.startsWith("[") || !trimmedResponse.endsWith("]")) {
                return emptyList()
            }

            // 移除最外層的方括號
            val content = trimmedResponse.substring(1, trimmedResponse.length - 1)

            // 分割多個工具調用（如果有的話）
            val toolCallsRaw = mutableListOf<String>()
            var currentCall = ""
            var parenthesesCount = 0

            // 解析邏輯，處理嵌套括號
            for (char in content) {
                when (char) {
                    '(' -> parenthesesCount++
                    ')' -> parenthesesCount--
                    ',' -> {
                        if (parenthesesCount == 0) {
                            toolCallsRaw.add(currentCall.trim())
                            currentCall = ""
                            continue
                        }
                    }
                }
                currentCall += char
            }

            // 添加最後一個調用
            if (currentCall.isNotEmpty()) {
                toolCallsRaw.add(currentCall.trim())
            }

            // 解析每個工具調用
            toolCallsRaw.forEach { toolCallRaw ->
                // 解析工具名稱和參數
                val functionNameEnd = toolCallRaw.indexOf("(")
                if (functionNameEnd > 0) {
                    val functionName = toolCallRaw.substring(0, functionNameEnd).trim()

                    // 提取參數部分（去掉最後的括號）
                    var paramsString = toolCallRaw.substring(functionNameEnd + 1)
                    if (paramsString.endsWith(")")) {
                        paramsString = paramsString.substring(0, paramsString.length - 1)
                    }

                    // 解析參數
                    val params = mutableMapOf<String, String>()
                    var currentParam = ""
                    var currentKey = ""
                    var inValue = false
                    var quoteCount = 0

                    // 參數解析邏輯
                    for (i in paramsString.indices) {
                        val char = paramsString[i]

                        when {
                            char == '=' && !inValue -> {
                                currentKey = currentParam.trim()
                                currentParam = ""
                                inValue = true
                            }
                            char == ',' && quoteCount % 2 == 0 -> {
                                if (currentKey.isNotEmpty()) {
                                    params[currentKey] = currentParam.trim()
                                }
                                currentKey = ""
                                currentParam = ""
                                inValue = false
                            }
                            char == '"' -> quoteCount++
                            else -> currentParam += char
                        }
                    }

                    // 添加最後一個參數
                    if (currentKey.isNotEmpty()) {
                        params[currentKey] = currentParam.trim()
                    }

                    // 創建 JSON 參數字符串
                    val jsonParams = buildString {
                        append("{")
                        params.entries.forEachIndexed { index, (key, value) ->
                            if (index > 0) append(", ")

                            // 嘗試將值解析為數字或布爾值
                            val processedValue = when {
                                value.equals("true", ignoreCase = true) -> "true"
                                value.equals("false", ignoreCase = true) -> "false"
                                value.toIntOrNull() != null -> value
                                value.toDoubleOrNull() != null -> value
                                else -> "\"$value\"" // 字符串值加引號
                            }

                            append("\"$key\": $processedValue")
                        }
                        append("}")
                    }

                    // 創建工具調用信息
                    result.add(
                        ToolCallInfo(
                            id = generateCallId(),
                            function = FunctionCall(
                                name = functionName,
                                arguments = jsonParams
                            )
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("Llama3_2_Translator", "Error parsing tool calls: ${e.message}")
        }

        return result
    }

    /**
     * 根據回應文本估算token使用情況
     */
    private fun estimateTokenUsage(response: String): TokenUsage {
        // 一個粗略的估算：大約每4個字符等於1個token
        val tokens = (response.length / 4) + 1
        return TokenUsage(
            completionTokens = tokens,
            totalTokens = tokens
        )
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