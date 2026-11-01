package cn.reverie.render.nanovg

import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.system.MemoryStack
import java.nio.ByteBuffer

class NanoVGFont(
    val context: Long,
    val name: String,
    val handle: Int,
    private val isMinecraftBitmap: Boolean = false
) {
    fun setSize(size: Float): NanoVGFont {
        if (!isMinecraftBitmap) nvgFontSize(context, size)
        return this
    }

    fun setBlur(blur: Float): NanoVGFont {
        if (!isMinecraftBitmap) nvgFontBlur(context, blur)
        return this
    }

    fun setLetterSpacing(spacing: Float): NanoVGFont {
        if (!isMinecraftBitmap) nvgTextLetterSpacing(context, spacing)
        return this
    }

    fun setLineHeight(height: Float): NanoVGFont {
        if (!isMinecraftBitmap) nvgTextLineHeight(context, height)
        return this
    }

    fun setAlign(align: Int): NanoVGFont {
        if (!isMinecraftBitmap) nvgTextAlign(context, align)
        return this
    }

    fun prepare(size: Float, align: Int = NanoVGConstants.ALIGN_LEFT or NanoVGConstants.ALIGN_TOP): NanoVGFont {
        if (isMinecraftBitmap) return this
        if (handle >= 0) nvgFontFaceId(context, handle)
        nvgFontFace(context, name)
        nvgFontSize(context, size)
        nvgTextAlign(context, align)
        return this
    }

    fun drawText(text: String, x: Float, y: Float, color: NanoVGColor, size: Float? = null): Float {
        if (text.isEmpty()) return 0f
        if (isMinecraftBitmap) return cn.reverie.render.font.MinecraftVanillaBitmapFont.draw(context, text, x, y, color, size ?: 14f)
        if (size != null) nvgFontSize(context, size)
        if (handle >= 0) nvgFontFaceId(context, handle)
        nvgFontFace(context, name)
        MemoryStack.stackPush().use { stack ->
            val c = NVGColor.malloc(stack)
            color.toNVG(c)
            nvgFillColor(context, c)
            return nvgText(context, x, y, text)
        }
    }

    fun measureWidth(text: String, size: Float? = null): Float {
        if (text.isEmpty()) return 0f
        if (isMinecraftBitmap) return cn.reverie.render.font.MinecraftVanillaBitmapFont.measureWidth(text, size ?: 14f)
        if (size != null) nvgFontSize(context, size)
        if (handle >= 0) nvgFontFaceId(context, handle)
        nvgFontFace(context, name)
        MemoryStack.stackPush().use { stack ->
            val bounds = stack.mallocFloat(4)
            return nvgTextBounds(context, 0f, 0f, text, bounds)
        }
    }

    fun measureHeight(size: Float): Float {
        if (isMinecraftBitmap) return size
        nvgFontSize(context, size)
        if (handle >= 0) nvgFontFaceId(context, handle)
        nvgFontFace(context, name)
        MemoryStack.stackPush().use { stack ->
            val asc = stack.mallocFloat(1)
            val desc = stack.mallocFloat(1)
            val lineh = stack.mallocFloat(1)
            nvgTextMetrics(context, asc, desc, lineh)
            return lineh.get(0)
        }
    }

    fun measureMetrics(size: Float): FloatArray {
        nvgFontSize(context, size)
        if (handle >= 0) nvgFontFaceId(context, handle)
        nvgFontFace(context, name)
        MemoryStack.stackPush().use { stack ->
            val asc = stack.mallocFloat(1)
            val desc = stack.mallocFloat(1)
            val lineh = stack.mallocFloat(1)
            nvgTextMetrics(context, asc, desc, lineh)
            return floatArrayOf(asc.get(0), desc.get(0), lineh.get(0))
        }
    }

    companion object {
        fun createFromMemory(context: Long, name: String, data: ByteBuffer): NanoVGFont? {
            val handle = nvgCreateFontMem(context, name, data, false)
            return if (handle >= 0) NanoVGFont(context, name, handle) else null
        }
    }
}
