package com.smith.lai.smithtoolcalls.langgraph.state

import java.util.UUID

/**
 * 通用消息數據類
 */
data class Message(
    val role: MessageRole,
    val content: String,
    val id: String = UUID.randomUUID().toString()
)

/**
 * 通用消息角色
 */
enum class MessageRole {
    SYSTEM, USER, ASSISTANT, TOOL
}