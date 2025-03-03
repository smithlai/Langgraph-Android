package com.smith.lai.smithtoolcalls.tools.example_tools

import com.smith.lai.smithtoolcalls.tools.BaseTool
import com.smith.lai.smithtoolcalls.tools.ToolAnnotation
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@ToolAnnotation(name = "tool_today", description = "取得今天日期")
class ToolToday : BaseTool<Unit, String>() {
    override suspend fun invoke(input: Unit): String {
        delay(1000)
        val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
        val today = LocalDate.now()
        val formattedDate = today.format(formatter)
        return formattedDate
    }
}
