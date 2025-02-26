package com.smith.lai.smithtoolcalls.tool_calls.tools

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ToolAnnotation(
    val name: String,
    val description: String,
    val returnDescription: String = ""
)