package com.srizwan.task.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.srizwan.task.core.MotionLevel
import com.srizwan.task.core.MotionStore
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Book cover images (Task grid, admin Books list): one shared Coil loader for the whole app ([RizwanApp] returns it from
 * `newImageLoader`).
 *  - memory cache: 25% of the app's memory budget (covers stay while scrolling back and forth, and between tabs)
 *  - disk cache: 200 MB in `cacheDir/covers` (covers survive an app restart, no new download)
 *  - the server's `Cache-Control` / `Expires` / `Pragma` / `Vary` headers are replaced by "keep for 7 days": many hosts send
 *    `no-cache` for images, which made every cover download again each time. A changed cover usually has a new link; the same
 *    link with a new picture shows after 7 days (or after clearing the app's cache).
 */
object CoverImages {
    private const val KEEP_SECONDS = 7 * 24 * 3600
    private const val DISK_BYTES = 200L * 1024 * 1024

    fun loader(context: PlatformContext): ImageLoader {
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor(Interceptor { chain ->
                chain.proceed(chain.request()).newBuilder()
                    .removeHeader("Pragma").removeHeader("Expires").removeHeader("Vary")
                    .header("Cache-Control", "public, max-age=$KEEP_SECONDS")
                    .build()
            })
            .build()
        return ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
            .diskCache { DiskCache.Builder().directory(File(context.cacheDir, "covers").toOkioPath()).maxSizeBytes(DISK_BYTES).build() }
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .build()
    }
}

/** A cover picture filling its box (`ContentScale.Crop`). [onError] is called when it cannot be loaded (the caller shows a book icon). */
@Composable
fun CoverImage(url: String, modifier: Modifier = Modifier, onError: () -> Unit = {}) {
    val ctx = LocalContext.current
    val still = MotionStore.level == MotionLevel.OFF
    val req = remember(url, still) {
        ImageRequest.Builder(ctx).data(url).apply { if (!still) crossfade(160) }.build()
    }
    AsyncImage(
        model = req, contentDescription = null, contentScale = ContentScale.Crop,
        onError = { onError() }, modifier = modifier
    )
}
