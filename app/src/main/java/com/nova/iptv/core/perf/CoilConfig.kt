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
import java.io.File
import java.util.concurrent.TimeUnit

object CoilConfig {
    fun create(context: Context, okHttp: OkHttpClient): ImageLoader {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val heap = am.memoryClass * 1024L * 1024L
        val memCache = (heap * 0.15).toLong().coerceIn(8L * 1024 * 1024, 64L * 1024 * 1024)
        val rgb565 = LowRam.useRgb565()
        return ImageLoader.Builder(context)
            .okHttpClient(
                okHttp.newBuilder()
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
                    .maxSizeBytes(80L * 1024L * 1024L)
                    .build()
            }
            .crossfade(true)
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
