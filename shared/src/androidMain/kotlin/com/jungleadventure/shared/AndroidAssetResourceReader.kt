package com.jungleadventure.shared

import android.content.Context

class AndroidAssetResourceReader(private val context: Context) : ResourceReader {
    override fun readText(path: String): String {
        return readBytes(path).decodeToString()
    }

    override fun readBytes(path: String): ByteArray {
        return context.assets.open(path).use { it.readBytes() }
    }
}
