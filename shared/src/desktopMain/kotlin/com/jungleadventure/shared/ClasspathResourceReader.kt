package com.jungleadventure.shared

class ClasspathResourceReader : ResourceReader {
    override fun readText(path: String): String {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
            ?: error("资源不存在：$path")
        return stream.bufferedReader().use { it.readText() }
    }
}
