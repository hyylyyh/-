package com.jungleadventure.shared

class ClasspathResourceReader : ResourceReader {
    override fun readText(path: String): String {
        return readBytes(path).decodeToString()
    }

    override fun readBytes(path: String): ByteArray {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
            ?: error("资源不存在：$path")
        return stream.use { it.readBytes() }
    }
}
