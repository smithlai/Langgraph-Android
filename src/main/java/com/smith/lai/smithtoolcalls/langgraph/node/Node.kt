package com.smith.lai.smithtoolcalls.langgraph.node

import com.smith.lai.smithtoolcalls.langgraph.GraphState

/**
 * 节点接口 - 定义图中节点的基本行为
 * 节点处理特定类型的状态并返回更新后的状态
 */
interface Node<S> {
    /**
     * 执行节点逻辑并返回更新后的状态
     * @param state 输入状态
     * @return 更新后的状态
     */
    suspend fun invoke(state: S): S
}

/**
 * 标准节点常量
 */
object NodeNames {
    /**
     * 标准入口节点名称
     */
    const val START = "start"

    /**
     * 标准出口节点名称
     */
    const val END = "end"

    /**
     * 标准LLM节点名称
     */
    const val LLM = "llm"

    /**
     * 标准工具节点名称
     */
    const val TOOL = "tool"

    /**
     * 标准记忆节点名称
     */
    const val MEMORY = "memory"
}