package com.smith.lai.smithtoolcalls.langgraph.state

import com.smith.lai.smithtoolcalls.langgraph.response.StructuredLLMResponse
import com.smith.lai.smithtoolcalls.langgraph.response.ToolResponse
import java.util.UUID

/**
 * 消息角色枚舉
 */
enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

/**
 * 消息類 - 表示圖流程中的單個消息，增強以支持工具交互
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val structuredLLMResponse: StructuredLLMResponse? = null, // if any tool_calls (this usually comes from llm response)
    val timestamp: Long = System.currentTimeMillis(),
    val toolResponse: ToolResponse<*>? = null,  // if any tool_node, this comes from tool node (or processLLMResponse())
    var queueing: Boolean = false, // if need to send to model
    val metadata: Map<String, Any>? = null
) {
    constructor(role: MessageRole, content: String) : this(
        UUID.randomUUID().toString(), role, content
    )

    /**
     * 判斷此消息是否包含工具調用
     */
    fun hasToolCalls(): Boolean = structuredLLMResponse?.hasToolCalls() == true

    companion object {
        /**
         * 從工具響應創建工具消息
         */
        fun fromToolResponse(toolResponse: ToolResponse<*>): Message {
            // Skip creating a message if the tool doesn't require follow-up
            if (!toolResponse.followUpMetadata.requiresFollowUp) {
                throw IllegalStateException("Cannot create message from tool that doesn't require follow-up")
            }

            // Create appropriate content based on tool response
            val content = when {
                toolResponse.followUpMetadata.customFollowUpPrompt.isNotEmpty() -> {
                    toolResponse.followUpMetadata.customFollowUpPrompt
                }
                // For Unit responses or empty responses, provide a minimal message
                toolResponse.output == Unit || toolResponse.output == null || toolResponse.output.toString().isEmpty() -> {
                    "The tool action was executed successfully."
                }
                else -> {
                    "The tool returned: \"${toolResponse.output}\". Based on this information, continue answering the request."
                }
            }

            return Message(
                role = MessageRole.TOOL,
                content = content,
                toolResponse = toolResponse
            )
        }
    }
}