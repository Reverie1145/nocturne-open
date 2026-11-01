package cn.reverie.gui.mainmenu

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGFont
import cn.reverie.render.nanovg.NanoVGRenderer
import org.lwjgl.nanovg.NanoVG.*
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Main menu clock and date renderer · Zinc Precision v4.0
 */
object MainMenuClock {

    private var comfortaaFont: NanoVGFont? = null

    fun getComfortaaFont(): NanoVGFont? {
        if (comfortaaFont != null) return comfortaaFont
        RenderEngine.comfortaaFont?.let {
            comfortaaFont = it
            return it
        }
        val mgr = RenderEngine.fontManager ?: return null
        var font = mgr.getFont("comfortaa")
        if (font == null) {
            val candidates = listOf(
                File("src/main/resources/assets/reverie/fonts/Comfortaa.ttf"),
                File("assets/reverie/fonts/Comfortaa.ttf")
            )
            for (f in candidates) {
                if (f.exists()) {
                    font = mgr.loadFontFromFile("comfortaa", f)
                    if (font != null) break
                }
            }
            if (font == null) {
                val s = javaClass.getResourceAsStream("/assets/reverie/fonts/Comfortaa.ttf")
                    ?: Thread.currentThread().contextClassLoader.getResourceAsStream("assets/reverie/fonts/Comfortaa.ttf")
                if (s != null) {
                    font = mgr.loadFontFromStream("comfortaa", s)
                }
            }
        }
        comfortaaFont = font
        return font
    }

    fun renderTopRightClock(screenW: Float, screenH: Float, anim: Float) {
        val clockX = screenW - 38f
        val clockY = 28f
        val timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd", Locale.ENGLISH)).uppercase()

        val comfortaa = getComfortaaFont()
        val context = RenderEngine.nanoVG.handle

        if (comfortaa != null && context != 0L) {
            val timeSize = 36f
            val dateSize = 11f
            val dateY = clockY + 40f

            nvgTextAlign(context, NVG_ALIGN_RIGHT or NVG_ALIGN_TOP)
            comfortaa.drawText(timeStr, clockX + 2f, clockY + 2f, NanoVGColor(0f, 0f, 0f, 0.40f * anim), timeSize)
            comfortaa.drawText(timeStr, clockX, clockY, NanoVGColor(1f, 1f, 1f, 0.98f * anim), timeSize)

            comfortaa.drawText(dateStr, clockX + 1.2f, dateY + 1.2f, NanoVGColor(0f, 0f, 0f, 0.35f * anim), dateSize)
            comfortaa.drawText(dateStr, clockX, dateY, NanoVGColor(0.75f, 0.80f, 0.90f, 0.85f * anim), dateSize)
            nvgTextAlign(context, NVG_ALIGN_LEFT or NVG_ALIGN_TOP)
        } else {
            NanoVGRenderer.drawRightString(timeStr, clockX, clockY, NanoVGColor(1f, 1f, 1f, 0.98f * anim), 34f, shadow = true)
            NanoVGRenderer.drawRightString(dateStr, clockX, clockY + 38f, NanoVGColor(0.75f, 0.80f, 0.90f, 0.85f * anim), 11f, shadow = true)
        }
    }
}
