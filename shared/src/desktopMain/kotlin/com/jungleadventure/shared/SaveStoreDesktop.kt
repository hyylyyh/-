package com.jungleadventure.shared

import java.io.File

private class FileSaveStore : SaveStore {
    private val baseDir: File = File(System.getProperty("user.home"), ".jungle_adventure/saves")

    override fun save(slot: Int, data: String) {
        require(slot in 1..8) { "Slot must be 1..8" }
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        val file = File(baseDir, "slot$slot.json")
        GameLogger.info("SaveSystem", "Desktop 写入存档：${file.absolutePath}")
        file.writeText(data, Charsets.UTF_8)
    }

    override fun load(slot: Int): String? {
        require(slot in 1..8) { "Slot must be 1..8" }
        val file = File(baseDir, "slot$slot.json")
        GameLogger.info("SaveSystem", "Desktop 读取存档：${file.absolutePath}")
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }
}

actual fun defaultSaveStore(): SaveStore = FileSaveStore()
