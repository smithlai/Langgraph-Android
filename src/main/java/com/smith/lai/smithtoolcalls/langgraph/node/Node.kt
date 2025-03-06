package com.smith.lai.smithtoolcalls.langgraph.node

import com.smith.lai.smithtoolcalls.langgraph.state.BaseState

/**
 * Base Node interface following LangGraph pattern
 */
interface BaseNode<S, O> {
    suspend fun invoke(state: S): O
}


/**
 * 節點接口 - 處理特定類型的狀態
 */
interface Node<S> {
    suspend fun invoke(state: S): S
}

/**
 * 标准节点，使用BaseState作为状态
 */
interface StateNode : Node<BaseState>

/**
 * Enum for standard node types
 */
object NodeTypes {
    const val START = "__start__"
    const val END = "__end__"
}