package com.smith.lai.smithtoolcalls.langgraph.node

import android.util.Log
import com.smith.lai.smithtoolcalls.langgraph.state.BaseState

/**
 * End node that properly finalizes the graph execution
 */
class EndNode(private val completionMessage: String = "Graph execution completed") : StateNode {
    override suspend fun invoke(state: BaseState): BaseState {
        val duration = state.executionDuration()

        Log.d("EndNode", "Finalizing graph execution after ${state.stepCount} steps in ${duration}ms")

        if (state.error != null) {
            Log.e("EndNode", "Execution completed with error: ${state.error}")
        } else {
            Log.d("EndNode", "Execution completed successfully: $completionMessage")
        }

        // If we don't have a final response yet, use the last assistant message
        if (state.getString("finalResponse").isEmpty()) {
            val messagesObj = state.getObject("messages")
            if (messagesObj != null) {
                val messagesArr = messagesObj.optJSONArray("messages")
                if (messagesArr != null) {
                    // 从后往前查找最后一条助手消息
                    for (i in messagesArr.length() - 1 downTo 0) {
                        val message = messagesArr.getJSONObject(i)
                        if (message.getString("role") == "ASSISTANT") {
                            val content = message.getString("content")
                            state.setString("finalResponse", content)
                            Log.d("EndNode", "Setting final response from last assistant message (${content.length} chars)")
                            break
                        }
                    }
                }
            }
        }

        // Make sure the state is marked as completed
        return state.withCompleted(true)
    }
}