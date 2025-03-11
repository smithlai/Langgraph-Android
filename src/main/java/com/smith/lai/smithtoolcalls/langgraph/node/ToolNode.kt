package com.smith.lai.smithtoolcalls.langgraph.node

import com.smith.lai.smithtoolcalls.langgraph.model.LLMWithTools
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState

/**
 * 極簡的工具節點
 */
class ToolNode<S : GraphState>(private val model: LLMWithTools) : Node<S>() {
    override suspend fun invoke(state: S): S {
        return model.processToolCallsInState(state) as S
    }
}