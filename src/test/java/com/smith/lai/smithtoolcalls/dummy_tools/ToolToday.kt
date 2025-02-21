package com.smith.lai.smithtoolcalls.dummy_tools

import com.smith.lai.smithtoolcalls.tool_calls.data.BaseTool
import com.smith.lai.smithtoolcalls.tool_calls.data.Tool
import kotlinx.coroutines.delay
import java.util.Date

@Tool(name = "tool_today", description = "取得今天日期")
class ToolToday : BaseTool<Unit>() {
    override suspend fun invoke(input: Unit): String {
        delay(1000)
        return Date().time.toString()
    }
}
