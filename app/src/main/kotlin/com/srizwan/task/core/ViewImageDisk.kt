package com.srizwan.task.core

import android.app.Application
import java.io.File
import java.security.MessageDigest

/**
 * Disk cache of the View page images (the raw bytes downloaded from the GitHub repo), so a page that was opened once is
 * not downloaded again after the app restarts or after it was pushed out of the small memory cache.
 *
 * Folder layout: `cacheDir/view_pages/<hash of repo|branch|pagesDir>/<hash of image name>`. Every connection has its own folder,
 * so changing the repo / branch / folder never shows an old image. Files are written to a temporary name first and renamed
 * (a half-written file is never read). The whole cache is trimmed oldest-first (last read) when it grows over [MAX_BYTES].
 * Android may also clear `cacheDir` itself when the phone runs low on storage — this is only a cache.
 */
object ViewImageDisk {
    private const val MAX_BYTES = 256L * 1024 * 1024
    private const val TRIM_TO = 192L * 1024 * 1024
    private const val TRIM_EVERY = 12

    private var root: File? = null
    private var writes = 0

    fun init(app: Application) { root = File(app.cacheDir, "view_pages") }

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun dir(scope: String): File? = root?.let { File(it, sha(scope)) }
    private fun file(scope: String, name: String): File? = dir(scope)?.let { File(it, sha(name)) }

    /** The cached bytes of [name] under connection [scope], or null. Reading refreshes the file's "last used" time. */
    fun read(scope: String, name: String): ByteArray? = try {
        val f = file(scope, name)
        if (f != null && f.isFile && f.length() > 0L) f.readBytes().also { f.setLastModified(System.currentTimeMillis()) } else null
    } catch (_: Exception) { null }

    fun write(scope: String, name: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        try {
            val d = dir(scope) ?: return
            val f = file(scope, name) ?: return
            d.mkdirs()
            val tmp = File(d, f.name + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(f)) { f.delete(); if (!tmp.renameTo(f)) tmp.delete() }
            synchronized(this) { writes++; if (writes % TRIM_EVERY == 0) trim() }
        } catch (_: Exception) { }
    }

    /** One image (it could not be decoded, so the saved bytes are bad). */
    fun remove(scope: String, name: String) { try { file(scope, name)?.delete() } catch (_: Exception) { } }

    /** Every image of one connection (the reload button: the next open downloads fresh files). */
    fun clear(scope: String) { try { dir(scope)?.deleteRecursively() } catch (_: Exception) { } }

    /** Deletes the least recently used files until the cache is back under [TRIM_TO]. */
    fun trim() {
        try {
            val r = root ?: return
            val all = r.walkTopDown().filter { it.isFile }.toList()
            var total = all.sumOf { it.length() }
            if (total <= MAX_BYTES) return
            for (f in all.sortedBy { it.lastModified() }) {
                if (total <= TRIM_TO) break
                val n = f.length()
                if (f.delete()) total -= n
            }
        } catch (_: Exception) { }
    }
}
