package com.smith.lai.smithtoolcalls.smollm

import android.content.Context
import com.smith.lai.smithtoolcalls.ToolRegistry
import io.shubham0204.smollm.SmolLM
//
///**
// * LLM模型工廠，負責創建和配置SmolLM實例
// */
//class SmolLMFactory(private val context: Context) {
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
//    suspend fun createModelWithTools(
//        modelPath: String,
//        toolRegistry: ToolRegistry,
//        minP: Float = 0.05f,
//        temperature: Float = 0.7f,
//        contextSize: Long = 4096
//    ): SmolLMWithTools {
//        val smolLM = createModel(
//            modelPath = modelPath,
//            minP = minP,
//            temperature = temperature,
//            contextSize = contextSize
//        )
//
//        val smolLMWithTools = SmolLMWithTools(smolLM, toolRegistry)
//        smolLMWithTools.setupSystemPrompt()
//
//        return smolLMWithTools
//    }
//}