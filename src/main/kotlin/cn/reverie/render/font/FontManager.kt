package cn.reverie.render.font

import cn.reverie.render.nanovg.NanoVGFont
import org.lwjgl.nanovg.NanoVG.nvgCreateFontMem
import org.lwjgl.system.MemoryUtil
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer

class FontManager(val nvgContext: Long) : AutoCloseable {

    private val fonts = HashMap<String, NanoVGFont>()
    private val allocatedBuffers = ArrayList<ByteBuffer>()

    fun loadFontFromBytes(name: String, bytes: ByteArray): NanoVGFont? {
        if (bytes.isEmpty()) return null
        val buffer = MemoryUtil.memAlloc(bytes.size)
        buffer.put(bytes)
        buffer.flip()
        allocatedBuffers.add(buffer)

        val handle = nvgCreateFontMem(nvgContext, name, buffer, false)
        if (handle >= 0) {
            val font = NanoVGFont(nvgContext, name, handle)
            fonts[name] = font
            println("[Nocturne-Font] Successfully loaded font '$name' with handle $handle (size: ${bytes.size} bytes)")
            return font
        } else {
            System.err.println("[Nocturne-Font] nvgCreateFontMem failed for '$name', returned $handle")
            return null
        }
    }

    fun loadFontFromFile(name: String, file: File): NanoVGFont? {
        if (!file.exists() || !file.isFile) return null
        return try {
            val bytes = file.readBytes()
            loadFontFromBytes(name, bytes)
        } catch (e: Exception) {
            System.err.println("[Nocturne-Font] Error reading font file ${file.absolutePath}: ${e.message}")
            null
        }
    }

    fun loadFontFromStream(name: String, stream: InputStream): NanoVGFont? {
        return try {
            val bytes = stream.use { it.readAllBytes() }
            loadFontFromBytes(name, bytes)
        } catch (e: Exception) {
            System.err.println("[Nocturne-Font] Error reading font stream: ${e.message}")
            null
        }
    }

    fun getFont(name: String): NanoVGFont? = fonts[name]

    override fun close() {
        allocatedBuffers.forEach { MemoryUtil.memFree(it) }
        allocatedBuffers.clear()
        fonts.clear()
    }
}
