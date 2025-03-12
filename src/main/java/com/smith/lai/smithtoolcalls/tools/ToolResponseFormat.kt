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

/**
 * 工具後續處理元數據
 */
@Serializable
data class ToolFollowUpMetadata(
    // 是否需要後續處理
    val requiresFollowUp: Boolean = true,
    // 是否應該終止流程
    val shouldTerminateFlow: Boolean = false,
    // 自定義的後續提示詞
    val customFollowUpPrompt: String = ""
)

@Serializable
data class ToolResponse<T>(
    val id: String,
    val type: ToolResponseType = ToolResponseType.FUNCTION,
    val output: T,
    // 新增：直接包含後續處理元數據
    val followUpMetadata: ToolFollowUpMetadata = ToolFollowUpMetadata()
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
    fun hasToolCalls(): Boolean = toolCalls?.any{ it.executed == false} == true

}

/**
 * 處理結果包裝類
 */
data class ProcessingResult(
    val structuredResponse: StructuredLLMResponse,
    val toolResponses: List<ToolResponse<*>>
) {
    /**
     * 檢查是否需要後續處理
     */
    val requiresFollowUp: Boolean
        get() = toolResponses.any { it.followUpMetadata.requiresFollowUp } && !shouldTerminateFlow()

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

    /**
     * 構建基於工具元數據的後續提示詞
     */
    fun buildFollowUpPrompt(): String {
        val followUpBuilder = StringBuilder()

        toolResponses.forEachIndexed { index, response ->
            val metadata = response.followUpMetadata

            if (metadata.customFollowUpPrompt.isNotEmpty()) {
                // 使用工具提供的自定義提示詞
                followUpBuilder.append(metadata.customFollowUpPrompt)
            } else {
                // 使用默認提示詞格式
                followUpBuilder.append("The tool returned: \"${response.output}\". ")
            }

            if (index < toolResponses.size - 1) {
                followUpBuilder.append("\n\n")
            }
        }

        // 如果沒有非空的自定義提示詞，添加標準指令
        if (!toolResponses.any { it.followUpMetadata.customFollowUpPrompt.isNotEmpty() }) {
            followUpBuilder.append("\nBased on this information, continue answering the request.")
        }

        return followUpBuilder.toString()
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

    /**
     * 檢查是否有任何工具要求終止流程
     */
    fun shouldTerminateFlow(): Boolean {
        return toolResponses.any { it.followUpMetadata.shouldTerminateFlow }
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

