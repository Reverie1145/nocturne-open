package cn.reverie.render.texture

import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL30C.glGenerateMipmap
import org.lwjgl.stb.STBImage.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.io.InputStream
import java.nio.ByteBuffer

object TextureLoader {

    data class LoadedImage(val textureId: Int, val width: Int, val height: Int)

    fun loadFromStream(stream: InputStream, generateMipmaps: Boolean = true, forceWhite: Boolean = true): LoadedImage? {
        val bytes = stream.readAllBytes()
        val directBuffer = MemoryUtil.memAlloc(bytes.size)
        directBuffer.put(bytes)
        directBuffer.flip()

        return try {
            loadFromMemory(directBuffer, generateMipmaps, forceWhite)
        } finally {
            MemoryUtil.memFree(directBuffer)
        }
    }

    fun loadFromMemory(buffer: ByteBuffer, generateMipmaps: Boolean = true, forceWhite: Boolean = true): LoadedImage? {
        MemoryStack.stackPush().use { stack ->
            val w = stack.mallocInt(1)
            val h = stack.mallocInt(1)
            val comp = stack.mallocInt(1)

            stbi_set_flip_vertically_on_load(false)
            val pixels = stbi_load_from_memory(buffer, w, h, comp, 4) ?: return null

            val width = w.get(0)
            val height = h.get(0)

            if (forceWhite) {
                for (i in 0 until width * height) {
                    val a = pixels.get(i * 4 + 3).toInt() and 0xFF

                    if (a > 0) {

                        pixels.put(i * 4, 255.toByte())
                        pixels.put(i * 4 + 1, 255.toByte())
                        pixels.put(i * 4 + 2, 255.toByte())

                    } else {

                        pixels.put(i * 4, 0.toByte())
                        pixels.put(i * 4 + 1, 0.toByte())
                        pixels.put(i * 4 + 2, 0.toByte())
                        pixels.put(i * 4 + 3, 0.toByte())
                    }
                }
            }

            val textureId = glGenTextures()
            glBindTexture(GL_TEXTURE_2D, textureId)

            val minFilter = if (generateMipmaps) GL_LINEAR_MIPMAP_LINEAR else GL_LINEAR
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)

            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels)

            if (generateMipmaps) {
                glGenerateMipmap(GL_TEXTURE_2D)
            }

            stbi_image_free(pixels)
            glBindTexture(GL_TEXTURE_2D, 0)

            return LoadedImage(textureId, width, height)
        }
    }
}
