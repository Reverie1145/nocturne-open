package cn.reverie.render.core

import cn.reverie.render.font.FontManager
import cn.reverie.render.nanovg.NanoVGFont
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL30C.*
import java.io.File
/** Font lookup and loading kept separate from the frame/render pipeline. */
internal object RenderFontFacade {
    private const val UI_FONT_SIZE_SCALE = 1.07f
    private const val UI_FONT_VERTICAL_BIAS = -0.0442f

    fun getFontByName(name: String): NanoVGFont? {
        val n = name.trim().lowercase()
        return when (n) {
            "minecraft", "vanilla", "minecraft default" -> RenderEngine.minecraftFont ?: RenderEngine.defaultFont ?: RenderEngine.fontRenderer.defaultFont
            "default", "misans" -> RenderEngine.defaultFont ?: RenderEngine.fontRenderer.defaultFont
            "ui", "inter" -> RenderEngine.uiFont ?: RenderEngine.defaultFont ?: RenderEngine.fontRenderer.defaultFont
            "product sans", "sans" -> RenderEngine.sansFont ?: RenderEngine.uiFont ?: RenderEngine.defaultFont ?: RenderEngine.fontRenderer.defaultFont
            "arial" -> RenderEngine.arialFont ?: RenderEngine.defaultFont ?: RenderEngine.fontRenderer.defaultFont
            "wqy", "wenquanyi" -> RenderEngine.wqyFont ?: RenderEngine.defaultFont ?: RenderEngine.fontRenderer.defaultFont
            "comfortaa" -> RenderEngine.comfortaaFont ?: RenderEngine.defaultFont ?: RenderEngine.fontRenderer.defaultFont
            "kuriyama" -> RenderEngine.kuriyamaFont ?: RenderEngine.defaultFont ?: RenderEngine.fontRenderer.defaultFont
            "verdana" -> RenderEngine.verdanaFont ?: RenderEngine.defaultFont ?: RenderEngine.fontRenderer.defaultFont
            "tahoma" -> RenderEngine.tahomaFont ?: RenderEngine.defaultFont ?: RenderEngine.fontRenderer.defaultFont
            "bahnschrift" -> RenderEngine.bahnschriftFont ?: RenderEngine.defaultFont ?: RenderEngine.fontRenderer.defaultFont
            "consolas" -> RenderEngine.consolasFont ?: RenderEngine.defaultFont ?: RenderEngine.fontRenderer.defaultFont
            "segoe ui", "segoe" -> RenderEngine.segoeUiFont ?: RenderEngine.defaultFont ?: RenderEngine.fontRenderer.defaultFont
            "comic sans", "comic" -> RenderEngine.comicFont ?: RenderEngine.defaultFont ?: RenderEngine.fontRenderer.defaultFont
            else -> RenderEngine.fontManager?.getFont(name) ?: RenderEngine.defaultFont ?: RenderEngine.fontRenderer.defaultFont
        }
    }

    fun <T> withNamedFont(name: String, block: () -> T): T {
        val font = getFontByName(name) ?: return block()
        val isUi = name.equals("UI", ignoreCase = true) || name.equals("Inter", ignoreCase = true)
        val scale = if (isUi) UI_FONT_SIZE_SCALE else 1.0f
        val bias = if (isUi) UI_FONT_VERTICAL_BIAS else 0.0f
        return RenderEngine.fontRenderer.withFont(font, scale, bias, block)
    }

    fun loadFont(
        mgr: FontManager,
        fontName: String,
        fileNames: List<String>,
        systemFallback: String? = null
    ): NanoVGFont? {
        for (fileName in fileNames) {
            val devFiles = listOf(
                File("src/main/resources/assets/reverie/fonts/$fileName"),
                File("assets/reverie/fonts/$fileName")
            )
            for (file in devFiles) {
                if (file.exists() && file.isFile) {
                    val font = mgr.loadFontFromFile(fontName, file)
                    if (font != null) {
                        println("[Nocturne] Successfully loaded font '$fontName' from file: ${file.absolutePath}, handle=${font.handle}")
                        return font
                    }
                }
            }
            val streamCandidates = listOf("/assets/reverie/fonts/$fileName", "assets/reverie/fonts/$fileName")
            for (path in streamCandidates) {
                val stream = RenderEngine::class.java.getResourceAsStream(path)
                    ?: Thread.currentThread().contextClassLoader.getResourceAsStream(path.removePrefix("/"))
                if (stream != null) {
                    val font = mgr.loadFontFromStream(fontName, stream)
                    if (font != null) {
                        println("[Nocturne] Successfully loaded font '$fontName' from stream: $path, handle=${font.handle}")
                        return font
                    }
                }
            }
        }
        if (systemFallback != null) {
            val sysFile = File(systemFallback)
            if (sysFile.exists() && sysFile.isFile) {
                val font = mgr.loadFontFromFile(fontName, sysFile)
                if (font != null) {
                    println("[Nocturne] Successfully loaded font '$fontName' from system file: ${sysFile.absolutePath}, handle=${font.handle}")
                    return font
                }
            }
        }
        return null
    }
}
