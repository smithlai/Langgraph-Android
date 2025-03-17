package com.smith.lai.smithtoolcalls.langgraph.tools

import com.smith.lai.smithtoolcalls.langgraph.response.ToolFollowUpMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties



abstract class BaseTool<TInput, TOutput> {
    abstract suspend fun invoke(input: TInput): TOutput
    open fun followup_prompt(response: String):String {
        return """
The tool returned: "$response". 
Based on this information, continue answering the request.
"""
    }
    open fun getFollowUpMetadata(response: TOutput): ToolFollowUpMetadata {
        // Modified default behavior to check if response is Unit, which typically indicates
        // a UI control or action that doesn't need follow-up
        return if (response is Unit) {
            ToolFollowUpMetadata(
                requiresFollowUp = false,
//                shouldTerminateFlow = false,
                customFollowUpPrompt = ""
            )
        } else {
            // Default behavior for other response types: requires follow-up
            ToolFollowUpMetadata(
                requiresFollowUp = true,
//                shouldTerminateFlow = false,
                customFollowUpPrompt = ""
            )
        }
    }

    fun getParameterType(): KClass<*>? {
        val parameterType = this::class.supertypes
            .firstOrNull()
            ?.arguments
            ?.firstOrNull()
            ?.type
        return parameterType?.classifier as? KClass<*>
    }

    fun getReturnType(): KClass<*>? {
        val returnType = this::class.supertypes
            .firstOrNull()
            ?.arguments
            ?.getOrNull(1)
            ?.type
        return returnType?.classifier as? KClass<*>
    }

    fun getToolAnnotation(): ToolAnnotation {
        val annotation = this::class.findAnnotation<ToolAnnotation>() ?:
        throw IllegalStateException("Tool annotation not found")
        return annotation
    }
}