package cn.reverie.render.font

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.nanovg.NanoVGColor
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.system.MemoryStack
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * NanoVG adapter for Minecraft's own ASCII bitmap font. Non-ASCII text uses Nocturne's
 * CJK-capable default font so player/server-provided text remains readable.
 */
internal object MinecraftVanillaBitmapFont {
    private data class PixelRun(val row: Int, val start: Int, val endExclusive: Int)
    private data class Glyph(val advance: Int, val runs: List<PixelRun>)
    private data class Atlas(val cellWidth: Int, val cellHeight: Int, val glyphs: Map<Int, Glyph>)

    @Volatile
    private var cachedAtlas: Atlas? = null

    fun measureWidth(text: String, size: Float): Float {
        val atlas = atlas() ?: return fallbackWidth(text, size)
        val scale = size / atlas.cellHeight.coerceAtLeast(1)
        var width = 0f
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            offset += Character.charCount(codePoint)
            val glyph = atlas.glyphs[codePoint]
            width += if (glyph != null) {
                glyph.advance * scale
            } else {
                fallbackWidth(String(Character.toChars(codePoint)), size)
            }
        }
        return width
    }

    fun draw(context: Long, text: String, x: Float, y: Float, color: NanoVGColor, size: Float): Float {
        val atlas = atlas() ?: return drawFallback(text, x, y, color, size)
        val scale = size / atlas.cellHeight.coerceAtLeast(1)
        var penX = x
        var hasPixels = false

        fun flushPixels() {
            if (!hasPixels) return
            MemoryStack.stackPush().use { stack ->
                val nvgColor = org.lwjgl.nanovg.NVGColor.malloc(stack)
                color.toNVG(nvgColor)
                nvgFillColor(context, nvgColor)
                nvgFill(context)
            }
            hasPixels = false
        }

        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            offset += Character.charCount(codePoint)
            val glyph = atlas.glyphs[codePoint]
            if (glyph == null) {
                flushPixels()
                val fallbackText = String(Character.toChars(codePoint))
                drawFallback(fallbackText, penX, y, color, size)
                penX += fallbackWidth(fallbackText, size)
                continue
            }

            if (!hasPixels) {
                nvgBeginPath(context)
                hasPixels = true
            }
            for (run in glyph.runs) {
                nvgRect(
                    context,
                    penX + run.start * scale,
                    y + run.row * scale,
                    (run.endExclusive - run.start) * scale,
                    scale
                )
            }
            penX += glyph.advance * scale
        }
        flushPixels()
        return penX - x
    }

    private fun atlas(): Atlas? {
        cachedAtlas?.let { return it }
        return try {
            val id = Identifier.fromNamespaceAndPath("minecraft", "textures/font/ascii.png")
            val resource = Minecraft.getInstance().resourceManager.getResource(id).orElse(null) ?: return null
            val image: BufferedImage = resource.open().use(ImageIO::read) ?: return null
            if (image.width % 16 != 0 || image.height % 16 != 0) return null

            val cellWidth = image.width / 16
            val cellHeight = image.height / 16
            val glyphs = HashMap<Int, Glyph>()
            for (codePoint in 0x20..0x7e) {
                val sourceX = (codePoint and 0x0f) * cellWidth
                val sourceY = (codePoint ushr 4) * cellHeight
                val visible = Array(cellHeight) { BooleanArray(cellWidth) }
                var actualWidth = 0
                for (py in 0 until cellHeight) {
                    for (px in 0 until cellWidth) {
                        val argb = image.getRGB(sourceX + px, sourceY + py)
                        val alpha = argb ushr 24 and 0xff
                        visible[py][px] = alpha > 0 && (argb and 0x00ffffff) != 0
                        if (visible[py][px]) actualWidth = maxOf(actualWidth, px + 1)
                    }
                }

                val runs = ArrayList<PixelRun>()
                for (py in 0 until cellHeight) {
                    var px = 0
                    while (px < cellWidth) {
                        if (!visible[py][px]) {
                            px++
                            continue
                        }
                        val start = px
                        while (px < cellWidth && visible[py][px]) px++
                        runs += PixelRun(py, start, px)
                    }
                }

                // The vanilla font supplies the space glyph separately via include/space.json.
                val advance = if (codePoint == 0x20) 4 else actualWidth + 1
                glyphs[codePoint] = Glyph(advance, runs)
            }

            Atlas(cellWidth, cellHeight, glyphs).also { cachedAtlas = it }
        } catch (_: Throwable) {
            null
        }
    }

    private fun fallbackWidth(text: String, size: Float): Float =
        (RenderEngine.defaultFont ?: RenderEngine.fontRenderer.defaultFont).measureWidth(text, size)

    private fun drawFallback(text: String, x: Float, y: Float, color: NanoVGColor, size: Float): Float =
        (RenderEngine.defaultFont ?: RenderEngine.fontRenderer.defaultFont).drawText(text, x, y, color, size)
}
