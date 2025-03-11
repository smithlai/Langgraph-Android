package com.smith.lai.smithtoolcalls.langgraph.state

import com.smith.lai.smithtoolcalls.tools.StructuredLLMResponse
import com.smith.lai.smithtoolcalls.tools.ToolResponse

/**
 * 抽象GraphState實現 - 提供基本圖狀態功能的默認實現
 * 使用 Message 作為核心狀態元素
 */
abstract class GraphState {
    // 基本狀態標誌
    var completed: Boolean = false
    var error: String? = null
    var stepCount: Int = 0

    // 核心狀態：消息歷史
    val messages: MutableList<Message> = mutableListOf()

    // 工具相關狀態
    val toolResponses: MutableList<ToolResponse<*>> = mutableListOf()
    var hasToolCalls: Boolean = false
    var structuredLLMResponse: StructuredLLMResponse? = null

    // 性能監控
    private val startTime: Long = System.currentTimeMillis()

    /**
     * 設置完成狀態
     */
    fun withCompleted(isCompleted: Boolean): GraphState {
        completed = isCompleted
        return this
    }

    /**
     * 設置錯誤訊息
     */
    fun withError(errorMessage: String): GraphState {
        error = errorMessage
        return this
    }

    /**
     * 增加步驟計數
     */
    fun incrementStep(): GraphState {
        stepCount++
        return this
    }

    /**
     * 添加新消息（以角色和內容）
     */
    fun addMessage(role: MessageRole, content: String): GraphState {
        val message = Message(role, content)
        return addMessage(message)
    }

    /**
     * 添加消息對象並自動更新相關狀態
     */
    fun addMessage(message: Message): GraphState {
        messages.add(message)

        // 如果是助手消息，檢查是否包含工具調用
        if (message.role == MessageRole.ASSISTANT && message.hasToolCalls()) {
            hasToolCalls = true
        }

        return this
    }

    /**
     * 添加工具響應並創建對應的 TOOL 角色消息
     */
    fun addToolResponse(response: ToolResponse<*>): GraphState {
        toolResponses.add(response)

        // 創建 TOOL 消息
        val toolMessage = Message.fromToolResponse(response)
        messages.add(toolMessage)

        return this
    }

    /**
     * 設置工具調用標誌
     */
    fun setHasToolCalls(value: Boolean): GraphState {
        hasToolCalls = value
        return this
    }

    /**
     * 設置結構化 LLM 響應
     */
    fun setStructuredLLMResponse(response: StructuredLLMResponse?): GraphState {
        structuredLLMResponse = response
        return this
    }

    /**
     * 獲取最後一條助手消息內容
     */
    fun getLastAssistantMessage(): String? {
        return getLastMessageByRole(MessageRole.ASSISTANT)?.content
    }

    /**
     * 獲取最後一條用戶消息內容
     */
    fun getLastUserMessage(): String? {
        return getLastMessageByRole(MessageRole.USER)?.content
    }

    /**
     * 獲取最後一條指定角色的消息
     */
    fun getLastMessageByRole(role: MessageRole): Message? {
        return messages.lastOrNull { it.role == role }
    }

    /**
     * 獲取特定角色的所有消息
     */
    fun getMessagesByRole(role: MessageRole): List<Message> {
        return messages.filter { it.role == role }
    }

    /**
     * 計算執行持續時間（毫秒）
     */
    fun executionDuration(): Long = System.currentTimeMillis() - startTime

    /**
     * 獲取消息的結構化表示（用於 API 調用或序列化）
     */
    fun getFormattedMessages(): List<Map<String, Any>> {
        return messages.map { message ->
            buildMap {
                put("role", message.role.toString().lowercase())
                put("content", message.content)

                // 處理工具調用
                message.toolCalls?.let { put("tool_calls", it as Any) }

                // 處理工具輸出
                message.toolOutput?.let { put("tool_output", it) }

                // 處理元數據
                message.metadata?.forEach { (key, value) ->
                    put(key, value)
                }
            }
        }
    }
}