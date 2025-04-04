package com.smith.lai.toolcalls.langgraph.tools.example_tools

import com.smith.lai.toolcalls.langgraph.response.ToolFollowUpMetadata
import com.smith.lai.toolcalls.langgraph.tools.BaseTool
import com.smith.lai.toolcalls.langgraph.tools.ToolAnnotation
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@ToolAnnotation(name = "tool_today", description = "retrieve today's date")
class ToolToday : BaseTool<Unit, String>() {
    override suspend fun invoke(input: Unit): String {
        delay(1000)
        val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
        val today = LocalDate.now()
        val formattedDate = today.format(formatter)
        return formattedDate
    }
    override fun getFollowUpMetadata(response: String): ToolFollowUpMetadata {
        val customPrompt = """
The tool returned: "$response". 
Based on this information, continue answering the request.
"""

        return ToolFollowUpMetadata(
            requiresFollowUp = true,
//            shouldTerminateFlow = false,
            customFollowUpPrompt = customPrompt // 現在使用非空字串
        )
    }
}
