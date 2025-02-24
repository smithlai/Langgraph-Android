package com.smith.lai.smithtoolcalls.tool_calls.tools.example_tools

import com.smith.lai.smithtoolcalls.tool_calls.tools.BaseTool
import com.smith.lai.smithtoolcalls.tool_calls.tools.Tool
import kotlinx.serialization.Serializable


@Serializable
data class WeatherInput(
    val location: String,
    val unit: String = "celsius"
)

@Tool(
    name = "get_weather",
    description = "Get the current weather in a given location",
    returnDescription = "The current weather conditions and temperature"
)
class WeatherTool : BaseTool<WeatherInput, String>() {
    override suspend fun invoke(input: WeatherInput): String {
        return "Current weather in ${input.location} is 25°${input.unit.first().uppercase()}, Partly Cloudy"
    }
}