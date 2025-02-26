package com.smith.lai.smithtoolcalls.smollm

import android.util.Log
import com.smith.lai.smithtoolcalls.ToolRegistry
import com.smith.lai.smithtoolcalls.tool_calls.tools.ToolResponseType
import io.shubham0204.smollm.SmolLM
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * 使用增強版ToolRegistry的SmolLMWithTools
 */
class SmolLMWithTools(val smolLM: SmolLM, private val toolRegistry: ToolRegistry, additionalInstructions: String = "") {

    init {
        // 在構造函數中設置系統提示
        setupSystemPrompt(additionalInstructions)
    }

    /**
     * 設置系統提示
     */
    private fun setupSystemPrompt(additionalInstructions: String = "") {
        val systemPrompt = toolRegistry.createSystemPrompt() +
                (if (additionalInstructions.isNotEmpty()) "\n\n$additionalInstructions" else "")
        smolLM.addSystemPrompt(systemPrompt)
    }

    /**
     * 取得LLM回應，處理工具呼叫並返回結果
     */
    suspend fun getResponseWithTools(query: String): Flow<String> = flow {
        // 添加使用者訊息
        smolLM.addUserMessage(query)

        // 取得LLM回應
        val firstResponse = StringBuilder()
        smolLM.getResponse("").collect {
            firstResponse.append(it)
        }
        Log.d("SmolLMWithTools", "First Response: ${firstResponse.toString()}")

        // 使用擴展的ToolRegistry處理LLM回應
        val processingResult = toolRegistry.processLLMResponse(firstResponse.toString())
        Log.d("SmolLMWithTools", "Structured Response: ${processingResult.structuredResponse}")
        Log.d("SmolLMWithTools", "Tool Responses: ${processingResult.toolResponses.map { it.output.toString() }.joinToString(",")}")

        // 如果不需要後續處理，直接返回結果
        if (!processingResult.requiresFollowUp) {
            emit(processingResult.toolResponses.first().output.toString())
            return@flow
        }

        // 處理工具調用結果
        if (processingResult.toolResponses.isNotEmpty()) {
            // 獲取格式化的工具回應JSON
            val toolResultsJson = processingResult.getToolResponseJson()
            Log.d("SmolLMWithTools", "Tool Results JSON: $toolResultsJson")

            // 將工具回應作為使用者訊息發送回LLM
            smolLM.addUserMessage(toolResultsJson)

            // 取得LLM的最終回應
            smolLM.getResponse("").collect {
                emit(it)
            }
        } else {
            // 如果沒有工具調用或工具執行失敗，直接返回原始回應
            emit(firstResponse.toString())
        }
    }

    /**
     * 啟動一個完整的對話，支援多輪工具呼叫
     */
    suspend fun chat(query: String, maxToolCalls: Int = 5): Flow<String> = flow {
        var toolCallCount = 0

        // 初始化對話
        smolLM.addUserMessage(query)

        while (toolCallCount < maxToolCalls) {
            // 取得LLM回應
            val response = StringBuilder()
            smolLM.getResponse("").collect {
                response.append(it)
                emit("助理: $it") // 向用戶實時顯示回應
            }

            // 使用擴展的ToolRegistry處理LLM回應
            val processingResult = toolRegistry.processLLMResponse(response.toString())

            // 如果不需要後續處理，結束對話
            if (!processingResult.requiresFollowUp) {
                break
            }

            // 處理工具調用結果
            if (processingResult.toolResponses.isNotEmpty()) {
                // 獲取格式化的工具回應JSON
                val toolResultsJson = processingResult.getToolResponseJson()
                emit("工具執行結果: $toolResultsJson") // 向用戶顯示工具執行結果

                // 將工具回應作為使用者訊息發送回LLM
                smolLM.addUserMessage(toolResultsJson)

                toolCallCount++
            } else {
                // 如果沒有工具調用或工具執行失敗，結束對話
                break
            }
        }
    }
}