package com.smith.lai.smithtoolcalls.tools.example_tools

import com.smith.lai.smithtoolcalls.tools.BaseTool
import com.smith.lai.smithtoolcalls.tools.ToolAnnotation
import com.smith.lai.smithtoolcalls.tools.ToolFollowUpMetadata
import kotlinx.serialization.Serializable


@Serializable
data class WeatherInput(
    val location: String,
    val unit: String = "celsius"
)

@ToolAnnotation(
    name = "get_weather",
    description = "Get the current weather in a given location",
    returnDescription = "The current weather conditions and temperature"
)
class WeatherTool : BaseTool<WeatherInput, String>() {
    override suspend fun invoke(input: WeatherInput): String {
        return "Current weather in ${input.location} is 25°${input.unit.first().uppercase()}, Partly Cloudy"
    }
    override fun getFollowUpMetadata(response: String): ToolFollowUpMetadata {
        val customPrompt = """
The tool returned: "$response". 
Based on this information, continue answering the request.
"""

        return ToolFollowUpMetadata(
            requiresFollowUp = true,
            shouldTerminateFlow = false,
            customFollowUpPrompt = customPrompt // 現在使用非空字串
        )
    }
}