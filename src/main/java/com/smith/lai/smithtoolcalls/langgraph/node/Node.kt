package com.smith.lai.smithtoolcalls.langgraph.node

/**
 * Base Node interface following LangGraph pattern
 */
interface BaseNode<S : BaseState, O : BaseState> {
    suspend fun invoke(state: S): O
}

/**
 * Graph node that accepts and returns the same type
 */
interface Node<S : BaseState> : BaseNode<S, S>

/**
 * Decorator for defining a LangGraph node
 */
//@Target(AnnotationTarget.CLASS)
//@Retention(AnnotationRetention.RUNTIME)
//annotation class GraphNode(val name: String)

/**
 * Enum for standard node types
 */
object NodeTypes {
    val START = "__start__"
    val END = "__end__"
}