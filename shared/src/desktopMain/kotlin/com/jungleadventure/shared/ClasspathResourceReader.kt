package com.jungleadventure.shared

class ClasspathResourceReader : ResourceReader {
    override fun readText(path: String): String {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
            ?: error("Missing resource: $path")
        return stream.bufferedReader().use { it.readText() }
    }
}
