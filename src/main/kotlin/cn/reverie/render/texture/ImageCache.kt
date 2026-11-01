package cn.reverie.render.texture

import org.lwjgl.opengl.GL11C.glDeleteTextures
import java.util.LinkedHashMap

class ImageCache(val maxEntries: Int = 128) : AutoCloseable {

    private val cache = object : LinkedHashMap<String, TextureLoader.LoadedImage>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TextureLoader.LoadedImage>?): Boolean {
            if (size > maxEntries && eldest != null) {
                glDeleteTextures(eldest.value.textureId)
                return true
            }
            return false
        }
    }

    fun getOrLoad(key: String, loader: () -> TextureLoader.LoadedImage?): TextureLoader.LoadedImage? {
        synchronized(cache) {
            val cached = cache[key]
            if (cached != null) return cached

            val loaded = loader()
            if (loaded != null) {
                cache[key] = loaded
            }
            return loaded
        }
    }

    override fun close() {
        synchronized(cache) {
            cache.values.forEach { glDeleteTextures(it.textureId) }
            cache.clear()
        }
    }
}
