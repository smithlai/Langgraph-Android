package com.smith.lai.smithtoolcalls.langgraph.node

import android.util.Log
import com.smith.lai.smithtoolcalls.langgraph.model.LLMWithTools
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState
import com.smith.lai.smithtoolcalls.langgraph.state.Message
import com.smith.lai.smithtoolcalls.langgraph.state.MessageRole


class LLMNode<S : GraphState>(
    private val model: LLMWithTools,
    private val maxTrackedMessages: Int = 5
) : Node<S>() {
    private val processedMessageIds = LinkedHashSet<String>(maxTrackedMessages)
    private val debugTag = "LLMNode"

    override suspend fun invoke(state: S): S {
        try {
            // 检查已有错误
            if (state.error != null) {
                Log.e(debugTag, "LLM node: previous error detected: ${state.error}")
                return state.withCompleted(true) as S
            }

            // 检查消息是否为空
            if (state.messages.isEmpty()) {
                Log.e(debugTag, "LLM node: no messages to process")
                return state.withError("No messages to process").withCompleted(true) as S
            }

            // 防止上下文溢出-仅处理最近消息
            val messagesToProcess = state.messages.takeLast(maxTrackedMessages)

            // 检查是否需要添加工具提示（使用新的方法）
            if (!model.isToolPromptAdded()) {
                model.addToolPrompt()
            }

            // 添加新的消息（与原代码逻辑相同）
            var newMessagesAdded = processMessages(messagesToProcess)

            // 生成响应逻辑（与原代码逻辑相同）
            if (newMessagesAdded > 0) {
                generateAndProcessResponse(state)
            } else {
                Log.d(debugTag, "LLM node: no new messages, skipping response generation")
                state.setHasToolCalls(false)
            }

            return state.withCompleted(!state.hasToolCalls) as S
        } catch (e: Exception) {
            Log.e(debugTag, "LLM node: error processing state", e)
            return state.withError("LLM node error: ${e.message}").withCompleted(true) as S
        }
    }

    private fun processMessages(messagesToProcess: List<Message>): Int {
        var newMessagesAdded = 0
        for (message in messagesToProcess) {
            // 检查消息是否已处理过
            if (processedMessageIds.contains(message.id)) {
                continue
            }

            // 添加新消息到模型
            when (message.role) {
                MessageRole.SYSTEM -> {
                    model.addUserMessage(message.content)
                    Log.v(debugTag, "Added SYSTEM: ${message.content}")
                }
                MessageRole.USER -> {
                    model.addUserMessage(message.content)
                    Log.v(debugTag, "Added USER: ${message.content}")
                }
                MessageRole.ASSISTANT -> {
                    model.addAssistantMessage(message.content)
                    Log.v(debugTag, "Added ASSISTANT: ${message.content}")
                }
                MessageRole.TOOL -> {
                    model.addUserMessage(message.content)
                    Log.v(debugTag, "Added TOOL as USER: ${message.content}")
                }
                else -> {} // 忽略系统消息
            }

            // 添加消息 ID 到处理集合
            processedMessageIds.add(message.id)

            // 如果超过最大跟踪数，移除最舊的
            if (processedMessageIds.size > maxTrackedMessages) {
                processedMessageIds.iterator().next()?.let {
                    processedMessageIds.remove(it)
                }
            }

            newMessagesAdded++
        }
        Log.v(debugTag, "LLM node: Totally $newMessagesAdded new messages added.")
        return newMessagesAdded
    }

    private suspend fun generateAndProcessResponse(state: S) {
        // 生成回应
        Log.d(debugTag, "LLM node: generating response...")
        val response = StringBuilder()
        model.getResponse().collect {
            response.append(it)
        }

        val assistantResponse = response.toString()
        Log.d(debugTag, "LLM node: response generated (${assistantResponse.length}): ${assistantResponse}")

        // 添加助手消息, 并标记为已处理
        state.addMessage(MessageRole.ASSISTANT, assistantResponse)
        processedMessageIds.add(state.messages.last().id)

        // 使用 ToolRegistry 的方法检测工具调用并存储结构化回应
        val structuredResponse = model.convertToStructured(assistantResponse)
        state.setStructuredLLMResponse(structuredResponse)
        state.setHasToolCalls(structuredResponse.hasToolCalls())

        Log.d(debugTag, "LLM node: detected tool calls = ${state.hasToolCalls}")
    }
}