package com.tejeet.carddrop.ui.components

import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.tejeet.carddrop.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Decoded photos kept in memory (up to 1/8 of the heap), so reopening a card shows them instantly. */
private object ImageCache {
    private val cache = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.asAndroidBitmap().byteCount / 1024
    }

    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, bitmap: ImageBitmap) {
        cache.put(key, bitmap)
    }
}

/**
 * A photo from disk, decoded off the main thread. Returns null until it's ready (or if the file
 * can't be read), so screens open immediately instead of stalling on a multi-megapixel decode.
 */
@Composable
fun rememberImage(path: String, maxDimension: Int): ImageBitmap? {
    val key = "$path@$maxDimension"
    return produceState(initialValue = if (path.isBlank()) null else ImageCache.get(key), key) {
        if (path.isBlank() || value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { ImageUtils.decodeUprightSampledBitmap(File(path), maxDimension)?.asImageBitmap() }.getOrNull()
        }?.also { ImageCache.put(key, it) }
    }.value
}
