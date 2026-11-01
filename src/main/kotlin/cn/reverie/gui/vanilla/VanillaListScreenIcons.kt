package cn.reverie.gui.vanilla

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.CornerRadii
import cn.reverie.render.draw.RoundedRect
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGImage
import cn.reverie.render.nanovg.NanoVGPaint
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.render.texture.TextureLoader
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.system.MemoryStack
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
internal fun VanillaListScreenRenderer.drawIcon(image: NanoVGImage?, x: Float, y: Float, size: Float, alpha: Float) {
        if (image != null && image.isValid) {
            val context = RenderEngine.nanoVG.handle
            MemoryStack.stackPush().use { stack ->
                val paint = NVGPaint.malloc(stack)
                NanoVGPaint.imagePattern(context, x, y, size, size, 0f, image.imageHandle, alpha.coerceIn(0f, 1f), paint)
                RoundedRect.draw(context, x, y, size, size, CornerRadii(size * 0.2f), paint)
            }
        } else {
            val r = size * 0.2f
            NanoVGRenderer.drawRoundedRect(x, y, size, size, r, NanoVGColor(1f, 1f, 1f, 0.05f * alpha))
            NanoVGRenderer.drawRoundedOutline(x, y, size, size, r, NanoVGColor(1f, 1f, 1f, 0.12f * alpha), 1f)
            NanoVGRenderer.drawCenteredString("?", x + size * 0.5f, y + size * 0.5f, NanoVGColor(1f, 1f, 1f, 0.35f * alpha), size * 0.3f)
        }
    }

    internal fun VanillaListScreenRenderer.loadIconFromFile(path: Path?, cacheId: Any): NanoVGImage? {
        val key = VanillaListScreenRenderer.IconCacheKey(cacheId)
        if (iconCache.containsKey(key)) return iconCache[key]
        val image = try {
            if (path == null || !Files.exists(path)) null else loadStream(Files.newInputStream(path), cacheId)
        } catch (ignored: Throwable) {
            null
        }
        iconCache[key] = image
        return image
    }

    internal fun VanillaListScreenRenderer.loadIconFromBytes(bytes: ByteArray?, cacheId: Any): NanoVGImage? {
        val key = VanillaListScreenRenderer.IconCacheKey(cacheId)
        if (iconCache.containsKey(key)) return iconCache[key]
        val image = if (bytes == null || bytes.isEmpty()) null else {
            try {
                loadStream(ByteArrayInputStream(bytes), cacheId)
            } catch (ignored: Throwable) {
                null
            }
        }
        iconCache[key] = image
        return image
    }

    internal fun VanillaListScreenRenderer.loadStream(stream: java.io.InputStream, cacheId: Any): NanoVGImage? {
        return stream.use { s ->
            TextureLoader.loadFromStream(s, generateMipmaps = true, forceWhite = false)
        }?.let { loaded ->
            try {
                NanoVGImage.createFromGLTexture(RenderEngine.nanoVG.handle, loaded.textureId, loaded.width, loaded.height)
            } catch (ignored: Throwable) {
                null
            }
        }
    }
