package com.smith.lai.toolcalls.langgraph

import android.util.Log
import com.smith.lai.toolcalls.langgraph.node.Node
import com.smith.lai.toolcalls.langgraph.node.Node.Companion.NodeNames
import com.smith.lai.toolcalls.langgraph.state.GraphState
import com.smith.lai.toolcalls.langgraph.state.MessageRole

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
        return addNode(NodeNames.START, com.smith.lai.toolcalls.langgraph.node.StartNode<S>())
    }

    fun addEndNode(): LangGraph<S> {
        return addNode(NodeNames.END, com.smith.lai.toolcalls.langgraph.node.EndNode<S>())
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
        state.resetStatus()

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
                nodeOutput.forEach { message ->
                    when(message.role){
                        // LLM 節點輸出消息
                        MessageRole.ASSISTANT -> {
                            if (message.structuredLLMResponse == null)
                                throw AssertionError("message.structuredLLMResponse should work with MessageRole.ASSISTANT")
                            Log.d(logTag, "處理輸出: Message - ${message.content.take(50)}...")
                            state.addMessage(message)
                        }
                        // Tool 節點輸出消息
                        MessageRole.TOOL -> {
                            if(message.toolResponse == null)
                                throw AssertionError("message.toolResponse should work with MessageRole.TOOL")

                            Log.d(logTag, "處理輸出: ToolResponse - ${message.toolResponse.output}")
                            state.addMessage(message)
                        }
                        // Node 發生Error
                        MessageRole.ERROR -> {
                            Log.d(logTag, "發生錯誤: Error Response - ${message.content}")
//                        state.addMessage(message)
                            throw IllegalStateException("${message.content}")
                        }
                        else ->{}
                    }
                }
                if (currentNodeName == endNodeName) {
                    Log.d(logTag, "終止節點執行完成，標記流程完成")
                    state.withCompleted(true)
                }


            } catch (e: Exception) {
                // 處理節點執行異常
                Log.e(logTag, "節點執行異常: '$currentNodeName': ${e.message}", e)
                state = state.withError("${e.message}") as S
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