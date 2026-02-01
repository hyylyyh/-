package com.jungleadventure.shared

import android.content.Context
import java.io.File

object AndroidSaveStoreHolder {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        GameLogger.info("存档系统", "安卓存档目录初始化完成")
    }

    fun contextOrNull(): Context? = appContext
}

private class AndroidFileSaveStore(
    private val context: Context
) : SaveStore {
    private val baseDir: File = File(context.filesDir, "jungle_adventure/saves")

    override fun save(slot: Int, data: String) {
        require(slot in 1..8) { "存档槽位必须在 1..8" }
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        val file = File(baseDir, "slot$slot.json")
        GameLogger.info("存档系统", "安卓写入存档：${file.absolutePath}")
        file.writeText(data, Charsets.UTF_8)
    }

    override fun load(slot: Int): String? {
        require(slot in 1..8) { "存档槽位必须在 1..8" }
        val file = File(baseDir, "slot$slot.json")
        GameLogger.info("存档系统", "安卓读取存档：${file.absolutePath}")
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }
}

private object MemorySaveStore : SaveStore {
    private val slots = mutableMapOf<Int, String>()

    override fun save(slot: Int, data: String) {
        require(slot in 1..8) { "存档槽位必须在 1..8" }
        GameLogger.warn("存档系统", "安卓未初始化上下文，使用内存存档，槽位=$slot")
        slots[slot] = data
    }

    override fun load(slot: Int): String? {
        require(slot in 1..8) { "存档槽位必须在 1..8" }
        GameLogger.warn("存档系统", "安卓未初始化上下文，使用内存读档，槽位=$slot")
        return slots[slot]
    }
}

actual fun defaultSaveStore(): SaveStore {
    val context = AndroidSaveStoreHolder.contextOrNull()
    return if (context != null) {
        AndroidFileSaveStore(context)
    } else {
        MemorySaveStore
    }
}
