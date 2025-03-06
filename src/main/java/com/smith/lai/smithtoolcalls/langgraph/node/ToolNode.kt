package com.smith.lai.smithtoolcalls.langgraph.node

import android.util.Log
import com.smith.lai.smithtoolcalls.ToolRegistry
import com.smith.lai.smithtoolcalls.langgraph.Message
import com.smith.lai.smithtoolcalls.langgraph.MessageRole

/**
 * Tool execution node that handles executing tools
 */
class ToolNode(private val toolRegistry: ToolRegistry) : Node<StateGraph> {
    override suspend fun invoke(state: StateGraph): StateGraph {
        Log.d("ToolNode", "Processing tool node with state: completionStatus=${state.completed}")

        val processingResult = state.processingResult
        if (processingResult == null) {
            Log.e("ToolNode", "No processing result available")
            return state.copy(
                error = "No processing result available",
                completed = true
            )
        }

        if (processingResult.toolResponses.isEmpty()) {
            Log.d("ToolNode", "No tool responses to process")
            return state.copy(completed = true)
        }

        // Add tool responses to state
        Log.d("ToolNode", "Adding ${processingResult.toolResponses.size} tool responses to state")
        processingResult.toolResponses.forEach { toolResponse ->
            Log.d("ToolNode", "Tool response: ${toolResponse.id}, type=${toolResponse.type}, output=${toolResponse.output.toString().take(50)}...")
        }
        state.toolResponses.addAll(processingResult.toolResponses)

        // Create follow-up prompt
        val followUpPrompt = processingResult.buildFollowUpPrompt()
        Log.d("ToolNode", "Created follow-up prompt (${followUpPrompt.length} chars): ${followUpPrompt.take(100)}...")
        state.messages.add(Message(MessageRole.TOOL, followUpPrompt))

        val shouldTerminate = processingResult.shouldTerminateFlow()
        Log.d("ToolNode", "Should terminate flow: $shouldTerminate")

        return state.copy(
            completed = shouldTerminate
        )
    }
}