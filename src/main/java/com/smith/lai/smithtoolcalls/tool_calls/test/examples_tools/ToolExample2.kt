package com.smith.lai.smithtoolcalls.tool_calls.test.examples_tools

import com.smith.lai.smithtoolcalls.tool_calls.data.BaseTool
import com.smith.lai.smithtoolcalls.tool_calls.data.ToolAnnotation
import kotlinx.coroutines.delay

// API 查詢工具
@ToolAnnotation(name = "tool_example2", description = "執行本地計算")
class ToolExample2 : BaseTool() {
    override suspend fun invoke(input: String): String {
        return "tool_example2: ${input.length}\n"
    }
}

