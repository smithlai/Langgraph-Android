# Langgraph Android Module

## Overview

langgraph-android is an Android module that provides tool_calls and langgraph-like function in Android.

Example code: [LangGraphDemoOnSmolChat](https://github.com/smithlai/LangGraphDemoOnSmolChat.git)

## Installation

clone this as your submodule
```sh
git submodule add https://github.com/smithlai/Langgraph-Android.git langgraph-android
```
##### root/setting.gradle.kts
```kotlin
.....
include(":langgraph-android")
......
```
##### root/build.gradle.kts
```kotlin
plugins {
    id("kotlinx-serialization")
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false    //Plugin [id: 'com.google.devtools.ksp'] was not found in any of the following sources:
    kotlin("plugin.serialization") version "2.1.0" apply false    //Plugin [id: 'kotlinx-serialization'] was not found in any of the following sources:

}

dependencies {
    ....
    implementation(project(":langgraph-android"))
    // for @Serialization and @Annotation
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.10")
    implementation("org.jetbrains.kotlin.plugin.serialization:org.jetbrains.kotlin.plugin.serialization.gradle.plugin:2.1.10")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("io.github.classgraph:classgraph:4.8.179")
}
```

## Basic Usage

### Tool Creation

```kotlin
@ToolAnnotation(
    name = "calculator_add",
    description = "Add two integers together",
    returnDescription = "The sum of two integers"
)
class CalculatorTool : BaseTool<CalculatorInput, Int>() {
    @Serializable
    data class CalculatorInput(val param1: Int, val param2: Int)

    override suspend fun invoke(input: CalculatorInput): Int {
        return input.param1 + input.param2
    }

    override fun getFollowUpMetadata(response: Int): ToolFollowUpMetadata {
        return ToolFollowUpMetadata(
            requiresFollowUp = true,
            customFollowUpPrompt = "The calculation result is $response"
        )
    }
}
```

### Example1: LLM with Tools Integration

```kotlin
class ChatViewModel(
    private val smolLM: SmolLM
) {
    private val smolLMWithTools = SmolLMWithTools(
        Llama3_2_3B_LLMToolAdapter(), 
        smolLM
    )

    init {
        val uiBridgeTool = UIBridgeTool().apply {
            setNavigateCallback { cmd ->
                when (cmd) {
                    "chat_setting" -> showMoreOptionsPopup()
                    "rag_setting" -> navigateToRAGSettings()
                }
            }
        }

        val ragTool = RagSearchTool(chunksDB, sentenceEncoder)

        smolLMWithTools.bind_tools(listOf(uiBridgeTool, ragTool))
    }

    fun sendUserQuery(query: String) {
        viewModelScope.launch {
            if (!smolLMWithTools.isToolPromptAdded()) {
                smolLMWithTools.addToolPrompt()
            }

            smolLMWithTools.getResponse(query).collect { partialResponse ->
                val processingResult = smolLMWithTools.processLLMResponse(partialResponse)
                
                if (processingResult.toolResponses.isNotEmpty()) {
                    if (processingResult.requiresFollowUp) {
                        val followUpPrompt = processingResult.buildFollowUpPrompt()
                        Log.e("toolResponses", "Following up with: $followUpPrompt")
                        sendUserQuery(followUpPrompt)
                    }
                }
            }
        }
    }
// Simulated LLM Response in Llama3.2 Tool Call Format
// Original Query: "What's the weather in San Francisco and find me a good restaurant nearby?"
// 
// LLM Response: 
// [weather_tool(city="San Francisco"), rag_search_tool(query="top-rated restaurants in San Francisco")]
// 
// Parsed Tool Calls:
// 1. Weather Tool
//    - Input: { city: "San Francisco" }
//    - Output: "65°F, Partly Cloudy"
// 
// 2. RAG Search Tool
//    - Input: { query: "top-rated restaurants in San Francisco" }
//    - Output: "Zuni Cafe - Mediterranean cuisine, 4.5/5 stars"
// 
// Final Assistant Response:
// "In San Francisco, it's currently 65°F with partly cloudy skies. 
//  I recommend Zuni Cafe, a highly-rated Mediterranean restaurant with 4.5/5 stars."
}
```

### Example2: LangGraph Workflow

```kotlin
class LangGraphExample {
    suspend fun runConversation() {
        val smolLM = SmolLM()
        smolLM.create(
            "/path/to/model/Llama-3.2-3B-Instruct-uncensored-Q4_K_M.gguf",
            0.1f, 0.0f, false, 2048
        )

        val llmWithTools = SmolLMWithTools(
            Llama3_2_3B_LLMToolAdapter(), 
            smolLM
        )

        llmWithTools.bind_tools(listOf(
            CalculatorTool(), 
            WeatherTool()
        ))

        val graph = ConversationAgent.createExampleWithTools<MyCustomState>(
            model = llmWithTools
        )

        val initialState = MyCustomState().apply {
            addMessage(MessageRole.USER, "What's 125 + 437 and the weather in San Francisco?")
        }

        val result = graph.run(initialState)
    }
}
// Simulated Workflow Demonstration
// 
// Input: "What's 125 + 437 and the weather in San Francisco?"
// 
// LLM Response in Llama3.2 Tool Call Format:
// [calculator_add(param1=125, param2=437), weather_tool(city="San Francisco")]
// 
// Tool Call Execution:
// 1. Calculator Tool
//    - Input: { param1: 125, param2: 437 }
//    - Output: 562
//    - Follow-up Prompt: "The calculation result is 562"
// 
// 2. Weather Tool
//    - Input: { city: "San Francisco" }
//    - Output: "65°F, Partly Cloudy, Humidity: 62%"
//    - Follow-up Prompt: "The current weather in San Francisco"
// 
// Final Aggregated Response:
// "Let me help you with that:
// 
// The result of 125 + 437 is 562.
// 
// In San Francisco, it's currently 65°F with partly cloudy skies. 
// The humidity is at 62%, making for a mild day.
// 
// Is there anything else I can help you with?"
// 
// Workflow Details:
// - Total Tool Calls: 2
```

## Customizing LLMwithTools
```kotlin
abstract class CustomLLMWithTools(
    adapter: BaseLLMToolAdapter, 
    private val customLLM: YourLanguageModel
) : LLMWithTools(adapter) {

    // Custom model initialization
    override suspend fun init_model() {
        // Implement specific model loading logic
        customLLM.prepare()
    }

    // Custom model cleanup
    override suspend fun close_model() {
        customLLM.shutdown()
    }

    // Custom message handling methods
    override fun addSystemMessage(content: String) {
        customLLM.setSystemContext(content)
    }

    override fun addUserMessage(content: String) {
        customLLM.appendUserMessage(content)
    }

    override fun addAssistantMessage(content: String) {
        customLLM.appendAssistantResponse(content)
    }

    // Custom response generation
    override fun getResponse(query: String?): Flow<String> {
        return if (query != null) {
            customLLM.generateResponse(query)
        } else {
            customLLM.continueGeneration()
        }
    }

    // Optional: Add custom tool-specific methods
    fun setCustomModelParameters(temperature: Float, topP: Float) {
        customLLM.updateGenerationParameters(temperature, topP)
    }
}

```

## Customizing LLM Adapter

```kotlin
class Llama3_2_3B_LLMToolAdapter : BaseLLMToolAdapter() {
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "    "
    }

    override fun toolSchemas(tools: List<BaseTool<*, *>>): String {
        val toolsArray = buildJsonArray {
            tools.forEach { tool ->
                val toolAnnotation = tool::class.findAnnotation<ToolAnnotation>() 
                    ?: throw IllegalStateException("Tool annotation not found")

                val parameterType = tool.getParameterType()

                if (parameterType != null) {
                    add(buildJsonObject {
                        put("name", toolAnnotation.name)
                        put("description", toolAnnotation.description)

                        putJsonObject("parameters") {
                            put("type", "dict")

                            val requiredProperties = mutableListOf<String>()
                            parameterType.memberProperties.forEach { property ->
                                val isNullable = property.returnType.isMarkedNullable
                                if (!isNullable) {
                                    requiredProperties.add(property.name)
                                }
                            }

                            put("required", buildJsonArray {
                                requiredProperties.forEach { 
                                    add(JsonPrimitive(it)) 
                                }
                            })

                            putJsonObject("properties") {
                                parameterType.memberProperties.forEach { property ->
                                    val propertyName = property.name
                                    val propertyType = getJsonType(property)

                                    putJsonObject(propertyName) {
                                        put("type", propertyType)
                                        put("description", "The $propertyName parameter")
                                    }
                                }
                            }
                        }

                        if (toolAnnotation.returnDescription.isNotEmpty()) {
                            put("returnDescription", toolAnnotation.returnDescription)
                        }
                    })
                }
            }
        }

        return json.encodeToString(toolsArray)
    }

    override fun parseResponse(response: String): StructuredLLMResponse {
        try {
            val trimmedResponse = response.trim()

            if (trimmedResponse.startsWith("[") && trimmedResponse.endsWith("]")) {
                val toolCalls = parseToolCalls(trimmedResponse)

                if (toolCalls.isNotEmpty()) {
                    return StructuredLLMResponse(
                        toolCalls = toolCalls,
                        metadata = ResponseMetadata(
                            tokenUsage = estimateTokenUsage(trimmedResponse),
                            finishReason = FinishReason.TOOL_CALLS.value
                        )
                    )
                }
            }

            return StructuredLLMResponse(
                content = response,
                metadata = ResponseMetadata(
                    tokenUsage = estimateTokenUsage(response),
                    finishReason = FinishReason.STOP.value
                )
            )
        } catch (e: Exception) {
            return StructuredLLMResponse(
                content = response,
                metadata = ResponseMetadata(
                    finishReason = FinishReason.ERROR.value
                )
            )
        }
    }

    private fun estimateTokenUsage(response: String): TokenUsage {
        val tokens = (response.length / 4) + 1
        return TokenUsage(
            completionTokens = tokens,
            totalTokens = tokens
        )
    }
}
```

## Appendix and Working Notes

This is just an working note.

### Jetpack Compose Integration

In `module/build.gradle.kts`:
```kotlin
plugins {
    .....
    alias(libs.plugins.kotlin.compose)  // https://developer.android.com/develop/ui/compose/compiler
    id("com.google.devtools.ksp")   // for @ComponentScan, or this may cause "org.koin.core.error.NoBeanDefFoundException: No definition found for type ChatViewModel"
    
}

android{
    ....
    buildFeatures {
        compose = true  // for jetpack compose
        buildConfig = true  //for define veriable
    }
    ....
}
....
// A compile-time verification mechanism for Koin Annotations, used to check Koin configuration correctness during the Kotlin Symbol Processing (KSP) phase
ksp {
    arg("KOIN_CONFIG_CHECK", "true")
}
....

dependencies {
    ....
    // Koin: dependency injection
    // for @Single @Module @ComponentScan
    libs.koin.annotations?.let { implementation(it) } ?: implementation("io.insert-koin:koin-annotations:1.3.1")
    ksp(libs.koin.ksp.compiler)?.let { implementation(it) } ?: implementation("io.insert-koin:koin-ksp-compiler:1.3.1")   // for @ComponentScan automate generate module
    libs.koin.android?.let { implementation(it) } ?: implementation("io.insert-koin:koin-android:3.5.6")
    libs.koin.androidx.compose?.let { implementation(it) } ?: implementation("io.insert-koin:koin-androidx-compose:3.5.6")
    libs.androidx.activity.compose?.let { implementation(it) } ?: implementation("androidx.activity:activity-compose:1.9.3")  // for rememberLauncherForActivityResult
    ....

    // for @Serialization and @Annotation
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.10")
    implementation("org.jetbrains.kotlin.plugin.serialization:org.jetbrains.kotlin.plugin.serialization.gradle.plugin:2.1.10")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("io.github.classgraph:classgraph:4.8.179")
}
```

## License

Apache License 2.0

## Contributing

1. Fork the repository
2. Create your feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request
