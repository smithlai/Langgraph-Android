package com.smith.lai.smithtoolcalls.langgraph.node

import com.smith.lai.smithtoolcalls.langgraph.model.LLMWithTools
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState

/**
 * 極簡的 LLM 節點
 */
class LLMNode<S : GraphState>(private val model: LLMWithTools) : Node<S>() {
    override suspend fun invoke(state: S): S {
        return model.invoke(state) as S
    }
}