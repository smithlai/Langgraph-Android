package com.smith.lai.smithtoolcalls.langgraph.node

import android.util.Log
import com.smith.lai.smithtoolcalls.langgraph.state.BaseState

/**
 * Start node for initializing graph execution
 */
class StartNode : StateNode {
    override suspend fun invoke(state: BaseState): BaseState {
        val query = state.getString("query")
        Log.d("StartNode", "Initializing graph execution with query: $query")

        // 重置步骤计数和开始时间
        state.stepCount = 0

        return state
    }
}