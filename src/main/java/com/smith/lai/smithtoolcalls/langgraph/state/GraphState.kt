package com.smith.lai.smithtoolcalls.langgraph.state

import com.smith.lai.smithtoolcalls.tools.ToolResponse

/**
 * 抽象GraphState實現 - 提供基本圖狀態功能的默認實現
 */
abstract class GraphState {
    var completed: Boolean = false
    var error: String? = null
    var stepCount: Int = 0

    val messages: MutableList<Message> = mutableListOf()
    val toolResponses: MutableList<ToolResponse<*>> = mutableListOf()
    var hasToolCalls: Boolean = false
    var rawLLMResponse: String? = null

    private val startTime: Long = System.currentTimeMillis()

    fun withCompleted(isCompleted: Boolean): GraphState {
        completed = isCompleted
        return this
    }

    fun withError(errorMessage: String): GraphState {
        error = errorMessage
        return this
    }

    fun incrementStep(): GraphState {
        stepCount++
        return this
    }

    fun addMessage(role: MessageRole, content: String): GraphState {
        val message = Message(role, content)
        messages.add(message)
        return this
    }

    fun addMessage(message: Message): GraphState {
        messages.add(message)
        return this
    }

    fun addUserInput(content: String): GraphState {
        addMessage(MessageRole.USER, content)
        return this
    }

    fun setHasToolCalls(value: Boolean): GraphState {
        hasToolCalls = value
        return this
    }

    fun setRawLLMResponse(response: String?): GraphState {
        rawLLMResponse = response
        return this
    }

    fun getLastAssistantMessage(): String? {
        return messages.lastOrNull { it.role == MessageRole.ASSISTANT }?.content
    }

    fun getLastUserMessage(): String? {
        return messages.lastOrNull { it.role == MessageRole.USER }?.content
    }

    /**
     * 計算執行持續時間
     */
    fun executionDuration(): Long = System.currentTimeMillis() - startTime
}