package cn.reverie.render.video

import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.opengl.GL11.*
import org.lwjgl.stb.STBImage.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
    internal fun VideoBackgroundPlayer.resolveAssetFileImpl(subPath: String): File? {
        val candidates = listOf(
            File("C:/Users/24033/Desktop/Nocturne/apps/client/src/main/resources/assets/reverie/$subPath"),
            File("src/main/resources/assets/reverie/$subPath"),
            File("../src/main/resources/assets/reverie/$subPath"),
            File("../../src/main/resources/assets/reverie/$subPath"),
            File("Nocturne/src/main/resources/assets/reverie/$subPath"),
            File("build/resources/main/assets/reverie/$subPath"),
            File("../build/resources/main/assets/reverie/$subPath"),
            File("assets/reverie/$subPath"),
            File("../assets/reverie/$subPath")
        )
        for (candidate in candidates) {
            if (candidate.exists() && candidate.isFile && candidate.length() > 0L) {
                return candidate.canonicalFile
            }
        }

        try {
            val resourceUrl = VideoBackgroundPlayer::class.java.getResource("/assets/reverie/$subPath")
                ?: Thread.currentThread().contextClassLoader.getResource("assets/reverie/$subPath")
            if (resourceUrl != null) {
                val conn = resourceUrl.openConnection()
                val expectedSize = conn.contentLengthLong

                val tempDir = File(System.getProperty("user.dir"), ".nocturne/cache").apply { mkdirs() }
                val target = File(tempDir, File(subPath).name)
                if (!target.exists() || target.length() == 0L || (expectedSize > 0L && target.length() != expectedSize)) {
                    conn.getInputStream().use { input ->
                        Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                }
                if (target.exists() && target.length() > 0L) {
                    return target
                }
            }
        } catch (ignored: Throwable) {}

        return null
    }

    internal fun VideoBackgroundPlayer.findFFmpegImpl(): String? {
        val candidates = mutableListOf("ffmpeg", "ffmpeg.exe")
        val localAppData = System.getenv("LOCALAPPDATA")
        if (localAppData != null) {
            val wingetDir = File(localAppData, "Microsoft/WinGet/Packages")
            if (wingetDir.exists()) {
                wingetDir.walkTopDown().maxDepth(4)
                    .filter { it.isFile && it.name.equals("ffmpeg.exe", ignoreCase = true) }
                    .firstOrNull()?.let { candidates.add(0, it.absolutePath) }
            }
        }
        for (candidate in candidates) {
            try {
                val p = ProcessBuilder(candidate, "-version").start()
                if (p.waitFor() == 0) {
                    return candidate
                }
            } catch (ignored: Throwable) {}
        }
        return null
    }

    internal fun VideoBackgroundPlayer.prepareDecoderImpl() {
        val videoFile = resolveAssetFileImpl("video/background.mp4")
        if (videoFile == null || !videoFile.exists()) {
            System.err.println("[Nocturne-Video] background.mp4 not found on disk!")
            return
        }
        println("[Nocturne-Video] Playing background video from: ${videoFile.absolutePath} (size: ${videoFile.length()} bytes)")

        val ffmpegPath = findFFmpegImpl()
        if (ffmpegPath == null) {
            System.err.println("[Nocturne-Video] ffmpeg executable not found, using fallback poster.")
            return
        }

        decoderVideoFile = videoFile
        decoderExecutable = ffmpegPath
    }
