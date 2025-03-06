package com.smith.lai.smithtoolcalls.langgraph.node

import android.util.Log
import com.smith.lai.smithtoolcalls.langgraph.MessageRole


/**
 * End node that properly finalizes the graph execution
 */
class EndNode(private val completionMessage: String = "Graph execution completed") : Node<StateGraph> {
    override suspend fun invoke(state: StateGraph): StateGraph {
        val duration = state.executionDuration()

        Log.d("EndNode", "Finalizing graph execution after ${state.stepCount} steps in ${duration}ms")

        if (state.error != null) {
            Log.e("EndNode", "Execution completed with error: ${state.error}")
        } else {
            Log.d("EndNode", "Execution completed successfully: $completionMessage")
        }

        // If we don't have a final response yet, use the last assistant message
        if (state.finalResponse.isEmpty()) {
            val lastAssistantMessage = state.messages
                .lastOrNull { it.role == MessageRole.ASSISTANT }
                ?.content ?: ""

            Log.d("EndNode", "Setting final response from last assistant message (${lastAssistantMessage.length} chars)")

            return state.copy(
                finalResponse = lastAssistantMessage,
                completed = true
            )
        }

        // Make sure the state is marked as completed
        return state.copy(completed = true)
    }
}