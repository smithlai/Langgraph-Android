package com.smith.lai.smithtoolcalls.tools

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ToolAnnotation(
    val name: String,
    val description: String,
    val returnDescription: String = ""
)