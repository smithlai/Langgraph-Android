package com.smith.lai.smithtoolcalls.langgraph.state

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
 * 消息類 - 表示圖流程中的單個消息
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    constructor(role: MessageRole, content: String) : this(
        UUID.randomUUID().toString(), role, content
    )
}