package com.jungleadventure.shared.loot

actual fun loadResourceText(path: String): String? {
    val stream = object {}.javaClass.getResourceAsStream("/$path") ?: return null
    return stream.bufferedReader().use { it.readText() }
}
