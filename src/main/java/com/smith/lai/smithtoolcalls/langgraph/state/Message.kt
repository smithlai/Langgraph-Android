package com.smith.lai.smithtoolcalls.langgraph.state

import com.smith.lai.smithtoolcalls.tools.ToolCallInfo
import com.smith.lai.smithtoolcalls.tools.ToolResponse
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
    val timestamp: Long = System.currentTimeMillis(),
    // 新增屬性以支援工具調用和工具回應
    val toolCalls: List<ToolCallInfo>? = null,
    val toolOutput: Any? = null,
    val metadata: Map<String, Any>? = null
) {
    constructor(role: MessageRole, content: String) : this(
        UUID.randomUUID().toString(), role, content
    )

    /**
     * 判斷此消息是否包含工具調用
     */
    fun hasToolCalls(): Boolean = toolCalls != null && toolCalls.isNotEmpty()

    companion object {
        /**
         * 從工具響應創建工具消息
         */
        fun fromToolResponse(toolResponse: ToolResponse<*>): Message {
            return Message(
                role = MessageRole.TOOL,
                content = if (toolResponse.followUpMetadata.customFollowUpPrompt.isNotEmpty()) {
                    toolResponse.followUpMetadata.customFollowUpPrompt
                } else {
                    "The tool returned: \"${toolResponse.output}\". Based on this information, continue answering the request."
                },
                toolOutput = toolResponse.output,
                metadata = mapOf(
                    "toolId" to toolResponse.id,
                    "toolType" to toolResponse.type.toString(),
                    "requiresFollowUp" to toolResponse.followUpMetadata.requiresFollowUp.toString(),
                    "shouldTerminateFlow" to toolResponse.followUpMetadata.shouldTerminateFlow.toString()
                )
            )
        }
    }
}