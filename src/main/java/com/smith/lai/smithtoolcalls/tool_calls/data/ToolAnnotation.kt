package com.smith.lai.smithtoolcalls.tool_calls.data

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ToolAnnotation(val name: String, val description: String)
