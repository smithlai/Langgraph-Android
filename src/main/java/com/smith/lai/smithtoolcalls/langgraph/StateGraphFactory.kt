package com.smith.lai.smithtoolcalls.langgraph

import com.smith.lai.smithtoolcalls.ToolRegistry
import com.smith.lai.smithtoolcalls.langgraph.node.Node
import io.shubham0204.smollm.SmolLM

/**
 * 泛型狀態接口，定義狀態必須具備的基本功能
 */
interface GraphState {
    val completed: Boolean
    val error: String?
    val stepCount: Int

    fun withCompleted(isCompleted: Boolean): GraphState
    fun withError(errorMessage: String): GraphState
    fun incrementStep(): GraphState
}

/**
 * Factory for creating pre-configured graphs with specific state types
 */
object StateGraphFactory {
    /**
     * Creates a standard conversation agent with LLM, Tool, and memory handling
     *
     * @param model LLM model to use
     * @param toolRegistry Tool registry for tool execution
     * @param createLLMNode function to create an LLM node for state type S
     * @param createToolNode function to create a tool node for state type S
     * @param createMemoryNode function to create a memory node for state type S (optional)
     * @param createStartNode function to create a start node for state type S (optional)
     * @param createEndNode function to create an end node for state type S (optional)
     */
    fun <S : GraphState> createConversationalAgent(
        model: SmolLM,
        toolRegistry: ToolRegistry,
        createLLMNode: (SmolLM, ToolRegistry) -> Node<S>,
        createToolNode: (ToolRegistry) -> Node<S>,
        createMemoryNode: (() -> Node<S>)? = null,
        createStartNode: (() -> Node<S>)? = null,
        createEndNode: ((String) -> Node<S>)? = null
    ): LangGraph<S> {
        // Create graph builder
        val graphBuilder = LangGraph<S>()

        // Add nodes
        graphBuilder.addNode("memory", createMemoryNode?.invoke() ?: createPassThroughNode())
        graphBuilder.addNode("llm", createLLMNode(model, toolRegistry))
        graphBuilder.addNode("tool", createToolNode(toolRegistry))
        graphBuilder.addNode("start", createStartNode?.invoke() ?: createPassThroughNode())
        graphBuilder.addNode("end", createEndNode?.invoke("Conversation agent completed") ?: createPassThroughNode())

        // Set entry point
        graphBuilder.setEntryPoint("start")

        // Set completion checker
        graphBuilder.setCompletionChecker { state -> state.completed }

        // Add edges
        graphBuilder.addEdge("start", "memory")
        graphBuilder.addEdge("memory", "llm")

        // Conditional edges
        graphBuilder.addConditionalEdges(
            "llm",
            mapOf(
                StateConditions.hasToolCalls<S>() to "tool",
                StateConditions.isComplete<S>() to "end"
            ),
            defaultTarget = "end"
        )

        graphBuilder.addEdge("tool", "llm")

        // Compile and return the graph
        return graphBuilder.compile()
    }

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
        graphBuilder.setEntryPoint("start")

        // Set completion checker
        graphBuilder.setCompletionChecker { state -> state.completed }

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
        return object : Node<S> {
            override suspend fun invoke(state: S): S {
                // Simple pass-through node
                return state
            }
        }
    }
}

/**
 * 通用狀態條件
 */
object StateConditions {
    /**
     * 檢查狀態是否有工具調用
     * 根據命名約定查找hasToolCalls屬性
     */
    fun <S> hasToolCalls(): (S) -> Boolean = { state ->
        // 使用反射嘗試獲取hasToolCalls屬性
        try {
            val method = state!!.javaClass.getMethod("getHasToolCalls")
            method.invoke(state) as? Boolean ?: false
        } catch (e: Exception) {
            try {
                // 嘗試直接訪問hasToolCalls字段
                val field = state!!.javaClass.getDeclaredField("hasToolCalls")
                field.isAccessible = true
                field.get(state) as? Boolean ?: false
            } catch (e: Exception) {
                // 如果無法獲取，則返回false
                false
            }
        }
    }

    /**
     * 檢查狀態是否有錯誤
     */
    fun <S> hasError(): (S) -> Boolean = { state ->
        when (state) {
            is GraphState -> state.error != null
            else -> false
        }
    }

    /**
     * 檢查狀態是否已完成
     */
    fun <S> isComplete(): (S) -> Boolean = { state ->
        when (state) {
            is GraphState -> state.completed
            else -> false
        }
    }

    /**
     * 組合多個條件，所有條件必須為真
     */
    fun <S> all(vararg conditions: (S) -> Boolean): (S) -> Boolean = { state ->
        conditions.all { it(state) }
    }

    /**
     * 組合多個條件，任一條件為真
     */
    fun <S> any(vararg conditions: (S) -> Boolean): (S) -> Boolean = { state ->
        conditions.any { it(state) }
    }

    /**
     * 否定條件
     */
    fun <S> not(condition: (S) -> Boolean): (S) -> Boolean = { state ->
        !condition(state)
    }
}