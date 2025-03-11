package com.smith.lai.smithtoolcalls.custom_data

import com.smith.lai.smithtoolcalls.langgraph.model.LLMWithTools
import com.smith.lai.smithtoolcalls.langgraph.model.adapter.BaseLLMToolAdapter
import io.shubham0204.smollm.SmolLM
import kotlinx.coroutines.flow.Flow

class SmolLMWithTools(adapter: BaseLLMToolAdapter, val smolLM: SmolLM): LLMWithTools(adapter) {

    override suspend fun init_model() {
    }

    override suspend fun close_model() {
        smolLM.close()
    }

    override fun addSystemMessage(content: String) {
        smolLM.addSystemPrompt(content)
    }

    override fun addUserMessage(content: String) {
        smolLM.addUserMessage(content)
    }

    override fun addAssistantMessage(content: String) {
        smolLM.addAssistantMessage(content)
    }

    override fun addToolMessage(content: String) {
        smolLM.addUserMessage(content)
    }

    override fun getResponse(query: String?): Flow<String> {
        return smolLM.getResponse()
    }

}