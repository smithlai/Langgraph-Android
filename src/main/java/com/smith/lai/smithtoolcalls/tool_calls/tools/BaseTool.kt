package com.smith.lai.smithtoolcalls.tool_calls.tools

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

    private fun getParameterSchema(): JsonObject {
        val paramClass = getParameterType()

        // 處理 null 或 Unit 類型（Tool without parameter）
        if (paramClass == null || paramClass == Unit::class) {
            return buildJsonObject {
                put("type", "object")
                put("properties", JsonObject(emptyMap()))
                // 不包含 required 字段，因為沒有必要的參數
            }
        }

        if (paramClass.findAnnotation<Serializable>() == null) {
            throw IllegalStateException("Parameter class must be @Serializable")
        }

        return buildJsonObject {
            put("type", "object")

            val properties = paramClass.memberProperties
            put("required", buildJsonArray {
                properties.forEach { add(it.name) }
            })

            putJsonObject("properties") {
                properties.forEach { prop ->
                    putJsonObject(prop.name) {
                        put("type", getJsonType(prop))
                        put("description", getPropertyDescription(prop))
                    }
                }
            }
        }
    }

    open fun getReturnSchema(): JsonObject {
        val returnClass = getReturnType() ?: return JsonObject(emptyMap())

        val isPrimitiveOrString = returnClass in listOf(
            String::class, Int::class, Long::class,
            Float::class, Double::class, Boolean::class
        )

        if (!isPrimitiveOrString && returnClass.findAnnotation<Serializable>() == null) {
            throw IllegalStateException("Return class must be a primitive type, String, or @Serializable")
        }

        return buildJsonObject {
            put("type", when(returnClass) {
                Int::class, Long::class -> "integer"
                Float::class, Double::class -> "number"
                Boolean::class -> "boolean"
                String::class -> "string"
                else -> "object"
            })
        }
    }

    // 其他輔助方法保持不變
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
        return "Parameter: ${property.name}"
    }
    fun getToolAnnotation(): ToolAnnotation {
        val annotation = this::class.findAnnotation<ToolAnnotation>() ?:
        throw IllegalStateException("Tool annotation not found")
        return annotation
    }

    open fun getJsonSchema(): JsonObject {
        val annotation = getToolAnnotation()

        return buildJsonObject {
            put("name", annotation.name)
            put("description", annotation.description)
            put("parameters", getParameterSchema())
            put("returns", getReturnSchema())
            if (annotation.returnDescription.isNotEmpty()) {
                put("returnDescription", annotation.returnDescription)
            }
        }
    }
}