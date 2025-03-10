package com.smith.lai.smithtoolcalls.custom_data

import android.util.Log
import com.smith.lai.smithtoolcalls.langgraph.model.LLMWithTools
import com.smith.lai.smithtoolcalls.langgraph.node.Node
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState

import com.smith.lai.smithtoolcalls.langgraph.state.MessageRole

/**
 * 通用 LLM 節點 - 可用於任何實現 GraphState 接口的狀態
 */
object LLMNodes {
    private const val DEBUG_TAG = "LLMNodes"

    /**
     * 創建通用LLM節點，支持任何GraphState實現
     */
    fun <S : GraphState> createLLMNode(model: LLMWithTools): Node<S> {
        return object : Node<S>() {
            // 最大跟踪消息數
            private val maxTrackedMessages = 5

            // 使用 LinkedHashSet 實現有序且唯一的 ID 集合
            private val processedMessageIds = LinkedHashSet<String>(maxTrackedMessages)

            override suspend fun invoke(state: S): S {
                try {
                    // 檢查已有錯誤
                    if (state.error != null) {
                        Log.e(DEBUG_TAG, "LLM node: previous error detected: ${state.error}")
                        return state.withCompleted(true) as S
                    }

                    // 檢查消息是否為空
                    if (state.messages.isEmpty()) {
                        Log.e(DEBUG_TAG, "LLM node: no messages to process")
                        return state.withError("No messages to process").withCompleted(true) as S
                    }

                    // 防止上下文溢出-僅處理最近消息
                    val messagesToProcess = state.messages.takeLast(maxTrackedMessages)

                    // 檢查是否需要添加系統提示
                    if (processedMessageIds.isEmpty()) {
                        val systemPrompt = model.createToolPrompt()
                        Log.d(DEBUG_TAG, "LLM node: Adding SystemPrompt(${systemPrompt.length})")
                        model.addSystemMessage(systemPrompt)
                    }

                    // 添加新的消息
                    var newMessagesAdded = 0
                    for (message in messagesToProcess) {
                        // 檢查消息是否已處理過
                        if (processedMessageIds.contains(message.id)) {
                            continue
                        }

                        // 添加新消息到模型
                        when (message.role) {
                            MessageRole.USER -> {
                                model.addUserMessage(message.content)
                                Log.v(DEBUG_TAG, "Added USER: ${message.content}")
                            }
                            MessageRole.ASSISTANT -> {
                                model.addAssistantMessage(message.content)
                                Log.v(DEBUG_TAG, "Added ASSISTANT: ${message.content}")
                            }
                            MessageRole.TOOL -> {
                                model.addUserMessage(message.content)
                                Log.v(DEBUG_TAG, "Added TOOL as USER: ${message.content}")
                            }
                            else -> {} // 忽略系統消息
                        }

                        // 添加消息 ID 到處理集合
                        processedMessageIds.add(message.id)

                        // 如果超過最大跟踪數，移除最舊的
                        if (processedMessageIds.size > maxTrackedMessages) {
                            processedMessageIds.iterator().next()?.let {
                                processedMessageIds.remove(it)
                            }
                        }

                        newMessagesAdded++
                    }

                    Log.d(DEBUG_TAG, "LLM node: Added $newMessagesAdded new messages to context")

                    // 只有當有新消息時才生成回應
                    if (newMessagesAdded > 0) {
                        // 生成回應
                        Log.d(DEBUG_TAG, "LLM node: generating response...")
                        val response = StringBuilder()
                        model.getResponse().collect {
                            response.append(it)
                        }

                        val assistantResponse = response.toString()
                        Log.d(DEBUG_TAG, "LLM node: response generated (${assistantResponse.length}): ${assistantResponse}")

                        // 添加助手消息, 並標記為已處理
                        state.addMessage(MessageRole.ASSISTANT, assistantResponse)
                        processedMessageIds.add(state.messages.last().id)

                        // 使用 ToolRegistry 的方法檢測工具調用並儲存結構化回應
                        val structuredResponse = model.convertToStructured(assistantResponse)
                        state.setStructuredLLMResponse(structuredResponse)
                        state.setHasToolCalls(structuredResponse.hasToolCalls())

                        Log.d(DEBUG_TAG, "LLM node: detected tool calls = ${state.hasToolCalls}")

                    } else {
                        Log.d(DEBUG_TAG, "LLM node: no new messages, skipping response generation")
                        state.setHasToolCalls(false)
                    }

                    return state.withCompleted(!state.hasToolCalls) as S
                } catch (e: Exception) {
                    Log.e(DEBUG_TAG, "LLM node: error processing state", e)
                    return state.withError("LLM node error: ${e.message}").withCompleted(true) as S
                }
            }
        }
    }
}