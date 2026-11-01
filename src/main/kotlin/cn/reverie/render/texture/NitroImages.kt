package cn.reverie.render.texture

import cn.reverie.render.nanovg.NanoVGImage

object NitroImages {

    private val glCache = HashMap<Int, NanoVGImage>()
    private val resourceCache = HashMap<String, NanoVGImage>()

    fun fromGLTexture(context: Long, glId: Int, width: Int, height: Int, flags: Int = 0): NanoVGImage? {
        if (glId <= 0) return null
        glCache[glId]?.let { return it }
        val image = NanoVGImage.createFromGLTexture(context, glId, width, height, flags)
        if (image.isValid) {
            glCache[glId] = image
            return image
        }
        return null
    }

    fun fromResource(context: Long, path: String): NanoVGImage? {
        resourceCache[path]?.let { return it }
        return try {
            val stream = NitroImages::class.java.getResourceAsStream(path) ?: return null
            val bytes = stream.readAllBytes()
            stream.close()
            val buffer = org.lwjgl.BufferUtils.createByteBuffer(bytes.size)
            buffer.put(bytes)
            buffer.flip()
            val image = NanoVGImage.createFromMemory(context, buffer)
            if (image != null && image.isValid) {
                resourceCache[path] = image
                image
            } else null
        } catch (e: Throwable) {
            null
        }
    }
}
