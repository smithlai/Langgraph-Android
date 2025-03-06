package com.smith.lai.smithtoolcalls.langgraph.node

import android.util.Log

/**
 * Memory node that stores and retrieves conversation history
 */
class MemoryNode : Node<StateGraph> {
    override suspend fun invoke(state: StateGraph): StateGraph {
        Log.d("MemoryNode", "Processing state with ${state.messages.size} messages")

        // Memory operations can be implemented here
        // For now, just pass through the state
        return state
    }
}