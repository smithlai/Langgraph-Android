package com.smith.lai.smithtoolcalls.langgraph.node

/**
 * 节点接口 - 定义图中节点的基本行为
 * 节点处理特定类型的状态并返回更新后的状态
 */
abstract class Node<S> {
    companion object{
        /**
         * 标准节点常量
         */
        object NodeNames {
            const val START = "__start__"
            const val END = "__end__"
            const val TOOLS = "tools"
        }
    }
    /**
     * 执行节点逻辑并返回更新后的状态
     * @param state 输入状态
     * @return 更新后的状态
     */
    abstract suspend fun invoke(state: S): S
}
