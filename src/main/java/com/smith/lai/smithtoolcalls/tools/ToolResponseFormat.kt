package com.smith.lai.smithtoolcalls.tools

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 回應元數據
 */
data class ResponseMetadata(
    val tokenUsage: TokenUsage? = null,
    val finishReason: String? = null,
    val logprobs: Any? = null
)

enum class ToolResponseType(val value: String) {
    FUNCTION("function"),
    DIRECT_RESPONSE("direct_response"),
    ERROR("error");

    override fun toString(): String = value

    companion object {
        fun fromString(value: String): ToolResponseType {
            return values().find { it.value == value } ?: FUNCTION
        }
    }
}
enum class FinishReason(val value: String) {
    STOP("stop"),
    TOOL_CALLS("tool_calls"),
    ERROR("error");

    override fun toString(): String = value

    companion object {
        fun fromString(value: String): FinishReason {
            return values().find { it.value == value } ?: STOP
        }
    }
}

@Serializable
data class ToolResponse<T>(
    val id: String,
    val type: ToolResponseType = ToolResponseType.FUNCTION,
    val output: T
)


/**
 * 結構化的LLM回應，使用現有的ToolCallArguments
 */
data class StructuredLLMResponse(
    val content: String = "",
    val toolCalls: List<ToolCallInfo> = emptyList(),
    val metadata: ResponseMetadata = ResponseMetadata(),
    val id: String = UUID.randomUUID().toString()
) {
    /**
     * 檢查這個回應是否包含工具調用
     */
    fun hasToolCalls(): Boolean = toolCalls.isNotEmpty()

    /**
     * 檢查這個回應是否是直接文本回應
     */
    fun isDirectResponse(): Boolean = content.isNotEmpty() && !hasToolCalls()
}

/**
 * 處理結果包裝類
 */
data class ProcessingResult(
    val structuredResponse: StructuredLLMResponse,
    val toolResponses: List<ToolResponse<*>>,
    val requiresFollowUp: Boolean
) {
    /**
     * 獲取格式化的工具回應JSON，用於發送回LLM
     */
    fun getToolResponseJson(): String {
        return buildString {
            append("[")
            toolResponses.forEachIndexed { index, response ->
                if (index > 0) append(",")
                append("""
                    {
                        "id": "${response.id}",
                        "type": "${response.type}",
                        "output": ${formatOutput(response.output)}
                    }
                """.trimIndent())
            }
            append("]")
        }
    }

    private fun formatOutput(output: Any?): String {
        return when (output) {
            null -> "null"
            is Number -> output.toString()
            is Boolean -> output.toString()
            is String -> "\"${output.replace("\"", "\\\"")}\""
            else -> "\"${output.toString().replace("\"", "\\\"")}\""
        }
    }
}

/**
 * Token使用統計
 */
data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)