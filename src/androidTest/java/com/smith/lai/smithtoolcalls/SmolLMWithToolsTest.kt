package com.smith.lai.smithtoolcalls

import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smith.lai.smithtoolcalls.smollm.SmolLMWithTools
import com.smith.lai.smithtoolcalls.tool_calls.tools.ToolResponseType
import com.smith.lai.smithtoolcalls.tool_calls.tools.example_tools.CalculatorTool
import io.shubham0204.smollm.SmolLM
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(AndroidJUnit4::class)
class SmolLMWithToolsTest {
    companion object{
        val TOOL_PACKAGE= "com.smith.lai.smithtoolcalls.tool_calls.tools.example_tools"
        val MODEL_PATH = "/data/local/tmp/llm/Llama-3.2-3B-Instruct-uncensored-Q4_K_M.gguf"

    }
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    val package_name ="${appContext.packageName}"

    val toolRegistry = ToolRegistry()
    private var smolLM: SmolLM = SmolLM()


    @get:Rule
    val testName = TestName()

    @Before
    fun setup(){
        println("vvvvvvvv Running test: ${testName.methodName} vvvvvvvv")
        toolRegistry.register(CalculatorTool::class)
        println("tools: ${toolRegistry.getToolNames().joinToString(",")}")
        runBlocking {
            // clear resources occupied by the previous model
            smolLM.close()
            val file = File(MODEL_PATH)
            print("${file.path} exists? ${file.exists()}\n")
            try {
                smolLM.create(
                    file.path,
                    0.1f,
                    0.0f,
                    false,
                    2048
                )
                println("Model loaded")
            } catch (e: Exception) {
                Log.e("loadModel", e.message.toString())

            }
        }
    }
    @After
    fun clear(){
        toolRegistry.clear()
        smolLM.close()
        println("====== ${testName.methodName} End ======")
    }
//    @Test
    fun test_001_CreateBasicModel() = runBlocking {

        smolLM.addUserMessage("Hello, how are you?")
        val response = StringBuilder()
        smolLM.getResponse("").collect {
            response.append(it)
        }

        Log.d("${testName.methodName}", "Model Response: $response")
        assertFalse("回應不應為空", response.isEmpty())

    }

    @Test
    fun test_002_CreateModelWithTools() = runBlocking {

        // 創建帶工具的模型
        val smolLMWithTools = SmolLMWithTools(
            smolLM = smolLM,
            toolRegistry = toolRegistry
        )


        // 測試計算器工具
        val query = "what is 123 + 456?"
        Log.d("${testName.methodName}", "Query: $query")
        val response = StringBuilder()

        smolLMWithTools.getResponseWithTools(query).collect {
            response.append(it)
        }

        Log.d("${testName.methodName}", "Tooled Response: $response")

        // 驗證回應中包含計算結果
        val containsAnswer = response.contains("579") || response.contains(
            "\"output\":579")
        assertTrue("回應應該包含計算結果", containsAnswer)
    }

//    @Test
//    fun test_003_MultiTurnToolConversation() = runBlocking {
//        // 創建帶工具的模型
//        val smolLMWithTools = factory.createModelWithTools(
//            modelPath = modelPath,
//            toolRegistry = toolRegistry,
//            temperature = 0.3f
//        )
//
//        try {
//            // 測試多輪對話
//            val responses = mutableListOf<String>()
//
//            // 第一輪：詢問計算
//            smolLMWithTools.chat("Can you calculate 50 + 25?", maxToolCalls = 2).collect {
//                responses.add(it)
//                Log.d("${testName.methodName}", "Turn 1: $it")
//            }
//
//            // 第二輪：詢問另一個計算
//            smolLMWithTools.chat("And what about 100 - 30?", maxToolCalls = 2).collect {
//                responses.add(it)
//                Log.d("${testName.methodName}", "Turn 2: $it")
//            }
//
//            // 檢查回應
//            val containsFirstAnswer = responses.any { it.contains("75") }
//            val containsSecondAnswer = responses.any { it.contains("70") }
//
//            assertTrue("應該含有第一個答案 (75)", containsFirstAnswer)
//            assertTrue("應該含有第二個答案 (70)", containsSecondAnswer)
//        } finally {
//            // 釋放資源
//            smolLMWithTools.smolLM.close()
//        }
//    }
//
//    @Test
//    fun test_004_FactoryWithCustomSettings() = runBlocking {
//        // 測試使用自定義設置創建模型
//        val customFactory = SmolLMFactory(context)
//
//        // 創建具有自定義系統提示的模型
//        val customSmolLMWithTools = customFactory.createModelWithTools(
//            modelPath = modelPath,
//            toolRegistry = toolRegistry,
//            temperature = 0.8f, // 高溫度，更隨機的回應
//            minP = 0.01f, // 低 minP，更多樣的詞彙選擇
//            contextSize = 8192 // 更大的上下文窗口
//        )
//
//        try {
//            // 設置自定義系統提示
//            customSmolLMWithTools.setupSystemPrompt(
//                "You are a helpful calculator assistant. Always use the calculator tool for math problems."
//            )
//
//            // 執行測試查詢
//            val response = StringBuilder()
//            customSmolLMWithTools.getResponseWithTools("what is 987 * 654?").collect {
//                response.append(it)
//            }
//
//            Log.d("${testName.methodName}", "Custom Factory Response: $response")
//
//            // 應該使用計算器工具
//            val expectedResult = 987 * 654
//            val containsResult = response.contains(expectedResult.toString())
//            assertTrue("回應應該包含計算結果 $expectedResult", containsResult)
//        } finally {
//            // 釋放資源
//            customSmolLMWithTools.smolLM.close()
//        }
//    }
//
//    /**
//     * 額外測試：測試工廠在建立多個模型時的行為
//     */
//    @Test
//    fun test_005_MultipleModelsCreation() = runBlocking {
//        val models = mutableListOf<SmolLM>()
//
//        try {
//            // 創建多個模型
//            repeat(3) { index ->
//                val model = factory.createModel(
//                    modelPath = modelPath,
//                    temperature = 0.5f + (index * 0.1f)
//                )
//                models.add(model)
//
//                // 確認模型可用
//                model.addUserMessage("Hello, this is model $index")
//                val response = StringBuilder()
//                model.getResponse("").collect {
//                    response.append(it)
//                }
//
//                Log.d("${testName.methodName}", "Model $index Response: $response")
//                assertFalse("模型 $index 的回應不應為空", response.isEmpty())
//            }
//
//            // 驗證創建了正確數量的模型
//            assertEquals("應該創建3個模型", 3, models.size)
//        } finally {
//            // 釋放所有模型資源
//            models.forEach { it.close() }
//        }
//    }
//}

///**
// * 為了支持測試，擴展 SmolLMFactory 以提供更靈活的方法
// */
//class SmolLMFactory {
//    suspend fun createModel(
//        modelPath: String,
//        minP: Float = 0.05f,
//        temperature: Float = 0.7f,
//        contextSize: Long = 4096,
//        storeChats: Boolean = true
//    ): SmolLM {
//        val smolLM = SmolLM()
//        val success = smolLM.create(
//            modelPath = modelPath,
//            minP = minP,
//            temperature = temperature,
//            storeChats = storeChats,
//            contextSize = contextSize
//        )
//
//        if (!success) {
//            throw IllegalStateException("Failed to load model at $modelPath")
//        }
//
//        return smolLM
//    }
//
//
//
//    suspend fun createModelWithTools(
//        modelPath: String,
//        toolRegistry: ToolRegistry,
//        minP: Float = 0.05f,
//        temperature: Float = 0.7f,
//        contextSize: Long = 4096
//    ): SmolLMWithTools {
//        val smolLM = runBlocking {
//            createModel(
//                modelPath = modelPath,
//                minP = minP,
//                temperature = temperature,
//                contextSize = contextSize
//            )
//        }
//        return createModelWithTools(smolLM, toolRegistry)
//    }
//
//    suspend fun createModelWithTools(
//        smolLM: SmolLM,
//        toolRegistry: ToolRegistry,
//    ): SmolLMWithTools {
//        val smolLMWithTools = createModelWithTools(smolLM, toolRegistry)
//        smolLMWithTools.setupSystemPrompt()
//
//        return smolLMWithTools
//    }
}