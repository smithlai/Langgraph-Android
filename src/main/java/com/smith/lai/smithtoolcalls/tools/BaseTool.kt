package com.smith.lai.smithtoolcalls.tools

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

    open fun getFollowUpMetadata(response: TOutput): ToolFollowUpMetadata {
        // 預設行為：需要後續處理
        return ToolFollowUpMetadata(
            requiresFollowUp = true,
            shouldTerminateFlow = false,
            customFollowUpPrompt = ""
        )
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