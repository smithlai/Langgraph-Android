package com.smith.lai.smithtoolcalls.tool_calls.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties



abstract class BaseTool<T> {
    abstract suspend fun invoke(input: T): String

    fun getParameterType(): KClass<*>? {
        val parameterType = this::class.supertypes
            .firstOrNull()
            ?.arguments
            ?.firstOrNull()
            ?.type
        return parameterType?.classifier as? KClass<*>
    }

    open fun getParameterSchema(): JsonObject {
        val paramClass = getParameterType() ?: return JsonObject(emptyMap())

        // Verify the class is serializable
        if (paramClass.findAnnotation<Serializable>() == null) {
            throw IllegalStateException("Parameter class must be @Serializable")
        }

        return buildJsonObject {
            put("type", "object")

            // Get required properties (all properties for now, could be customized with annotations)
            val properties = paramClass.memberProperties
            put("required", buildJsonArray {
                properties.forEach { add(it.name) }
            })

            // Build properties schema
            putJsonObject("properties") {
                properties.forEach { prop ->
                    putJsonObject(prop.name) {
                        put("type", getJsonType(prop))
                        // Could add property description from KDoc or custom annotation
                        put("description", getPropertyDescription(prop))
                    }
                }
            }
        }
    }

    private fun getJsonType(property: KProperty1<*, *>): String {
        val classifier = property.returnType.classifier
        return when (classifier){
            Int::class, Long::class -> "integer"
            Float::class, Double::class -> "number"
            Boolean::class -> "boolean"
            String::class -> "string"
            List::class -> "array"
            Map::class -> "object"
            else -> when {
                property.returnType.toString().contains("List") -> "array"
                property.returnType.toString().contains("Map") -> "object"
                else -> "object"
            }
        }
    }

    private fun getPropertyDescription(property: KProperty1<*, *>): String {
        // Could be enhanced to read from KDoc or custom property annotations
        return "Parameter: ${property.name}"
    }

    open fun getJsonSchema(): JsonObject {
        val annotation = this::class.findAnnotation<Tool>() ?:
        throw IllegalStateException("Tool annotation not found")

        return buildJsonObject {
            put("name", annotation.name)
            put("description", annotation.description)
            put("parameters", getParameterSchema())
            if (annotation.returnDescription.isNotEmpty()) {
                put("returnDescription", annotation.returnDescription)
            }
        }
    }

}