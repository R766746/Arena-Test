package com.nova.iptv.core.perf

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient
import okhttp3.Dispatcher
import java.io.File
import java.util.concurrent.TimeUnit

object CoilConfig {
    fun create(context: Context, okHttp: OkHttpClient): ImageLoader {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val heap = am.memoryClass * 1024L * 1024L
        val memFraction = if (LowRam.isLowRam) 0.06 else 0.15
        val memCache = (heap * memFraction).toLong().coerceIn(6L * 1024 * 1024, 64L * 1024 * 1024)
        val diskCache = if (LowRam.isLowRam) 128L else 256L
        val rgb565 = LowRam.useRgb565()
        val imageDispatcher = Dispatcher().apply {
            // A rapidly moving TV focus can expose dozens of lazy-grid items at
            // once. Keep image I/O behind rendering/input instead of allowing an
            // unbounded decode/download wave to saturate small TV devices.
            maxRequests = if (LowRam.isLowRam) 3 else 6
            maxRequestsPerHost = if (LowRam.isLowRam) 2 else 4
        }
        return ImageLoader.Builder(context)
            .okHttpClient(
                okHttp.newBuilder()
                    .dispatcher(imageDispatcher)
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(12, TimeUnit.SECONDS)
                    .build(),
            )
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizeBytes(memCache.toInt())
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "coil"))
                    .maxSizeBytes(diskCache * 1024L * 1024L)
                    .build()
            }
            .crossfade(!LowRam.isLowRam)
            .respectCacheHeaders(false)
            .bitmapConfig(if (rgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    fun onTrimMemory(loader: ImageLoader, level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) {
            loader.memoryCache?.clear()
        }
    }

    fun pause(loader: ImageLoader) {
        // Coil 2 has no global pause; drop the memory cache when backgrounded.
        loader.memoryCache?.clear()
    }
}
