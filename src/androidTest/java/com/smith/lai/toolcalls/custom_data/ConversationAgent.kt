package com.smith.lai.toolcalls.custom_data

import com.smith.lai.toolcalls.langgraph.LangGraph
import com.smith.lai.toolcalls.langgraph.model.LLMWithTools
import com.smith.lai.toolcalls.langgraph.node.LLMNode
import com.smith.lai.toolcalls.langgraph.node.Node.Companion.NodeNames
import com.smith.lai.toolcalls.langgraph.node.ToolNode
import com.smith.lai.toolcalls.langgraph.state.GraphState
import com.smith.lai.toolcalls.langgraph.state.StateConditions
import com.smith.lai.toolcalls.langgraph.tools.BaseTool

/**
 * 統一的會話代理工廠 - 創建標準會話代理流程
 */
object ConversationAgent {
    /**
     * 創建標準會話代理，適用於任何GraphState實現
     * 支援工具執行
     */
    fun <S : GraphState> createExampleWithTools(
        model: LLMWithTools,
        tools: List<BaseTool<*, *>> = emptyList()
    ): LangGraph<S> {
        // 創建圖構建器
        val graphBuilder = LangGraph<S>()

        // 創建節點
        val llmNode = LLMNode<S>(model)
        val toolNode = ToolNode<S>(tools)

        // 將工具直接綁定到ToolNode
        if (tools.isNotEmpty()) {
            model.bind_tools(tools)
            toolNode.bind_tools(tools)
        }

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
     * 建立簡化對話代理 - 不使用工具調用，僅對話
     */
    fun <S : GraphState> createExampleWithoutTools(
        model: LLMWithTools
    ): LangGraph<S> {
        val graphBuilder = LangGraph<S>()

        // 添加節點
        val llmNode = LLMNode<S>(model)

        graphBuilder.addStartNode()
        graphBuilder.addEndNode()
        graphBuilder.addNode("llm", llmNode)

        // 添加邊
        graphBuilder.addEdge(NodeNames.START, "llm")

        // 使用狀態條件
        graphBuilder.addConditionalEdges(
            "llm",
            mapOf(
                StateConditions.isComplete<S>() to NodeNames.END
            ),
            defaultTarget = NodeNames.END
        )

        // 編譯並返回圖
        return graphBuilder.compile()
    }
}