package com.smith.lai.smithtoolcalls.langgraph

import com.smith.lai.smithtoolcalls.ToolRegistry
import com.smith.lai.smithtoolcalls.langgraph.node.EndNode
import com.smith.lai.smithtoolcalls.langgraph.node.LLMNode
import com.smith.lai.smithtoolcalls.langgraph.node.MemoryNode
import com.smith.lai.smithtoolcalls.langgraph.node.NodeTypes
import com.smith.lai.smithtoolcalls.langgraph.node.StartNode
import com.smith.lai.smithtoolcalls.langgraph.node.StateGraph
import com.smith.lai.smithtoolcalls.langgraph.node.ToolNode
import io.shubham0204.smollm.SmolLM

/**
 * Factory for creating pre-configured graphs
 */
object StateGraphFactory {
    /**
     * Creates a standard conversation agent with LLM, Tool, and memory handling
     */
    fun createConversationalAgent(
        model: SmolLM,
        toolRegistry: ToolRegistry
    ): LangGraph {
        // Create nodes
        val startNode = StartNode()
        val memoryNode = MemoryNode()
        val llmNode = LLMNode(model, toolRegistry)
        val toolNode = ToolNode(toolRegistry)
        val endNode = EndNode("Conversation agent completed")

        // Create graph builder (Python-style)
        val graphBuilder = StateGraphBuilder()

        // Add nodes
        graphBuilder.addNode(NodeTypes.MEMORY, memoryNode)
        graphBuilder.addNode(NodeTypes.LLM, llmNode)
        graphBuilder.addNode(NodeTypes.TOOL, toolNode)
        graphBuilder.addNode(NodeTypes.START, startNode)
        graphBuilder.addNode(NodeTypes.END, endNode)

        // Add edges
        graphBuilder.addEdge(NodeTypes.START, NodeTypes.MEMORY)
        graphBuilder.addEdge(NodeTypes.MEMORY, NodeTypes.LLM)

        // Conditional edges - Python style
        graphBuilder.addConditionalEdge(
            NodeTypes.LLM,
            mapOf(
                StateConditions.hasToolCalls to NodeTypes.TOOL,
                StateConditions.isComplete to NodeTypes.END
            ),
            default = NodeTypes.END
        )

        graphBuilder.addEdge(NodeTypes.TOOL, NodeTypes.LLM)

        // Compile and return the graph
        return graphBuilder.compile()
    }

    /**
     * Creates a simple LLM-only agent without tool calling
     */
    fun createSimpleAgent(
        model: SmolLM,
        toolRegistry: ToolRegistry
    ): LangGraph {
        val graphBuilder = StateGraphBuilder()

        val startNode = StartNode()
        val llmNode = LLMNode(model, toolRegistry)
        val endNode = EndNode("Simple agent completed")

        graphBuilder.addNode(NodeTypes.START, startNode)
        graphBuilder.addNode(NodeTypes.LLM, llmNode)
        graphBuilder.addNode(NodeTypes.END, endNode)
        graphBuilder.addEdge(NodeTypes.START, NodeTypes.LLM)
        graphBuilder.addEdge(NodeTypes.LLM, NodeTypes.END)

        return graphBuilder.compile()
    }

    /**
     * Create a custom graph using a Python-style builder
     */
    fun createCustomGraph(
        configurator: StateGraphBuilder.() -> Unit
    ): LangGraph {
        val graphBuilder = StateGraphBuilder()

        // Apply custom configuration
        graphBuilder.configurator()

        // Compile and return
        return graphBuilder.compile()
    }
}

/**
 * Commonly used state conditions
 */
object StateConditions {
    /**
     * Check if state has tool calls
     */
    val hasToolCalls: (StateGraph) -> Boolean = { state ->
        state.processingResult?.toolResponses?.isNotEmpty() == true
    }

    /**
     * Check if state has an error
     */
    val hasError: (StateGraph) -> Boolean = { state ->
        state.error != null
    }

    /**
     * Check if state is complete
     */
    val isComplete: (StateGraph) -> Boolean = { state ->
        state.completed
    }

    /**
     * Combines multiple conditions with AND
     */
    fun all(vararg conditions: (StateGraph) -> Boolean): (StateGraph) -> Boolean = { state ->
        conditions.all { it(state) }
    }

    /**
     * Combines multiple conditions with OR
     */
    fun any(vararg conditions: (StateGraph) -> Boolean): (StateGraph) -> Boolean = { state ->
        conditions.any { it(state) }
    }

    /**
     * Negates a condition
     */
    fun not(condition: (StateGraph) -> Boolean): (StateGraph) -> Boolean = { state ->
        !condition(state)
    }
}