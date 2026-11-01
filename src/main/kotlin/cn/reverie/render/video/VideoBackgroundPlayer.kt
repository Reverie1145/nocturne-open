package cn.reverie.render.video

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.framebuffer.FBO
import cn.reverie.render.framebuffer.FBOBlitter
import cn.reverie.render.gl.GLStateGuard
import cn.reverie.render.shader.blur.DualKawaseBlur
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.stb.STBImage.*
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
object VideoBackgroundPlayer {

    const val VIDEO_WIDTH = 1920
    const val VIDEO_HEIGHT = 1080
    private const val FRAME_BYTES = VIDEO_WIDTH * VIDEO_HEIGHT * 4

    internal const val BLUR_WIDTH = 960
    internal const val BLUR_HEIGHT = 540

    internal var glTextureId: Int = 0
    internal var nvgImageHandle: Int = 0

    internal var fallbackTextureId: Int = 0
    internal var fallbackImageHandle: Int = 0

    private var videoInputFbo: FBO? = null
    internal var videoBlurredFbo: FBO? = null
    internal var blurredImageHandle: Int = 0
    private var kawaseBlur: DualKawaseBlur? = null

    private var bufferA: ByteBuffer? = null
    private var bufferB: ByteBuffer? = null

    @Volatile
    private var frontBuffer: ByteBuffer? = null

    @Volatile
    private var backBuffer: ByteBuffer? = null

    private val hasNewFrame = AtomicBoolean(false)
    private val isRunning = AtomicBoolean(false)
    private val decoderGeneration = AtomicLong(0)
    @Volatile
    private var playbackRequested = false
    private var decoderThread: Thread? = null
    private var ffmpegProcess: Process? = null
    internal var decoderExecutable: String? = null
    internal var decoderVideoFile: File? = null

    var isInitialized = false
        private set

    var hasVideoLoaded = false
        private set

    @Volatile
    private var framesDecoded: Long = 0
    private var diagCounter: Long = 0

    fun videoTimeSeconds(): Float = ((framesDecoded % LOOP_FRAMES).toFloat() / 60f)

    const val LOOP_SECONDS = 24f
    const val LOOP_FRAMES = 1440L

    fun init() {
        if (isInitialized) return

        try {
            initFallbackTexture()
            initVideoTexture()
            prepareDecoder()
            isInitialized = true
            setPlaybackActive(true)
        } catch (e: Throwable) {
            System.err.println("[Nocturne-Video] Failed to initialize video player: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun initFallbackTexture() {
        if (fallbackTextureId > 0) return

        val fallbackFile = resolveAssetFile("video/background_fallback.png")
        var imgBuffer: ByteBuffer? = null
        var w = 0
        var h = 0

        if (fallbackFile != null && fallbackFile.exists()) {
            MemoryStack.stackPush().use { stack ->
                val comp = stack.mallocInt(1)
                val width = stack.mallocInt(1)
                val height = stack.mallocInt(1)
                imgBuffer = stbi_load(fallbackFile.absolutePath, width, height, comp, 4)
                if (imgBuffer != null) {
                    w = width.get(0)
                    h = height.get(0)
                }
            }
        }

        if (imgBuffer == null) {
            val s = VideoBackgroundPlayer::class.java.getResourceAsStream("/assets/reverie/video/background_fallback.png")
                ?: Thread.currentThread().contextClassLoader.getResourceAsStream("assets/reverie/video/background_fallback.png")
            if (s != null) {
                try {
                    val bytes = s.readBytes()
                    val directBuf = MemoryUtil.memAlloc(bytes.size)
                    try {
                        directBuf.put(bytes).flip()
                        MemoryStack.stackPush().use { stack ->
                            val comp = stack.mallocInt(1)
                            val width = stack.mallocInt(1)
                            val height = stack.mallocInt(1)
                            imgBuffer = stbi_load_from_memory(directBuf, width, height, comp, 4)
                            if (imgBuffer != null) {
                                w = width.get(0)
                                h = height.get(0)
                            }
                        }
                    } finally {
                        MemoryUtil.memFree(directBuf)
                    }
                } catch (ignored: Throwable) {}
            }
        }

        if (imgBuffer != null && w > 0 && h > 0) {
            fallbackTextureId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, fallbackTextureId)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, imgBuffer)
            glBindTexture(GL_TEXTURE_2D, 0)
            stbi_image_free(imgBuffer)
        }
    }

    private fun initVideoTexture() {
        if (glTextureId > 0) return

        glTextureId = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, glTextureId)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, VIDEO_WIDTH, VIDEO_HEIGHT, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L)
        glBindTexture(GL_TEXTURE_2D, 0)

        bufferA = MemoryUtil.memAlloc(FRAME_BYTES)
        bufferB = MemoryUtil.memAlloc(FRAME_BYTES)
        frontBuffer = bufferA
        backBuffer = bufferB
    }

    private fun resolveAssetFile(subPath: String): File? = resolveAssetFileImpl(subPath)
    private fun findFFmpeg(): String? = findFFmpegImpl()
    private fun prepareDecoder() = prepareDecoderImpl()
    /**
     * Keeps the last decoded texture available while a game world is open, but
     * stops FFmpeg so the menu's 1080p stream does not consume CPU in-game.
     */
    fun setPlaybackActive(active: Boolean) {
        synchronized(this) {
            if (!isInitialized) return

            if (playbackRequested != active) {
                playbackRequested = active
                decoderGeneration.incrementAndGet()
                isRunning.set(active)

                if (!active) {
                    try {
                        ffmpegProcess?.destroyForcibly()
                    } catch (_: Throwable) {}
                    decoderThread?.interrupt()
                    return
                }
            } else if (!active) {
                return
            }

            startDecoderLocked()
        }
    }

    private fun startDecoderLocked() {
        if (!playbackRequested || decoderThread?.isAlive == true) return

        val ffmpegPath = decoderExecutable ?: return
        val videoFile = decoderVideoFile ?: return
        val generation = decoderGeneration.get()

        decoderThread = Thread({
            val cmd = listOf(
                ffmpegPath,
                "-re",
                "-stream_loop", "-1",
                "-i", videoFile.absolutePath,
                "-f", "rawvideo",
                "-pix_fmt", "rgba",
                "-s", "${VIDEO_WIDTH}x${VIDEO_HEIGHT}",
                "-r", "60",
                "-an",
                "-v", "error",
                "-"
            )

            val targetFps = 60.0
            val frameIntervalNanos = (1_000_000_000.0 / targetFps).toLong()

            while (isPlaybackGenerationActive(generation)) {
                var process: Process? = null
                try {
                    val pb = ProcessBuilder(cmd)
                    pb.redirectError(ProcessBuilder.Redirect.DISCARD)
                    val child = pb.start()
                    process = child
                    synchronized(this) {
                        if (isPlaybackGenerationActive(generation)) {
                            ffmpegProcess = child
                        } else {
                            child.destroyForcibly()
                        }
                    }

                    val stream: InputStream = child.inputStream
                    val tempByteArray = ByteArray(65536)
                    var nextFrameTime = System.nanoTime()

                    while (isPlaybackGenerationActive(generation) && child.isAlive) {
                        val currentBack = backBuffer ?: break
                        currentBack.clear()

                        var totalRead = 0
                        while (totalRead < FRAME_BYTES && isPlaybackGenerationActive(generation)) {
                            val toRead = (FRAME_BYTES - totalRead).coerceAtMost(tempByteArray.size)
                            val bytesRead = stream.read(tempByteArray, 0, toRead)
                            if (bytesRead == -1) break
                            currentBack.put(tempByteArray, 0, bytesRead)
                            totalRead += bytesRead
                        }

                        if (totalRead == FRAME_BYTES) {
                            currentBack.flip()
                            synchronized(this) {
                                val temp = frontBuffer
                                frontBuffer = currentBack
                                backBuffer = temp
                                hasNewFrame.set(true)
                                hasVideoLoaded = true
                                framesDecoded++
                            }

                            nextFrameTime += frameIntervalNanos
                            val now = System.nanoTime()
                            val sleepNanos = nextFrameTime - now
                            if (sleepNanos > 0) {
                                val sleepMs = sleepNanos / 1_000_000L
                                val remNanos = (sleepNanos % 1_000_000L).toInt()
                                try {
                                    Thread.sleep(sleepMs, remNanos)
                                } catch (_: InterruptedException) {
                                    break
                                }
                            } else if (sleepNanos < -100_000_000L) {

                                nextFrameTime = System.nanoTime()
                            }
                        } else {
                            break
                        }
                    }

                    child.destroyForcibly()
                } catch (e: Throwable) {
                    if (!isPlaybackGenerationActive(generation)) break
                    try { Thread.sleep(500) } catch (_: Throwable) {}
                } finally {
                    process?.let {
                        try { it.destroyForcibly() } catch (_: Throwable) {}
                        synchronized(this) {
                            if (ffmpegProcess === it) ffmpegProcess = null
                        }
                    }
                }
            }
            synchronized(this) {
                if (decoderThread === Thread.currentThread()) decoderThread = null
            }
        }, "Nocturne-VideoDecoder").apply {
            isDaemon = true
            start()
        }
    }

    private fun isPlaybackGenerationActive(generation: Long): Boolean =
        playbackRequested && decoderGeneration.get() == generation

    private fun getOrCreateKawaseBlur(): DualKawaseBlur {
        var blur = kawaseBlur
        if (blur == null) {
            blur = DualKawaseBlur()
            kawaseBlur = blur
        }
        return blur
    }

    private fun ensureBlurFbos() {
        if (videoInputFbo == null || videoInputFbo?.width != BLUR_WIDTH || videoInputFbo?.height != BLUR_HEIGHT) {
            videoInputFbo?.close()
            videoInputFbo = FBO(BLUR_WIDTH, BLUR_HEIGHT, hasDepthStencil = false)
        }
        if (videoBlurredFbo == null || videoBlurredFbo?.width != BLUR_WIDTH || videoBlurredFbo?.height != BLUR_HEIGHT) {
            videoBlurredFbo?.close()
            videoBlurredFbo = FBO(BLUR_WIDTH, BLUR_HEIGHT, hasDepthStencil = false)
            val context = RenderEngine.nanoVG.handle
            if (context != 0L && blurredImageHandle > 0) {
                nvgDeleteImage(context, blurredImageHandle)
                blurredImageHandle = 0
            }
        }
    }

    fun updateFrame() {
        if (!isInitialized) init()

        var frameUpdated = false
        if (hasNewFrame.getAndSet(false)) {
            synchronized(this) {
                val buffer = frontBuffer
                if (buffer != null && glTextureId > 0) {
                    glBindTexture(GL_TEXTURE_2D, glTextureId)
                    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, VIDEO_WIDTH, VIDEO_HEIGHT, GL_RGBA, GL_UNSIGNED_BYTE, buffer)
                    glBindTexture(GL_TEXTURE_2D, 0)
                    frameUpdated = true
                }
            }
        }

        val activeTextureId = if (hasVideoLoaded && glTextureId > 0) glTextureId else fallbackTextureId
        if (activeTextureId <= 0) {
            diagCounter++
            if (diagCounter % 120L == 0L) {
                System.err.println("[Nocturne-Video] diag: no texture. decoded=$framesDecoded loaded=$hasVideoLoaded vid=$glTextureId fallback=$fallbackTextureId")
            }
            return
        }

        if (frameUpdated || videoBlurredFbo == null) {
            try {
                ensureBlurFbos()
                val inFbo = videoInputFbo
                val outFbo = videoBlurredFbo
                if (inFbo != null && outFbo != null) {
                    GLStateGuard.withStateGuard {
                        FBOBlitter.blitTexture(activeTextureId, inFbo)
                        getOrCreateKawaseBlur().process(inFbo, outFbo, iterations = 4, offset = 2.2f)
                    }
                }
            } catch (e: Throwable) {
                System.err.println("[Nocturne-Video] Failed to update video blur: $e")
            }
        }

        diagCounter++
        if (diagCounter % 120L == 0L) {
            println("[Nocturne-Video] diag: decoded=$framesDecoded loaded=$hasVideoLoaded tex=$glTextureId fallback=$fallbackTextureId frameUpdated=$frameUpdated blur=$videoBlurredFbo running=$isRunning")
        }
    }

    fun resolveCoverRect(screenW: Float, screenH: Float): FloatArray = resolveCoverRectImpl(screenW, screenH)

    fun render(screenW: Float, screenH: Float, alpha: Float = 1.0f) = renderImpl(screenW, screenH, alpha)

    /**
     * [zoom], [panX] and [panY] describe a transform applied to the backdrop (scale around the screen centre, then
     * a translation) so frosted surfaces can sample exactly what is behind them.
     */
    fun renderBlurredArea(
        x: Float, y: Float, w: Float, h: Float, radius: Float, screenW: Float, screenH: Float,
        alpha: Float = 1.0f, rotation: Float = 0f,
        zoom: Float = 1f, panX: Float = 0f, panY: Float = 0f
    ) = renderBlurredAreaImpl(x, y, w, h, radius, screenW, screenH, alpha, rotation, zoom, panX, panY)

    /**
     * Draws a window onto the footage: the point ([srcX], [srcY]) (0..1 of the frame) is centred in the rounded
     * rectangle at [zoom] times "cover" size, shifted by ([offX], [offY]) pixels for parallax.
     */
    fun renderPoster(
        x: Float, y: Float, w: Float, h: Float, radius: Float,
        srcX: Float, srcY: Float, zoom: Float, offX: Float, offY: Float,
        alpha: Float = 1f, blurred: Boolean = false
    ) = renderPosterImpl(x, y, w, h, radius, srcX, srcY, zoom, offX, offY, alpha, blurred)

    fun renderBlurredQuad(
        xs: FloatArray, ys: FloatArray, count: Int, screenW: Float, screenH: Float, alpha: Float = 1.0f
    ) = renderBlurredQuadImpl(xs, ys, count, screenW, screenH, alpha)
    fun destroy() {
        setPlaybackActive(false)
        val worker = decoderThread
        worker?.interrupt()
        try {
            worker?.join(1000L)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        val decoderStopped = worker?.isAlive != true
        synchronized(this) {
            if (decoderThread === worker && decoderStopped) decoderThread = null
        }

        val context = RenderEngine.nanoVG.handle
        if (context != 0L) {
            if (nvgImageHandle > 0) {
                nvgDeleteImage(context, nvgImageHandle)
                nvgImageHandle = 0
            }
            if (fallbackImageHandle > 0) {
                nvgDeleteImage(context, fallbackImageHandle)
                fallbackImageHandle = 0
            }
            if (blurredImageHandle > 0) {
                nvgDeleteImage(context, blurredImageHandle)
                blurredImageHandle = 0
            }
        }

        videoInputFbo?.close()
        videoBlurredFbo?.close()
        videoInputFbo = null
        videoBlurredFbo = null

        kawaseBlur?.close()
        kawaseBlur = null

        if (glTextureId > 0) {
            glDeleteTextures(glTextureId)
            glTextureId = 0
        }
        if (fallbackTextureId > 0) {
            glDeleteTextures(fallbackTextureId)
            fallbackTextureId = 0
        }

        if (decoderStopped) {
            bufferA?.let { MemoryUtil.memFree(it) }
            bufferB?.let { MemoryUtil.memFree(it) }
        } else {
            System.err.println("[Nocturne-Video] Decoder did not stop in time; retaining frame buffers until process exit.")
        }
        bufferA = null
        bufferB = null
        frontBuffer = null
        backBuffer = null

        isInitialized = false
        hasVideoLoaded = false
        playbackRequested = false
        decoderExecutable = null
        decoderVideoFile = null
    }
}
