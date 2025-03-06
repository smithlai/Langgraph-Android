package com.smith.lai.smithtoolcalls.custom_data

import com.smith.lai.smithtoolcalls.langgraph.GraphState
import com.smith.lai.smithtoolcalls.tools.ProcessingResult
import com.smith.lai.smithtoolcalls.tools.ToolResponse
import java.util.UUID


// 訊息數據類
data class Message(
    val role: MessageRole,
    val content: String,
    val id: String = UUID.randomUUID().toString()
)

// 訊息角色
enum class MessageRole {
    SYSTEM, USER, ASSISTANT, TOOL
}

// 定義會話狀態
data class ConversationState(
    override var completed: Boolean = false,
    override var error: String? = null,
    override var stepCount: Int = 0,
    val startTime: Long = System.currentTimeMillis(),

    // 業務數據
    var query: String = "",
    val messages: MutableList<Message> = mutableListOf(),
    val toolResponses: MutableList<ToolResponse<*>> = mutableListOf(),
    var processingResult: ProcessingResult? = null,
    var finalResponse: String = "",
    var hasToolCalls: Boolean = false
) : GraphState {

    companion object {
        // 針對ConversationState的工具調用條件
        val HasToolCalls: (ConversationState) -> Boolean = { state ->
            state.hasToolCalls
        }
    }

    override fun withCompleted(isCompleted: Boolean): ConversationState {
        completed = isCompleted
        return this
    }

    override fun withError(errorMessage: String): ConversationState {
        error = errorMessage
        return this
    }

    override fun incrementStep(): ConversationState {
        stepCount++
        return this
    }

    fun executionDuration(): Long = System.currentTimeMillis() - startTime

    fun addMessage(role: MessageRole, content: String): ConversationState {
        messages.add(Message(role, content))
        return this
    }

    fun getLastAssistantMessage(): String? {
        return messages.lastOrNull { it.role == MessageRole.ASSISTANT }?.content
    }
}

