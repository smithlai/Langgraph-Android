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
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class GraphNode(val name: String)

/**
 * Enum for standard node types
 */
enum class NodeTypes(val id: String) {
    START("start"),
    END("end"),
    LLM("llm"),
    TOOL("tool"),
    MEMORY("memory"),
    FORMATTER("formatter");

    override fun toString(): String = id
}