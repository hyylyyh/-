package com.jungleadventure.shared

import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    return try {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (e: Exception) {
        GameLogger.warn("图片加载", "桌面端解码PNG失败，字节长度=${bytes.size}", e)
        null
    }
}
