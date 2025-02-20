package com.smith.lai.smithtoolcalls.tool_calls.test.examples_tools

import com.smith.lai.smithtoolcalls.tool_calls.data.BaseTool
import com.smith.lai.smithtoolcalls.tool_calls.data.ToolAnnotation
import kotlinx.coroutines.delay

// API 查詢工具
@ToolAnnotation(name = "tool_example1", description = "呼叫 API 來獲取資訊")
class ToolExample1 : BaseTool() {
    override suspend fun invoke(input: String): String {
        delay(1000) // 模擬網路請求
        return "tool_example1 回應: $input\n"
    }

}

