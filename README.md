# Smith Tool Call Module

SmithToolCall is a module that connects LLM tool call outputs directly to Android function calls. 
It translates AI-generated instructions into executable Android functions, enabling seamless integration between language models and Android applications.


## How to use

### Setup

#### Main Application
##### root/setting.gradle.kts
```kotlin
.....
include(":SmithToolCalls")
......
```

##### root/build.gradle.kts
```kotlin
plugins {
    .....
    id ("kotlinx-serialization")
    .....
}
.....
.....
dependencies {
    ....
    implementation(project(":SmithToolCalls"))
    // for @Serialization and @Annotation
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.1.10")
    implementation("org.jetbrains.kotlin.plugin.serialization:org.jetbrains.kotlin.plugin.serialization.gradle.plugin:2.1.10")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("io.github.classgraph:classgraph:4.8.179")
}
```
(Optional)
#####  root/Application.kt
```kotlin
class XXXApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@XXXApplication)
            modules(
                listOf(
                    KoinAppModule().module,
                    SmithToolCallsModule().module // 添加 Android module 的 Koin module
                )
            )
        }
        ...
        ...
    }
}
```

### Example 1
#### Tool Definiation
```kotlin

@Serializable
data class CalculatorInput(
    val param1: Int,
    val param2: Int
)

@ToolAnnotation(
    name = "calculator_add",
    description = "Add two numbers together",
    returnDescription = "The sum of the two numbers"
)
class CalculatorTool : BaseTool<CalculatorInput, Int>() {
    override suspend fun invoke(input: CalculatorInput): Int {
        val result = input.param1 + input.param2
        return result
    }
}

```

#### Usage:
```kotlin
val calculator = Calculator()
toolRegistry.register(calculator)
toolRegistry.setLLMToolAdapter(Llama3_2_3B_LLMToolAdapter())
........
........

val response = llm.query("what is 2+3") //[calculator_add(para1=2, param2=3)]
val processingResult = toolRegistry.processLLMResponse(response)
if (processingResult.toolResponses.isNotEmpty() && processingResult.requiresFollowUp) {
    val toolResponse = processingResult.toolResponses.first()
    .......
    ......
}
```

### Example 2:

#### Tool Definiation
```kotlin
import android.util.Log
import com.smith.lai.smithtoolcalls.tools.BaseTool
import com.smith.lai.smithtoolcalls.tools.ToolAnnotation
import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Serializable
data class UIBridgeInput(val command: String = "")

@ToolAnnotation(
    name = "screen_control",
    description = """
Manipulating screen with specific command.
acceptable command value:
    "chat_setting": open chat setting Screen
    "rag_setting": open Rag setting Screen


"""
)
class UIBridgeTool : BaseTool<UIBridgeInput, Unit>() {

    // 保存跳轉回調
    private var screenControlCallback: ((cmd:String) -> Unit)? = null

    fun setNavigateCallback(callback: (cmd:String) -> Unit) {
        screenControlCallback = callback
    }

    override suspend fun invoke(input: UIBridgeInput): Unit = withContext(Dispatchers.Main) {
        Log.d("UIBridgeTool","Executing command: ${input.command}")
        screenControlCallback?.invoke(input.command)
    }
}
```

```kotlin
@Serializable
data class RagSearchInput(val query: String = "")

@ToolAnnotation(
    name = "rag_serach",
    description = """
search data from rag database with specified query keyword
"""
)
class RagSearchTool(chunksDB: ChunksDB, sentenceEncoder: SentenceEmbeddingProvider) : BaseTool<RagSearchInput, String>() {
    val chunksDB: ChunksDB = chunksDB
    val sentenceEncoder:SentenceEmbeddingProvider = sentenceEncoder


    override suspend fun invoke(input: RagSearchInput): String = withContext(Dispatchers.Main) {
        val query = input.query
        Log.d("RagSearchTool","RAG Search: ${query}")
        val retrieveDuration = measureTimedValue {
            var jointContext = ""
            val retrievedContextList = ArrayList<RetrievedContext>()
            val queryEmbedding = sentenceEncoder.encodeText(query)
            chunksDB.getSimilarChunks(queryEmbedding)
                .forEach {
                    jointContext += "\n" + it.second.chunkData
                    retrievedContextList.add(
                        RetrievedContext(
                            it.second.docFileName,
                            it.second.chunkData
                        )
                    )
                }
            jointContext
        }
        return@withContext retrieveDuration.value
    }
}
```

#### ViewModel
``` kotlin
private val _navigationEvent = MutableSharedFlow<String>()
val navigationEvent = _navigationEvent.asSharedFlow()
val uiBridgeTool = UIBridgeTool()
val ragTool = RagSearchTool(chunksDB,sentenceEncoder)
uiBridgeTool.setNavigateCallback { cmd ->
    // Handle the navigation command
    when (cmd) {
        "chat_setting" ->
            viewModelScope.launch {
                showMoreOptionsPopup()
            }
        "rag_setting" ->
            viewModelScope.launch {
                _navigationEvent.emit(value = "rag_setting")
            }
        else -> Log.d("Navigation", "Unknown command: $cmd")
    }
}

toolRegistry.register(uiBridgeTool)
toolRegistry.register(ragTool)
toolRegistry.setLLMToolAdapter(Llama3_2_3B_LLMToolAdapter())
```

#### UI
```kotlin
LaunchedEffect(Unit) {
    viewModel.navigationEvent.collect { command ->
        when(command){
            "chat_setting" -> {
                // This is processed in viewModel
                //viewModel.showMoreOptionsPopup()
            }
            "rag_setting" -> {
                onRAGChatClick()
            }
            else -> {
                Log.e("viewModel.navigationEvent.collect", "invalid command: ${command}")
            }
        }

    }
}
```
output

## Customized LLM Adaptor : 



example: Llama3_2_3B_LLMToolAdapter

You can specify your own class by extends BaseLLMToolAdapter system prompt and parser here.



## Appendix and working Note

### Jetpack compose

__module/build.gradle.kts__
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
// Koin Annotations 的一個 編譯時驗證機制，用來在 KSP（Kotlin Symbol Processing）階段檢查 Koin 設定是否正確
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
....
```

