package com.smith.lai.smithtoolcalls.langgraph.node

import com.smith.lai.smithtoolcalls.langgraph.Message
import com.smith.lai.smithtoolcalls.tools.ProcessingResult
import com.smith.lai.smithtoolcalls.tools.ToolResponse

/**
 * Base State interface for LangGraph
 */
interface BaseState {
    val completed: Boolean
    val error: String?
    fun incrementStep(): BaseState
}

/**
 * StateGraph is the main state implementation for graph workflows
 */
data class StateGraph(
    val query: String = "",
    val messages: MutableList<Message> = mutableListOf(),
    val toolResponses: MutableList<ToolResponse<*>> = mutableListOf(),
    val processingResult: ProcessingResult? = null,
    val finalResponse: String = "",
    override val completed: Boolean = false,
    override val error: String? = null,
    val stepCount: Int = 0,
    val maxSteps: Int = 50,
    val startTime: Long = System.currentTimeMillis()
) : BaseState {
    override fun incrementStep(): StateGraph {
        return this.copy(stepCount = this.stepCount + 1)
    }

    fun maxStepsReached(): Boolean {
        return stepCount >= maxSteps
    }

    fun executionDuration(): Long {
        return System.currentTimeMillis() - startTime
    }
}