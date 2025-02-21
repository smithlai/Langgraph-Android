package com.smith.lai.smithtoolcalls.tool_calls.test.examples_tools

import com.smith.lai.smithtoolcalls.tool_calls.data.BaseTool
import com.smith.lai.smithtoolcalls.tool_calls.data.Tool
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

@Tool(name = "tool_example_noparam", description = "沒有參數")
class ToolExampleNoParam : BaseTool<Unit>() {
    override suspend fun invoke(input: Unit): String {
        delay(1000)
        return "tool_example_noparam 回應: \n"
    }
}

