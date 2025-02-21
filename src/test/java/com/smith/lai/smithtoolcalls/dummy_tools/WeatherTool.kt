package com.smith.lai.smithtoolcalls.dummy_tools

import com.smith.lai.smithtoolcalls.tool_calls.data.BaseTool
import com.smith.lai.smithtoolcalls.tool_calls.data.Tool
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
class WeatherTool : BaseTool<WeatherInput>() {
    override suspend fun invoke(input: WeatherInput): String {
        // 模拟天气API调用
        return "Current weather in ${input.location} is 25°${input.unit.first().uppercase()}, Partly Cloudy"
    }
}