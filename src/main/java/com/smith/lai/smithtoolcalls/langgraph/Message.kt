package com.smith.lai.smithtoolcalls.langgraph

/**
 * Message represents a chat message in the conversation
 */
data class Message(
    val role: MessageRole,
    val content: String,
    val id: String = java.util.UUID.randomUUID().toString()
)

/**
 * Message role enum
 */
enum class MessageRole {
    SYSTEM, USER, ASSISTANT, TOOL
}