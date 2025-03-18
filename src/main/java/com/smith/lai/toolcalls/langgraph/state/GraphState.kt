package com.smith.lai.toolcalls.langgraph.state

import com.smith.lai.toolcalls.langgraph.response.ToolResponse

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
    fun resetStatus(): GraphState {
        stepCount = 0
        completed = false
        error = null
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
//        if (message.role == MessageRole.ERROR) { // Skip error
//            return this
//        }
        messages.add(message)

        if (message.role == MessageRole.USER || message.role == MessageRole.TOOL) {
            //如果是USER or TOOL 我們將加入model中
            message.queueing = true
        }

        return this
    }


    /**
     * 獲取最後一條助手消息內容
     */
    fun getLastAssistantMessage(): Message? {
        return getLastMessageByRole(MessageRole.ASSISTANT)
    }
    fun getLastToolCallsMessage(): Message? {
        return getLastMessageByRole(MessageRole.ASSISTANT).takeIf { it?.hasToolCalls() == true}
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
     * 獲取所有工具響應消息
     * todo: for debug
     */
    fun getToolMessages(): List<Message> {
        return getMessagesByRole(MessageRole.TOOL)
    }

    /**
     * 獲取所有工具響應對象
     * todo: for debug
     */
    fun getToolResponses(): List<ToolResponse<*>> {
        return getToolMessages()
            .mapNotNull { it.toolResponse }
    }

    /**
     * 計算執行持續時間（毫秒）
     */
    fun executionDuration(): Long = System.currentTimeMillis() - startTime

    /**
     * 獲取消息的結構化表示（用於 API 調用或序列化）
     */
//    fun getFormattedMessages(): List<Map<String, Any>> {
//        return messages.map { message ->
//            buildMap {
//                put("role", message.role.toString().lowercase())
//                put("content", message.content)
//
//                // 處理工具調用
//                message.toolCalls?.let { put("tool_calls", it as Any) }
//
//                // 處理工具輸出
//                message.toolOutput?.let { put("tool_output", it) }
//
//                // 處理元數據
//                message.metadata?.forEach { (key, value) ->
//                    put(key, value)
//                }
//            }
//        }
//    }
}