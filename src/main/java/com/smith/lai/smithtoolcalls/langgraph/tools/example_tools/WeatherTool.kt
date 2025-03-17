package com.smith.lai.smithtoolcalls.langgraph.tools.example_tools

import com.smith.lai.smithtoolcalls.langgraph.response.ToolFollowUpMetadata
import com.smith.lai.smithtoolcalls.langgraph.tools.BaseTool
import com.smith.lai.smithtoolcalls.langgraph.tools.ToolAnnotation
import kotlinx.serialization.Serializable


@Serializable
data class WeatherInput(
    val city: String,
    val metric: String = "celsius"
)

@Serializable
data class WeatherOutput(
    val temperature: Int,
    val condition: String,
    val humidity: Int,
    val city: String
)

@ToolAnnotation(
    name = "get_weather",
    description = "Get weather info for a city",
    returnDescription = "Returns current weather conditions including temperature, humidity, and general conditions"
)
class WeatherTool : BaseTool<WeatherInput, WeatherOutput>() {
    override suspend fun invoke(input: WeatherInput): WeatherOutput {
        // This is a mock implementation
        val cityLower = input.city.lowercase()

        // Generate mock data based on city name
        val mockData = when {
            cityLower.contains("san francisco") -> WeatherOutput(
                temperature = 16,
                condition = "Partly cloudy with fog",
                humidity = 75,
                city = "San Francisco"
            )
            cityLower.contains("new york") -> WeatherOutput(
                temperature = 24,
                condition = "Sunny",
                humidity = 60,
                city = "New York"
            )
            cityLower.contains("seattle") -> WeatherOutput(
                temperature = 14,
                condition = "Rainy",
                humidity = 85,
                city = "Seattle"
            )
            cityLower.contains("tokyo") -> WeatherOutput(
                temperature = 22,
                condition = "Clear",
                humidity = 65,
                city = "Tokyo"
            )
            else -> WeatherOutput(
                temperature = 20,
                condition = "Clear",
                humidity = 70,
                city = input.city
            )
        }

        return mockData
    }

    override fun getFollowUpMetadata(response: WeatherOutput): ToolFollowUpMetadata {
        val customPrompt = """
The weather tool returned information for ${response.city}:
- Temperature: ${response.temperature}°${if (response.temperature > 25) "C (quite warm)" else "C"} 
- Condition: ${response.condition}
- Humidity: ${response.humidity}%

Based on this weather information, please continue answering the user's question.
"""

        return ToolFollowUpMetadata(
            requiresFollowUp = true,
//            shouldTerminateFlow = false,
            customFollowUpPrompt = customPrompt
        )
    }
}