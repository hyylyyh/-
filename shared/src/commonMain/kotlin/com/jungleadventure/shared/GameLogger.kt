package com.jungleadventure.shared

import kotlin.math.max
import kotlin.math.min

object GameLogger {
    fun log(tag: String, message: String) {
        println("[日志][$tag] $message")
    }

    fun info(tag: String, message: String) {
        println("[信息][$tag] $message")
    }

    fun warn(tag: String, message: String) {
        println("[警告][$tag] $message")
    }

    fun error(tag: String, message: String) {
        println("[错误][$tag] $message")
    }

    fun error(tag: String, message: String, throwable: Throwable) {
        println("[错误][$tag] $message，异常=${throwable.message}")
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
