package cn.reverie.app.music.player

import net.fabricmc.loader.api.FabricLoader
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread

/**
 * File cache manager for CloudMusic audio files.
 */
object CloudMusicCache {

    private val cacheDir: Path by lazy {
        val base = FabricLoader.getInstance().gameDir.resolve("cloud_music_cache")
        if (!Files.exists(base)) Files.createDirectories(base)
        base
    }

    private const val MAX_CACHE_BYTES = 512L * 1024L * 1024L // 512MB
    private const val PRUNE_TARGET_BYTES = 384L * 1024L * 1024L // prune to 384MB

    fun getCachedFile(trackId: Long): File? {
        val extensions = listOf("mp3", "flac", "m4a")
        for (ext in extensions) {
            val file = cacheDir.resolve("$trackId.$ext").toFile()
            if (file.exists() && file.length() > 4096) {
                file.setLastModified(System.currentTimeMillis())
                return file
            }
        }
        return null
    }

    fun downloadToFile(trackId: Long, url: String): File? {
        val ext = when {
            url.contains(".flac", ignoreCase = true) -> "flac"
            url.contains(".m4a", ignoreCase = true) -> "m4a"
            else -> "mp3"
        }
        val target = cacheDir.resolve("$trackId.$ext").toFile()
        val temp = cacheDir.resolve("$trackId.tmp").toFile()

        try {
            val conn = URI(url).toURL().openConnection()
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")

            conn.getInputStream().use { input ->
                BufferedInputStream(input).use { bis ->
                    FileOutputStream(temp).use { fos ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (bis.read(buffer).also { read = it } != -1) {
                            fos.write(buffer, 0, read)
                        }
                    }
                }
            }

            if (temp.exists() && temp.length() > 4096) {
                if (target.exists()) target.delete()
                temp.renameTo(target)
                target.setLastModified(System.currentTimeMillis())
                checkAndPruneCacheAsync()
                return target
            } else {
                temp.delete()
                return null
            }
        } catch (e: Exception) {
            temp.delete()
            System.err.println("[CloudMusicCache] Error downloading song $trackId: ${e.message}")
            return null
        }
    }

    private fun checkAndPruneCacheAsync() {
        thread(name = "CloudMusic-Cache-Pruner", isDaemon = true) {
            try {
                val files = cacheDir.toFile().listFiles() ?: return@thread
                var totalSize = files.sumOf { it.length() }
                if (totalSize > MAX_CACHE_BYTES) {
                    val sorted = files.sortedBy { it.lastModified() }
                    for (file in sorted) {
                        val len = file.length()
                        if (file.delete()) {
                            totalSize -= len
                            if (totalSize <= PRUNE_TARGET_BYTES) break
                        }
                    }
                }
            } catch (ignored: Exception) {}
        }
    }
}
