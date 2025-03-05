package com.smith.lai.smithtoolcalls.langgraph.node

import android.util.Log
import com.smith.lai.smithtoolcalls.ToolRegistry
import com.smith.lai.smithtoolcalls.langgraph.Message
import com.smith.lai.smithtoolcalls.langgraph.MessageRole
import io.shubham0204.smollm.SmolLM
/**
 * LLM node that handles generating responses from the LLM
 */
@GraphNode("llm")
class LLMNode(
    private val model: SmolLM,
    private val toolRegistry: ToolRegistry
) : Node<StateGraph> {
    override suspend fun invoke(state: StateGraph): StateGraph {
        try {
            // Check for existing error
            if (state.error != null) {
                Log.e("LLMNode", "Previous error detected: ${state.error}")
                return state.copy(completed = true)
            }

            Log.d("LLMNode", "Processing state with ${state.messages.size} messages")

            // If we have a new user query, we need to add it
            if (state.query.isNotEmpty() &&
                (state.messages.isEmpty() ||
                        state.messages.last().role != MessageRole.USER ||
                        state.messages.last().content != state.query)) {
                Log.d("LLMNode", "Adding user query to messages: ${state.query}")
                state.messages.add(Message(MessageRole.USER, state.query))
            }

            // Clear any previous messages in the model and add them fresh
            //model.reset()

            // Add system prompt
            val systemPrompt = toolRegistry.createSystemPrompt()
            Log.d("LLMNode", "Adding system prompt (${systemPrompt.length} chars)")
            model.addSystemPrompt(systemPrompt)

            // Prevent context overflow - only add the most recent messages if we're approaching limits
            val maxMessagesToAdd = if (state.messages.size > 30) 15 else state.messages.size
            val messagesToAdd = state.messages.takeLast(maxMessagesToAdd)
            Log.d("LLMNode", "Using ${messagesToAdd.size} of ${state.messages.size} total messages to prevent context overflow")

            // Add conversation messages except system
            for (message in messagesToAdd) {
                when (message.role) {
                    MessageRole.USER -> {
                        Log.d("LLMNode", "Adding USER message: ${message.content.take(50)}...")
                        model.addUserMessage(message.content)
                    }
                    MessageRole.ASSISTANT -> {
                        Log.d("LLMNode", "Adding ASSISTANT message: ${message.content.take(50)}...")
                        model.addAssistantMessage(message.content)
                    }
                    MessageRole.TOOL -> {
                        Log.d("LLMNode", "Adding TOOL message: ${message.content.take(50)}...")
                        model.addUserMessage(message.content)
                    }
                    else -> {} // System prompt handled above
                }
            }

            // Generate response
            Log.d("LLMNode", "Generating LLM response...")
            val response = StringBuilder()
            model.getResponse().collect {
                response.append(it)
            }

            val assistantResponse = response.toString()
            Log.d("LLMNode", "LLM response generated (${assistantResponse.length} chars): ${assistantResponse.take(100)}...")
            state.messages.add(Message(MessageRole.ASSISTANT, assistantResponse))

            // Process tool calls
            Log.d("LLMNode", "Processing LLM response for tool calls")
            val processingResult = toolRegistry.processLLMResponse(assistantResponse)
            Log.d("LLMNode", "Found ${processingResult.toolResponses.size} tool calls")

            return state.copy(
                processingResult = processingResult,
                completed = processingResult.toolResponses.isEmpty() // Complete if no tool calls
            )
        } catch (e: Exception) {
            Log.e("LLMNode", "Error processing state", e)
            return state.copy(error = "LLM node error: ${e.message}", completed = true)
        }
    }
}