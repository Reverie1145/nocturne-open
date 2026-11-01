package cn.reverie.gui.vfp

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.CornerRadii
import cn.reverie.render.draw.RoundedRect
import cn.reverie.render.nanovg.NanoVGImage
import cn.reverie.render.nanovg.NanoVGPaint
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.render.texture.TextureLoader
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.system.MemoryStack
    internal fun VfpVersionScreenRenderer.drawIconImpl(identifier: Identifier, x: Float, y: Float, size: Float, alpha: Float) {
        val image = loadIconImpl(identifier)
        if (image != null && image.isValid) {
            drawImageRectImpl(image, x, y, size, alpha)
        } else {
            NanoVGRenderer.drawCircle(x + size * 0.5f, y + size * 0.5f, size * 0.32f, textMuted.copy(a = 0.6f * alpha))
        }
    }

    internal fun VfpVersionScreenRenderer.drawImageRectImpl(image: NanoVGImage, x: Float, y: Float, size: Float, alpha: Float) {
        val context = RenderEngine.nanoVG.handle
        MemoryStack.stackPush().use { stack ->
            val paint = NVGPaint.malloc(stack)
            NanoVGPaint.imagePattern(context, x, y, size, size, 0f, image.imageHandle, alpha.coerceIn(0f, 1f), paint)
            RoundedRect.draw(context, x, y, size, size, CornerRadii(0f), paint)
        }
    }

    internal fun VfpVersionScreenRenderer.loadIconImpl(identifier: Identifier): NanoVGImage? {
        val key = identifier.toString()
        if (iconCache.containsKey(key)) return iconCache[key]

        val image = try {
            val resource = Minecraft.getInstance().resourceManager.getResource(identifier).orElse(null)
            if (resource == null) {
                null
            } else {
                val loaded = resource.open().use { stream ->
                    TextureLoader.loadFromStream(stream, generateMipmaps = true, forceWhite = false)
                }
                if (loaded == null) {
                    null
                } else {
                    try {
                        NanoVGImage.createFromGLTexture(RenderEngine.nanoVG.handle, loaded.textureId, loaded.width, loaded.height)
                    } catch (ignored: Throwable) {
                        null
                    }
                }
            }
        } catch (ignored: Throwable) {
            null
        }
        iconCache[key] = image
        return image
    }
