package com.smith.lai.smithtoolcalls.custom_data

import com.smith.lai.smithtoolcalls.ToolRegistry
import com.smith.lai.smithtoolcalls.langgraph.LangGraph
import com.smith.lai.smithtoolcalls.langgraph.node.Node
import com.smith.lai.smithtoolcalls.langgraph.node.Node.Companion.NodeNames
import com.smith.lai.smithtoolcalls.langgraph.nodes.ToolNodes
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState
import com.smith.lai.smithtoolcalls.langgraph.state.StateConditions
import io.shubham0204.smollm.SmolLM

/**
 * 統一的會話代理工廠 - 創建標準會話代理流程
 */
object ConversationAgent {
    /**
     * 創建標準會話代理，適用於任何GraphState實現
     */
    fun <S : GraphState> createExampleWithTools(
        model: SmolLM,
        toolRegistry: ToolRegistry,
        // 可選的自定義節點創建函數
        createLLMNode: ((SmolLM, ToolRegistry) -> Node<S>)? = null,
        createToolNode: ((ToolRegistry) -> Node<S>)? = null
    ): LangGraph<S> {
        // 創建圖構建器
        val graphBuilder = LangGraph<S>()

        // 使用提供的創建函數或默認實現
        val llmNode = createLLMNode?.invoke(model, toolRegistry)
            ?: LLMNodes.createLLMNode(model, toolRegistry)

        val toolNode = createToolNode?.invoke(toolRegistry)
            ?: ToolNodes.createToolNode(toolRegistry)

        graphBuilder.addStartNode()
        graphBuilder.addEndNode()
        graphBuilder.addNode("llm", llmNode)
        graphBuilder.addNode(NodeNames.TOOLS, toolNode)

        // 添加邊
        graphBuilder.addEdge(NodeNames.START, "llm")

        // 條件邊
        graphBuilder.addConditionalEdges(
            "llm",
            mapOf(
                StateConditions.hasToolCalls<S>() to NodeNames.TOOLS,
                StateConditions.isComplete<S>() to NodeNames.END
            ),
            defaultTarget = NodeNames.END
        )

        graphBuilder.addEdge(NodeNames.TOOLS, "llm")

        // 編譯並返回圖
        return graphBuilder.compile()
    }

    /**
     * 创建简化对话代理 - 不使用工具调用，仅对话
     */
    fun <S : GraphState> createExampleWithoutTools(
        model: SmolLM,
        toolRegistry: ToolRegistry
    ): LangGraph<S> {
        val graphBuilder = LangGraph<S>()

        // 添加节点 - 直接使用新的節點類
        val llmNode = LLMNodes.createLLMNode<S>(model, toolRegistry)

        graphBuilder.addStartNode()
        graphBuilder.addEndNode()
        graphBuilder.addNode("llm", llmNode)

        // 添加边
        graphBuilder.addEdge(NodeNames.START, "llm")
        graphBuilder.addEdge("llm", NodeNames.END)

        // 编译并返回图
        return graphBuilder.compile()
    }
}