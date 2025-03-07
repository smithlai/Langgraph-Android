package com.smith.lai.smithtoolcalls.langgraph

import com.smith.lai.smithtoolcalls.ToolRegistry
import com.smith.lai.smithtoolcalls.langgraph.node.Node
import com.smith.lai.smithtoolcalls.langgraph.nodes.GenericNodes
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState
import io.shubham0204.smollm.SmolLM



/**
 * Factory for creating pre-configured graphs with specific state types
 */
object StateGraphFactory {

    /**
     * Creates a simple LLM-only agent without tool calling
     */
    fun <S : GraphState> createSimpleAgent(
        model: SmolLM,
        toolRegistry: ToolRegistry,
        createLLMNode: (SmolLM, ToolRegistry) -> Node<S>,
        createStartNode: (() -> Node<S>)? = null,
        createEndNode: ((String) -> Node<S>)? = null
    ): LangGraph<S> {
        val graphBuilder = LangGraph<S>()

        // Add nodes
        graphBuilder.addNode("start", createStartNode?.invoke() ?: createPassThroughNode())
        graphBuilder.addNode("llm", createLLMNode(model, toolRegistry))
        graphBuilder.addNode("end", createEndNode?.invoke("Simple agent completed") ?: createPassThroughNode())

        // Set entry point
//        graphBuilder.setEntryPoint("start")

        // Set completion checker
//        graphBuilder.setCompletionChecker { state -> state.completed }

        // Add edges
        graphBuilder.addEdge("start", "llm")
        graphBuilder.addEdge("llm", "end")

        return graphBuilder.compile()
    }

    /**
     * Create a custom graph using a builder
     */
    fun <S : GraphState> createCustomGraph(
        configurator: LangGraph<S>.() -> Unit
    ): LangGraph<S> {
        val graphBuilder = LangGraph<S>()

        // Apply custom configuration
        graphBuilder.configurator()

        // Compile and return
        return graphBuilder.compile()
    }

    /**
     * Creates a default pass-through node for the given state type
     */
    private fun <S : GraphState> createPassThroughNode(): Node<S> {
        // 使用通用節點工具創建通過節點
        return GenericNodes.createPassThroughNode<S>()
    }
}

