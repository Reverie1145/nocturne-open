package cn.reverie.app.music.render

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.CornerRadii
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.draw.RoundedRect
import cn.reverie.render.font.IconFont
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGImage
import cn.reverie.render.nanovg.NanoVGPaint
import cn.reverie.render.nanovg.NanoVGRenderer
import org.lwjgl.BufferUtils
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.system.MemoryStack
import java.io.InputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * High-performance asynchronous image cache for CloudMusic covers, avatars, and QR codes.
 * Decodes PNG/JPEG into NanoVG textures on the render thread.
 */
object CloudMusicImageCache {

    private sealed interface State {
        data object Loading : State
        data class Ready(val bytes: ByteArray) : State
        data object Failed : State
    }

    private val states = ConcurrentHashMap<String, State>()

    private val textureCache = object : LinkedHashMap<String, NanoVGImage>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, NanoVGImage>?): Boolean {
            if (size > 128) {
                eldest?.value?.close()
                eldest?.key?.let { states.remove(it) }
                return true
            }
            return false
        }
    }

    fun putBytes(key: String, bytes: ByteArray) {
        states[key] = State.Ready(bytes)
    }

    fun invalidate(key: String) {
        synchronized(textureCache) {
            textureCache.remove(key)?.close()
        }
        states.remove(key)
    }

    fun drawCover(
        url: String?,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float = 6f,
        alpha: Float = 1f,
        fallbackText: String = "♫"
    ) {
        if (url.isNullOrBlank()) {
            drawPlaceholder(x, y, w, h, radius, alpha, fallbackText)
            return
        }

        // Optimize NetEase image size request if param not already present
        val optimizedUrl = if (url.contains("music.126.net") && !url.contains("?param=")) {
            val size = (w.coerceAtLeast(h) * 2f).toInt().coerceIn(64, 400)
            "$url?param=${size}y${size}"
        } else {
            url
        }

        val image = getImage(optimizedUrl)
        if (image != null && image.isValid) {
            MemoryStack.stackPush().use { stack ->
                val paint = NVGPaint.malloc(stack)
                NanoVGPaint.imagePattern(
                    RenderEngine.nanoVG.handle,
                    x, y, w, h, 0f,
                    image.imageHandle,
                    alpha.coerceIn(0f, 1f),
                    paint
                )
                RoundedRect.draw(RenderEngine.nanoVG.handle, x, y, w, h, CornerRadii(radius), paint)
            }
        } else {
            drawPlaceholder(x, y, w, h, radius, alpha, fallbackText)
        }
    }

    fun drawBytesImage(
        key: String,
        bytes: ByteArray,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float = 6f,
        alpha: Float = 1f
    ) {
        putBytes(key, bytes)
        val image = getImage(key)
        if (image != null && image.isValid) {
            MemoryStack.stackPush().use { stack ->
                val paint = NVGPaint.malloc(stack)
                NanoVGPaint.imagePattern(
                    RenderEngine.nanoVG.handle,
                    x, y, w, h, 0f,
                    image.imageHandle,
                    alpha.coerceIn(0f, 1f),
                    paint
                )
                RoundedRect.draw(RenderEngine.nanoVG.handle, x, y, w, h, CornerRadii(radius), paint)
            }
        } else {
            drawPlaceholder(x, y, w, h, radius, alpha, "...")
        }
    }

    private fun getImage(url: String): NanoVGImage? {
        synchronized(textureCache) {
            textureCache[url]?.let { return it }
        }

        when (val state = states[url]) {
            null -> {
                if (states.putIfAbsent(url, State.Loading) == null) {
                    thread(name = "CloudMusic-Img-Fetch", isDaemon = true) {
                        try {
                            val conn = URI(url).toURL().openConnection()
                            conn.connectTimeout = 8000
                            conn.readTimeout = 12000
                            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                            val bytes = conn.getInputStream().use { it.readAllBytes() }
                            states[url] = if (bytes.size > 32) State.Ready(bytes) else State.Failed
                        } catch (e: Exception) {
                            states[url] = State.Failed
                        }
                    }
                }
            }
            is State.Ready -> {
                val buffer = BufferUtils.createByteBuffer(state.bytes.size)
                buffer.put(state.bytes).flip()
                val decoded = try {
                    NanoVGImage.createFromMemory(RenderEngine.nanoVG.handle, buffer)
                } catch (t: Throwable) {
                    null
                }
                if (decoded != null && decoded.isValid) {
                    synchronized(textureCache) {
                        textureCache[url] = decoded
                    }
                    return decoded
                }
                states[url] = State.Failed
            }
            State.Loading, State.Failed -> Unit
        }
        return null
    }

    private fun drawPlaceholder(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        radius: Float,
        alpha: Float,
        text: String
    ) {
        Draw2D.roundedRect(x, y, w, h, radius, NanoVGColor(0.12f, 0.12f, 0.16f, 0.85f * alpha))
        Draw2D.roundedOutline(x, y, w, h, radius, NanoVGColor(1f, 1f, 1f, 0.08f * alpha), 1.0f)
        NanoVGRenderer.drawCenteredString(
            text,
            x + w * 0.5f,
            y + h * 0.5f,
            NanoVGColor(0.6f, 0.6f, 0.7f, 0.6f * alpha),
            (w * 0.35f).coerceIn(12f, 28f)
        )
    }

    fun clear() {
        synchronized(textureCache) {
            textureCache.values.forEach { it.close() }
            textureCache.clear()
        }
        states.clear()
    }
}
