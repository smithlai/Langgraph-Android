package com.smith.lai.smithtoolcalls.langgraph

import android.util.Log
import com.smith.lai.smithtoolcalls.langgraph.node.Node
import com.smith.lai.smithtoolcalls.langgraph.node.Node.Companion.NodeNames
import com.smith.lai.smithtoolcalls.langgraph.state.GraphState
import com.smith.lai.smithtoolcalls.langgraph.state.Message
import com.smith.lai.smithtoolcalls.langgraph.state.StateConditions
import com.smith.lai.smithtoolcalls.tools.ToolResponse

/**
 * LangGraph - 通用圖執行引擎
 * 完全負責狀態管理和流程控制
 */
class LangGraph<S: GraphState>(
    private val nodes: MutableMap<String, Node<S>> = mutableMapOf(),
    private val edges: MutableMap<String, MutableMap<(S) -> Boolean, String>> = mutableMapOf(),
    private val defaultEdges: MutableMap<String, String> = mutableMapOf(),
    private var startNodeName: String = NodeNames.START,
    private var endNodeName: String = NodeNames.END,
    private var maxSteps: Int = 50
) {
    private val logTag = "LangGraph"
    private var compiled = false

    fun addStartNode(): LangGraph<S> {
        return addNode(NodeNames.START, com.smith.lai.smithtoolcalls.langgraph.node.StartNode<S>())
    }

    fun addEndNode(): LangGraph<S> {
        return addNode(NodeNames.END, com.smith.lai.smithtoolcalls.langgraph.node.EndNode<S>())
    }

    /**
     * 添加節點
     */
    fun addNode(name: String, node: Node<S>): LangGraph<S> {
        nodes[name] = node
        return this
    }

    /**
     * 添加直接邊
     */
    fun addEdge(source: String, target: String): LangGraph<S> {
        defaultEdges[source] = target
        return this
    }

    /**
     * 添加條件邊
     */
    fun addConditionalEdges(
        source: String,
        conditionMap: Map<(S) -> Boolean, String>,
        defaultTarget: String? = null
    ): LangGraph<S> {
        val nodeEdges = edges.getOrPut(source) { mutableMapOf() }
        nodeEdges.putAll(conditionMap)

        if (defaultTarget != null) {
            defaultEdges[source] = defaultTarget
        }

        return this
    }

    /**
     * 設置最大步驟數
     */
    fun setMaxSteps(steps: Int): LangGraph<S> {
        maxSteps = steps
        return this
    }

    /**
     * 編譯圖
     */
    fun compile(): LangGraph<S> {
        // 檢查必要節點
        if (!nodes.containsKey(startNodeName)) {
            throw IllegalStateException("缺少入口節點: '$startNodeName'")
        }

        if (!nodes.containsKey(endNodeName)) {
            Log.w(logTag, "未定義結束節點 '$endNodeName'，這可能導致圖執行無法正確終止")
        }

        compiled = true
        return this
    }

    /**
     * 執行圖（調用別名）
     */
    suspend fun invoke(initialState: S): S {
        return run(initialState)
    }

    /**
     * 執行圖
     */
    suspend fun run(initialState: S): S {
        if (!compiled) {
            Log.w(logTag, "圖未編譯，現在自動編譯")
            compile()
        }

        var state = initialState
        var currentNodeName = startNodeName

        val startTime = System.currentTimeMillis()

        Log.d(logTag, "開始執行圖，入口: '$startNodeName'")

        while (true) {
            // 增加步驟計數
            state.incrementStep()

            // 檢查最大步驟數
            if (state.stepCount >= maxSteps) {
                Log.e(logTag, "達到最大步驟數 ($maxSteps)，終止執行")
                break
            }

            // 獲取當前節點
            val currentNode = nodes[currentNodeName]
            if (currentNode == null) {
                Log.e(logTag, "找不到節點 '$currentNodeName'，終止執行")
                break
            }

            Log.d(logTag, "===== 步驟 ${state.stepCount}: 執行節點 '$currentNodeName' =====")

            try {
                // 執行節點
                val nodeStartTime = System.currentTimeMillis()
                val nodeOutput = currentNode.process(state)
                val nodeDuration = System.currentTimeMillis() - nodeStartTime

                Log.d(logTag, "步驟 ${state.stepCount}: 節點 '$currentNodeName' 執行完成，耗時 ${nodeDuration}ms")

                // 處理節點輸出並更新狀態 - 基於輸出內容而非節點名稱
                when (nodeOutput) {
                    // LLM 節點輸出消息
                    is Message -> {
                        Log.d(logTag, "處理輸出: Message - ${nodeOutput.content.take(50)}...")

                        // 添加消息到狀態
                        state.addMessage(nodeOutput)

                        // 檢查是否有工具調用
                        if (nodeOutput.structuredLLMResponse != null && nodeOutput.hasToolCalls()) {
                            Log.d(logTag, "消息包含工具調用，繼續流程")
                        } else {
                            state.withCompleted(true)
                            Log.d(logTag, "消息不包含工具調用，標記流程完成")
                        }
                    }

                    // 檢查是否為List<ToolResponse<*>>
                    // 工具節點輸出執行結果
                    is List<*> -> {
                        if (nodeOutput.isNotEmpty() && nodeOutput.all { it is ToolResponse<*> }) {
                            Log.d(logTag, "處理輸出: ToolResponse 列表 - ${nodeOutput.size} 個響應")

                            // 是工具響應列表
                            var shouldTerminate = false

                            @Suppress("UNCHECKED_CAST")
                            (nodeOutput as List<ToolResponse<*>>).forEach { response ->
                                Log.d(logTag, "添加工具響應: ${response.type}")
                                val toolMessage = Message.fromToolResponse(response)
                                state.addMessage(toolMessage)

                                // 檢查工具是否要求終止流程
                                if (response.followUpMetadata.shouldTerminateFlow) {
                                    shouldTerminate = true
                                    Log.d(logTag, "工具要求終止流程")
                                }
                            }

                            if (shouldTerminate) {
                                state.withCompleted(true)
                                Log.d(logTag, "根據工具響應，標記流程完成")
                            }
                        } else {
                            Log.d(logTag, "處理輸出: 普通列表 - ${nodeOutput.size} 個項目")
                        }
                    }

                    // 終止節點可能返回特定標記
                    is String -> {
                        if (nodeOutput == "END" || currentNodeName == endNodeName) {
                            Log.d(logTag, "檢測到終止標記，標記流程完成")
                            state.withCompleted(true)
                        } else {
                            Log.d(logTag, "處理字符串輸出: $nodeOutput")
                        }
                    }

                    // 其他類型輸出
                    else -> {
                        if (currentNodeName == endNodeName) {
                            Log.d(logTag, "終止節點執行完成，標記流程完成")
                            state.withCompleted(true)
                        } else {
                            Log.d(logTag, "未知輸出類型或空輸出")
                        }
                    }
                }

            } catch (e: Exception) {
                // 處理節點執行異常
                Log.e(logTag, "節點執行異常: ${e.message}", e)
                state = state.withError("Error executing node '$currentNodeName': ${e.message}") as S
                state.withCompleted(true)
                break
            }

            // 檢查完成條件
            if (state.completed || currentNodeName == endNodeName) {
                Log.d(logTag, "狀態已完成或達到終止節點，結束執行")
                break
            }

            // 查找下一個節點
            val nextNodeName = findNextNode(currentNodeName, state)

            // 防止無限循環
            if (nextNodeName == currentNodeName && state.stepCount > 5) {
                Log.e(logTag, "檢測到潛在的無限循環: '$currentNodeName' -> '$currentNodeName'，終止執行")
                break
            }

            Log.d(logTag, "步驟 ${state.stepCount}: 從 '$currentNodeName' 轉換到 '$nextNodeName'")
            currentNodeName = nextNodeName
        }

        val totalDuration = System.currentTimeMillis() - startTime
        Log.d(logTag, "圖執行完成，共 ${state.stepCount} 步，總耗時 ${totalDuration}ms")

        return state
    }

    /**
     * 查找下一個要轉換到的節點
     */
    private fun findNextNode(currentNodeName: String, state: S): String {
        // 取得這個節點的條件邊
        val conditionalEdges = edges[currentNodeName]

        if (conditionalEdges != null) {
            // 按順序嘗試每個條件
            for ((condition, targetNode) in conditionalEdges) {
                if (condition(state)) {
                    Log.d(logTag, "滿足邊條件: $currentNodeName -> $targetNode")
                    return targetNode
                }
            }
        }

        // 如果沒有條件匹配，使用預設邊（如果存在）
        val defaultTarget = defaultEdges[currentNodeName]
        if (defaultTarget != null) {
            Log.d(logTag, "使用預設邊: $currentNodeName -> $defaultTarget")
            return defaultTarget
        }

        // 否則留在當前節點
        Log.d(logTag, "沒有匹配的邊條件，保持在當前節點: $currentNodeName")
        return currentNodeName
    }
}