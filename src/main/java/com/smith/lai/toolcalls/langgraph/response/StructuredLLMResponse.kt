package com.smith.lai.toolcalls.langgraph.response

import com.smith.lai.toolcalls.langgraph.tools.ToolCallInfo
import java.util.UUID

/**
 * 結構化的LLM回應，使用現有的ToolCallArguments
 */
data class StructuredLLMResponse(
    val content: String = "",
    val toolCalls: List<ToolCallInfo> = emptyList(),
    val metadata: LLMResponseMetadata = LLMResponseMetadata(),
    val id: String = UUID.randomUUID().toString()
) {
    /**
     * 檢查這個回應是否包含工具調用
     */
    fun hasToolCalls(): Boolean = toolCalls?.any{ it.executed == false} == true

}

/**
 * 回應元數據
 */
data class LLMResponseMetadata(
    val tokenUsage: TokenUsage? = null,
    val finishReason: String? = null,
    val logprobs: Any? = null
)