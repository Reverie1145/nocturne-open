package cn.reverie.render.art

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.CornerRadii
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGImage
import org.lwjgl.BufferUtils
import org.lwjgl.nanovg.NanoVG.nvgCreateImageMem
import org.lwjgl.nanovg.NanoVG.nvgDeleteImage
import org.lwjgl.nanovg.NanoVG.nvgImagePattern
import org.lwjgl.nanovg.NanoVG.nvgImageSize
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.system.MemoryStack
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val NVG_IMAGE_GENERATE_MIPMAPS = 0x10

object AnimeArt {

    val MENU_STANDING_POOL = listOf(
        "wan_14",
        "yaga_11",
        "wan_33",
        "yaga_26",
        "wan_16",
        "yaga_19",
        "yaga_37",
        "wan_43",
        "yaga_40"
    )

    data class PanelArt(val id: String, val caption: String, val isScene: Boolean)

    val PANEL_FOOTER_ART = mapOf(
        "Combat" to PanelArt("scene/wan_05", "RENAKO × MAI", true),
        "Movement" to PanelArt("wan_15", "RENAKO", false),
        "Render" to PanelArt("scene/yaga_17", "YUU", true),
        "Player" to PanelArt("wan_14", "MAI", false),
        "World" to PanelArt("scene/yaga_05", "TOUKO × YUU", true),
        "Misc" to PanelArt("wan_10", "KAHO", false)
    )

    const val TOAST_AVATAR_INFO = "wan_10"
    const val TOAST_AVATAR_SUCCESS = "wan_39"
    const val TOAST_AVATAR_WARNING = "wan_37"
    const val TOAST_AVATAR_ERROR = "wan_23"

    val TARGET_PORTRAIT_POOL = listOf(
        "wan_36", "wan_26", "wan_41", "wan_21",
        "yaga_17", "yaga_16", "yaga_26", "yaga_21"
    )

    const val EMPTY_SEARCH_ART = "wan_37"

    const val WATERMARK_AVATAR = "wan_09"
    const val SESSION_AVATAR = "wan_42"
    const val MENU_CHIBI = "wan_07"
    const val GUI_CHIBI_RIGHT = "wan_06"
    const val GUI_CHIBI_LEFT = "wan_08"
    val ACCOUNT_AVATARS = listOf("wan_06", "wan_08", "wan_10", "wan_42", "yaga_12", "yaga_29")

    private val handleCache = HashMap<String, NanoVGImage>()
    private val missing = HashSet<String>()

    fun image(id: String): NanoVGImage? {
        handleCache[id]?.let { return if (it.isValid) it else null }
        if (id in missing) return null

        val path = assetPath(id) ?: run { missing.add(id); return null }
        val context = RenderEngine.nanoVG.handle
        if (context == 0L) return null

        return try {
            val bytes = javaClass.getResourceAsStream(path)?.use { it.readAllBytes() }
            if (bytes == null) {
                missing.add(id)
                return null
            }
            val buffer: ByteBuffer = BufferUtils.createByteBuffer(bytes.size)
            buffer.put(bytes)
            buffer.flip()
            val handle = nvgCreateImageMem(context, NVG_IMAGE_GENERATE_MIPMAPS, buffer)
            if (handle <= 0) {
                missing.add(id)
                return null
            }
            MemoryStack.stackPush().use { stack ->
                val w = stack.mallocInt(1)
                val h = stack.mallocInt(1)
                nvgImageSize(context, handle, w, h)
                val img = NanoVGImage(context, handle, w.get(0), h.get(0))
                handleCache[id] = img
                img
            }
        } catch (t: Throwable) {
            missing.add(id)
            null
        }
    }

    private fun assetPath(id: String): String? {
        val scene = id.startsWith("scene/")
        val name = if (scene) id.removePrefix("scene/") else id
        if (!name.matches(Regex("[a-z]+_\\d{2}"))) return null
        val ext = if (scene) "jpg" else "png"
        return "/assets/reverie/anime/${if (scene) "scene" else "cut"}/$name.$ext"
    }

    fun releaseAll() {
        val context = RenderEngine.nanoVG.handle
        for (img in handleCache.values) {
            if (context != 0L && img.isValid) nvgDeleteImage(context, img.imageHandle)
        }
        handleCache.clear()
    }

    fun drawFitted(
        id: String,
        boxX: Float, boxY: Float, boxW: Float, boxH: Float,
        alpha: Float = 1f,
        alignBottom: Boolean = false
    ): Pair<Float, Float> {
        val img = image(id) ?: return 0f to 0f
        if (img.width <= 0 || img.height <= 0 || boxW <= 0f || boxH <= 0f) return 0f to 0f
        val scale = min(boxW / img.width, boxH / img.height)
        val w = img.width * scale
        val h = img.height * scale
        val x = boxX + (boxW - w) * 0.5f
        val y = if (alignBottom) boxY + boxH - h else boxY + (boxH - h) * 0.5f
        rawDraw(img, x, y, w, h, alpha)
        return w to h
    }

    fun drawStanding(
        id: String,
        anchorX: Float, baseY: Float, height: Float,
        alpha: Float = 1f
    ): Pair<Float, Float> {
        val img = image(id) ?: return 0f to 0f
        if (img.height <= 0 || height <= 0f) return 0f to 0f
        val w = img.width * (height / img.height)
        rawDraw(img, anchorX - w * 0.5f, baseY - height, w, height, alpha)
        return w to height
    }

    fun drawCover(
        id: String,
        x: Float, y: Float, w: Float, h: Float,
        alpha: Float = 1f,
        radius: Float = 0f,
        zoom: Float = 1f,
        biasY: Float = 0f
    ) {
        val img = image(id) ?: return
        if (img.width <= 0 || img.height <= 0 || w <= 0f || h <= 0f) return
        val coverScale = max(w / img.width, h / img.height) * max(1f, zoom)
        val drawW = img.width * coverScale
        val drawH = img.height * coverScale
        val dx = x + (w - drawW) * 0.5f
        val bias = biasY.coerceIn(-1f, 1f)
        val dy = y + (h - drawH) * (0.5f + bias * 0.5f)
        maskedDraw(img, x, y, w, h, if (radius > 0.01f) radius else 0f, dx, dy, drawW, drawH, alpha)
    }

    fun drawAvatar(
        id: String,
        x: Float, y: Float, size: Float,
        alpha: Float = 1f,
        ringAlpha: Float = 0.12f,
        zoom: Float = 1.35f,
        biasY: Float = -0.35f
    ) {
        val img = image(id) ?: return
        if (img.width <= 0 || img.height <= 0 || size <= 0f) return
        val coverScale = max(size / img.width, size / img.height) * max(1f, zoom)
        val drawW = img.width * coverScale
        val drawH = img.height * coverScale
        val dx = x + (size - drawW) * 0.5f
        val bias = biasY.coerceIn(-1f, 1f)
        val dy = y + (size - drawH) * (0.5f + bias * 0.5f)
        maskedDraw(img, x, y, size, size, size * 0.5f, dx, dy, drawW, drawH, alpha)

        if (ringAlpha > 0.01f) {
            val nvg = RenderEngine.nanoVG
            if (nvg.handle != 0L) {
                nvg.path.begin()
                nvg.path.circle(x + size * 0.5f, y + size * 0.5f, size * 0.5f - 0.5f)
                nvg.path.stroke(NanoVGColor(1f, 1f, 1f, ringAlpha * alpha.coerceIn(0f, 1f)), 0.75f)
            }
        }
    }

    private fun rawDraw(img: NanoVGImage, x: Float, y: Float, w: Float, h: Float, alpha: Float) {
        val context = RenderEngine.nanoVG.handle
        if (context == 0L) return
        MemoryStack.stackPush().use { stack ->
            val paint = NVGPaint.malloc(stack)
            nvgImagePattern(context, x, y, w, h, 0f, img.imageHandle, alpha.coerceIn(0f, 1f), paint)
            val path = RenderEngine.nanoVG.path
            path.begin()
            path.rect(x, y, w, h)
            path.fill(paint)
        }
    }

    private fun maskedDraw(
        img: NanoVGImage,
        x: Float, y: Float, w: Float, h: Float, radius: Float,
        dx: Float, dy: Float, drawW: Float, drawH: Float, alpha: Float
    ) {
        val context = RenderEngine.nanoVG.handle
        if (context == 0L) return
        MemoryStack.stackPush().use { stack ->
            val paint = NVGPaint.malloc(stack)
            nvgImagePattern(context, dx, dy, drawW, drawH, 0f, img.imageHandle, alpha.coerceIn(0f, 1f), paint)
            val path = RenderEngine.nanoVG.path
            path.begin()
            if (radius > 0.01f) {
                path.roundedRect(x, y, w, h, radius)
            } else {
                path.rect(x, y, w, h)
            }
            path.fill(paint)
        }
    }

    fun dailyPick(pool: List<String>, salt: Int = 0): String {
        if (pool.isEmpty()) return ""
        val day = (System.currentTimeMillis() / 86_400_000L).toInt() + salt
        return pool[Math.floorMod(day, pool.size)]
    }

    fun pickFor(pool: List<String>, seed: String): String {
        if (pool.isEmpty()) return ""
        var h = 1125899906842597L
        for (c in seed) h = 31 * h + c.code
        return pool[Math.floorMod((h xor (h ushr 32)).toInt(), pool.size)]
    }

    fun idleBob(timeMs: Long, periodMs: Float = 3400f): Float =
        sin((timeMs % periodMs.toLong().coerceAtLeast(1)) / periodMs * 2f * Math.PI.toFloat())
}
