package com.smith.lai.smithtoolcalls.langgraph.model.adapter

import com.smith.lai.smithtoolcalls.tools.BaseTool
import com.smith.lai.smithtoolcalls.tools.StructuredLLMResponse
import com.smith.lai.smithtoolcalls.tools.ToolCallInfo
import kotlin.reflect.KProperty1

abstract class BaseLLMToolAdapter {

    abstract fun toolSchemas(tools: List<BaseTool<*, *>>) : String

    abstract fun createToolPrompt(tools: List<BaseTool<*, *>>): String

    /**
     * 解析LLM回應為結構化格式
     * 由具體的 Translator 實現類來處理不同模型的輸出格式
     */
    abstract fun parseLLMResponse(response: String): StructuredLLMResponse

    /**
     * 解析工具調用格式
     * 從LLM回應中提取工具調用信息
     */
    abstract fun parseToolCalls(response: String): List<ToolCallInfo>

    protected fun getJsonType(property: KProperty1<*, *>): String {
        val classifier = property.returnType.classifier
        return when (classifier){
            Int::class, Long::class -> "integer"
            Float::class, Double::class -> "number"
            Boolean::class -> "boolean"
            String::class -> "string"
            List::class -> "array"
            Map::class -> "object"
            else -> when {
                property.returnType.toString().contains("List") -> "array"
                property.returnType.toString().contains("Map") -> "object"
                else -> "object"
            }
        }
    }
}