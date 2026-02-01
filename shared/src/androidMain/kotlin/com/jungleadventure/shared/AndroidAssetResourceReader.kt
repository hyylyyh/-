package com.jungleadventure.shared

import android.content.Context

class AndroidAssetResourceReader(private val context: Context) : ResourceReader {
    override fun readText(path: String): String {
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }
}
