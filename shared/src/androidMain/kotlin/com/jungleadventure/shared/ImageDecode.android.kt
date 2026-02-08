package com.jungleadventure.shared

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    return try {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bitmap == null) {
            GameLogger.warn("图片加载", "安卓端解码PNG失败：Bitmap为空，字节长度=${bytes.size}")
            null
        } else {
            bitmap.asImageBitmap()
        }
    } catch (e: Exception) {
        GameLogger.warn("图片加载", "安卓端解码PNG失败，字节长度=${bytes.size}", e)
        null
    }
}
