package cn.reverie.render.font

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.nanovg.NanoVGFont

class SkiaFont(val fontName: String, private val resourcePath: String) {

    @Volatile
    private var loaded = false

    fun getFont(size: Int): SkiaFontRenderer? {
        val nvg = ensureLoaded() ?: return null
        return cache.getOrPut(fontName to size) {
            SkiaFontRenderer(FontRenderer(nvg), nvg, size)
        }
    }

    private fun ensureLoaded(): NanoVGFont? {
        RenderEngine.fontManager?.getFont(fontName)?.let {
            loaded = true
            return it
        }
        if (loaded) return null
        return try {
            val stream = SkiaFont::class.java.getResourceAsStream(resourcePath)
                ?: SkiaFont::class.java.getResourceAsStream(resourcePath.replace("/nitro", ""))
                ?: run {
                    loaded = true
                    System.err.println("[Nocturne] Font resource missing: $resourcePath")
                    return null
                }
            val font = RenderEngine.fontManager?.loadFontFromStream(fontName, stream)
            loaded = font != null
            font
        } catch (e: Throwable) {
            loaded = true
            System.err.println("[Nocturne] Failed to load font '$fontName': ${e.message}")
            null
        }
    }

    companion object {
        private val cache = HashMap<Pair<String, Int>, SkiaFontRenderer>()
    }
}

object FontSet {
    val NUNITO_SANS = SkiaFont("nunito-sans", "/assets/minecraft/nitro/fonts/nunitosans-10pt-regular.ttf")
    val MISANS = SkiaFont("misans", "/assets/minecraft/nitro/fonts/misans.ttf")
    val MISANS_BOLD = SkiaFont("misans-bold", "/assets/minecraft/nitro/fonts/misans-bold.ttf")
    val LUGGAGE_ROUND = SkiaFont("luggageround", "/assets/minecraft/nitro/fonts/luggageround-regular.ttf")
    val WILD_FONT = SkiaFont("wildfont", "/assets/minecraft/nitro/fonts/wildfont.ttf")
    val NITRO_ICON = SkiaFont("nitro-icon", "/assets/minecraft/nitro/fonts/nitro-icon.ttf")
    val SF_PRO_SEMIBOLD = SkiaFont("sf-semi", "/assets/minecraft/nitro/fonts/sfsemi.otf")
    val SF_PRO_MEDIUM = SkiaFont("sf-medium", "/assets/minecraft/nitro/fonts/sfmed.otf")
    val SF_BOLD = SkiaFont("sf-bold", "/assets/minecraft/nitro/fonts/sf-bold.ttf")
    val BOREL = SkiaFont("borel", "/assets/minecraft/nitro/fonts/borel-regular.ttf")
    val CLICKGUI_ICON = SkiaFont("clickgui-icon", "/assets/minecraft/nitro/fonts/clickgui-icon.ttf")
    val ROBOTO_BOLD = SkiaFont("roboto-bold", "/assets/minecraft/nitro/fonts/roboto-bold.ttf")
    val ROBOTO = SkiaFont("roboto", "/assets/minecraft/nitro/fonts/quicksand.ttf")
    val BALOO = SkiaFont("baloo", "/assets/minecraft/nitro/fonts/baloo.ttf")
    val NICO = SkiaFont("nico", "/assets/minecraft/nitro/fonts/icons.ttf")
    val VERDANA = SkiaFont("verdana", "/assets/minecraft/nitro/fonts/verdana.ttf")
    val MINECRAFT = SkiaFont("minecraft", "/assets/minecraft/nitro/fonts/minecraft.ttf")
    val UNIFONT = SkiaFont("unifont", "/assets/minecraft/nitro/fonts/unifont.ttf")
    val NOTOSANS = SkiaFont("noto-bold", "/assets/minecraft/nitro/fonts/NotoEBlod.ttf")
}
