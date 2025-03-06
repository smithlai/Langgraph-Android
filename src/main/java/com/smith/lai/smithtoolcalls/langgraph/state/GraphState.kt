//package com.smith.lai.smithtoolcalls.langgraph.state
//
//import com.smith.lai.smithtoolcalls.langgraph.Message
//import com.smith.lai.smithtoolcalls.langgraph.MessageRole
//import com.smith.lai.smithtoolcalls.tools.ProcessingResult
//import com.smith.lai.smithtoolcalls.tools.ToolResponse
//
///**
// * 預設圖狀態實現，包含 LLM 對話所需的基本業務數據
// */
//class GraphState(
//    // 業務數據
//    var query: String = "",
//    val messages: MutableList<Message> = mutableListOf(),
//    val toolResponses: MutableList<ToolResponse<*>> = mutableListOf(),
//    var processingResult: ProcessingResult? = null,
//    var finalResponse: String = ""
//) : BaseState() {
//    /**
//     * 新增工具回應
//     */
//    fun addToolResponse(response: ToolResponse<*>): GraphState {
//        toolResponses.add(response)
//        return this
//    }
//
//    /**
//     * 設置最終回應
//     */
//    fun setFinalResponse(response: String): GraphState {
//        finalResponse = response
//        return this
//    }
//
//    /**
//     * 新增訊息
//     */
//    fun addMessage(message: Message): GraphState {
//        messages.add(message)
//        return this
//    }
//
//    /**
//     * 獲取最後一條助手訊息
//     */
//    fun getLastAssistantMessage(): String? {
//        return messages.lastOrNull { it.role == MessageRole.ASSISTANT }?.content
//    }
//
//    // 數據轉換方法
//
//    /**
//     * 將 GraphState 轉換為 BaseState
//     */
//    fun toBaseState(): BaseState {
//        // 這個方法允許將 GraphState 的數據保存到 BaseState 中
//        val baseState = BaseState(
//            completed = this.completed,
//            error = this.error,
//            stepCount = this.stepCount,
//            maxSteps = this.maxSteps,
//            startTime = this.startTime
//        )
//
//        // 將業務數據序列化為 JSON
//        baseState.setString("query", query)
//        baseState.setString("finalResponse", finalResponse)
//
//        // 複雜物件需單獨處理
//        // 注意：這裡簡化處理，實際使用可能需要更複雜的序列化邏輯
//
//        return baseState
//    }
//
//    companion object {
//        /**
//         * 從 BaseState 創建 GraphState
//         */
//        fun fromBaseState(baseState: BaseState): GraphState {
//            val graphState = GraphState(
//                query = baseState.getString("query"),
//                finalResponse = baseState.getString("finalResponse")
//            )
//
//            // 複製控制屬性
//            graphState.completed = baseState.completed
//            graphState.error = baseState.error
//            graphState.stepCount = baseState.stepCount
//
//            // 複雜物件需單獨處理
//            // 注意：這裡簡化處理，實際使用可能需要更複雜的反序列化邏輯
//
//            return graphState
//        }
//    }
//}
