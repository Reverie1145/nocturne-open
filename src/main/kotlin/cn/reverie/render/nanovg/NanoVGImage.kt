package cn.reverie.render.nanovg

import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.nanovg.NanoVGGL3.nvglCreateImageFromHandle
import org.lwjgl.nanovg.NanoVGGL3.nvglImageHandle
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.MemoryStack
import java.nio.ByteBuffer

class NanoVGImage(
    val context: Long,
    val imageHandle: Int,
    val width: Int,
    val height: Int
) : AutoCloseable {

    val isValid: Boolean get() = imageHandle > 0

    override fun close() {
        if (isValid) {
            nvgDeleteImage(context, imageHandle)
        }
    }

    companion object {
        fun createFromMemory(context: Long, buffer: ByteBuffer, flags: Int = 0): NanoVGImage? {
            val handle = nvgCreateImageMem(context, flags, buffer)
            if (handle <= 0) return null

            var width = 0
            var height = 0
            MemoryStack.stackPush().use { stack ->
                val w = stack.mallocInt(1)
                val h = stack.mallocInt(1)
                nvgImageSize(context, handle, w, h)
                width = w.get(0)
                height = h.get(0)
            }
            return NanoVGImage(context, handle, width, height)
        }

        fun createFromGLTexture(context: Long, textureId: Int, width: Int, height: Int, flags: Int = 0): NanoVGImage {
            val handle = nvglCreateImageFromHandle(context, textureId, width, height, flags)
            return NanoVGImage(context, handle, width, height)
        }

        fun getGLTextureId(context: Long, imageHandle: Int): Int {
            return nvglImageHandle(context, imageHandle)
        }
    }
}
