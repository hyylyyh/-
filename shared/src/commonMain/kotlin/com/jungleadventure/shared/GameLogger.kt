package com.jungleadventure.shared

import kotlin.math.max
import kotlin.math.min

object GameLogger {
    fun log(tag: String, message: String) {
        println("[LOG][$tag] $message")
    }

    fun info(tag: String, message: String) {
        println("[INFO][$tag] $message")
    }

    fun warn(tag: String, message: String) {
        println("[WARN][$tag] $message")
    }

    fun error(tag: String, message: String) {
        println("[ERROR][$tag] $message")
    }

    fun error(tag: String, message: String, throwable: Throwable) {
        println("[ERROR][$tag] $message，异常=${throwable.message}")
    }

    fun logBlock(tag: String, title: String, lines: List<String>) {
        log(tag, title)
        lines.forEach { line ->
            log(tag, "- $line")
        }
    }

    fun clampInt(value: Int, min: Int, max: Int): Int {
        return max(min, min(value, max))
    }
}
