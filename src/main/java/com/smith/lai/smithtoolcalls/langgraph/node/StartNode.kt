package com.smith.lai.smithtoolcalls.langgraph.node

import android.util.Log


/**
 * Start node for initializing graph execution
 */
@GraphNode("start")
class StartNode : Node<StateGraph> {
    override suspend fun invoke(state: StateGraph): StateGraph {
        Log.d("StartNode", "Initializing graph execution with query: ${state.query}")

        // Initialize state with any necessary setup
        return state.copy(
            startTime = System.currentTimeMillis(),
            stepCount = 0
        )
    }
}
