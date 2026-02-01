package com.jungleadventure.shared.loot

import android.content.res.AssetManager

private var assetManager: AssetManager? = null

fun setAndroidAssetManager(manager: AssetManager) {
    assetManager = manager
}

actual fun loadResourceText(path: String): String? {
    val manager = assetManager ?: return null
    return try {
        manager.open(path).bufferedReader().use { it.readText() }
    } catch (ex: Exception) {
        null
    }
}
