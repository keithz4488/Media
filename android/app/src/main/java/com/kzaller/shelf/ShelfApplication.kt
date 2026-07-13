package com.kzaller.shelf

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * App entry point. Configures Coil with a large, persistent cover cache: a big on-disk cache so
 * the many covers in a large library survive app restarts and load instantly (and offline) after
 * the first view, and respectCacheHeaders(false) so covers from CDNs that send no-cache headers
 * still get cached.
 */
class ShelfApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .crossfade(true)
            .respectCacheHeaders(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("cover_cache"))
                    .maxSizeBytes(250L * 1024 * 1024) // 250 MB — plenty for thousands of covers
                    .build()
            }
            .build()
}
