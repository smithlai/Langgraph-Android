package com.smith.lai.smithtoolcalls.langgraph.response

import kotlinx.serialization.Serializable

@Serializable
data class ToolResponse<T>(
    val id: String,
    val output: T,
    // 新增：直接包含後續處理元數據
    val followUpMetadata: ToolFollowUpMetadata = ToolFollowUpMetadata()
)

/**
 * 工具後續處理元數據
 */
@Serializable
data class ToolFollowUpMetadata(
    // 是否需要後續處理
    val requiresFollowUp: Boolean = true,
    // 是否應該終止流程
    val shouldTerminateFlow: Boolean = false,
    // 自定義的後續提示詞
    val customFollowUpPrompt: String = ""
)