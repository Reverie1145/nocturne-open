package cn.reverie.gui.mainmenu

import cn.reverie.gui.theme.MenuTheme
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGPath
import cn.reverie.render.nanovg.NanoVGRenderer
import org.lwjgl.nanovg.NanoVG.*
import kotlin.math.cos
import kotlin.math.sin

// Re-export common theme handles for the main menu package.
internal val MENU_INK get() = MenuTheme.INK
internal val MENU_ACCENT get() = MenuTheme.ACCENT
internal val MENU_DANGER get() = MenuTheme.DANGER

internal fun <T> MainMenuScreen.heading(block: () -> T): T = MenuTheme.heading(block)
internal fun <T> cjk(block: () -> T): T = MenuTheme.cjk(block)

internal fun drawGlass(
    x: Float, y: Float, w: Float, h: Float, radius: Float,
    screenW: Float, screenH: Float, alpha: Float, lift: Float = 0f, tint: Float = 0.42f
) = MenuTheme.drawGlass(x, y, w, h, radius, screenW, screenH, alpha, lift, tint)

internal fun drawArrow(cx: Float, cy: Float, color: NanoVGColor, t: Float = 0f) =
    MenuTheme.drawArrow(cx, cy, color, t)

internal fun drawCrescent(cx: Float, cy: Float, r: Float, alpha: Float) =
    MenuTheme.drawCrescent(cx, cy, r, alpha)

// ---------------------------------------------------------------------------------------------
// Icons
// ---------------------------------------------------------------------------------------------

internal fun drawDetailedIcon(iconName: String, cx: Float, cy: Float, size: Float, color: NanoVGColor) {
    // Intercept: custom vector sparkle for the AI Agent
    if (iconName.contains("agi")) {
        MenuTheme.drawAgentIcon(cx, cy, size, color)
        return
    }

    val half = size * 0.5f
    val loaded = NanoVGRenderer.loadImage(iconName)
    if (loaded != null && loaded.isValid) {
        NanoVGRenderer.drawImage(iconName, cx - half, cy - half, size, size, alpha = color.a, tintColor = color)
    } else {
        drawVectorIconFallback(iconName, cx, cy, size, color)
    }
}

internal fun drawVectorIconFallback(name: String, cx: Float, cy: Float, size: Float, color: NanoVGColor) {
    if (name.contains("agi")) {
        MenuTheme.drawAgentIcon(cx, cy, size, color)
        return
    }

    val context = cn.reverie.render.core.RenderEngine.nanoVG.handle
    val nvg = cn.reverie.render.core.RenderEngine.nanoVG
    val scale = size / 24.0f

    nvg.transform.save()
    nvg.transform.translate(cx, cy)
    nvg.transform.scale(scale, scale)

    nvgLineCap(context, NVG_ROUND)
    nvgLineJoin(context, NVG_ROUND)

    val path = NanoVGPath(context)
    val strokeW = 1.85f

    when {
        name.contains("single") -> {
            path.begin(); path.moveTo(-7f, -7f); path.lineTo(4f, 4f); path.stroke(color, strokeW)
            path.begin(); path.moveTo(7f, -7f); path.lineTo(-4f, 4f); path.stroke(color, strokeW)
        }
        name.contains("multi") || name.contains("world") -> {
            path.begin(); path.circle(0f, 0f, 8.5f); path.stroke(color, strokeW)
            path.begin(); path.moveTo(-8.5f, 0f); path.lineTo(8.5f, 0f); path.stroke(color, strokeW)
        }
        name.contains("account") || name.contains("player") -> {
            path.begin(); path.circle(0f, -4.2f, 3.2f); path.fill(color)
            path.begin(); path.roundedRect(-6.0f, 0.5f, 12f, 7.5f, 2.5f); path.fill(color)
        }
        name.contains("script") || name.contains("console") -> {
            path.begin(); path.roundedRect(-8.5f, -7.5f, 17f, 15f, 3.5f); path.stroke(color, strokeW)
            path.begin(); path.moveTo(-4.5f, -2.5f); path.lineTo(-2f, 0f); path.lineTo(-4.5f, 2.5f); path.stroke(color, strokeW)
        }
        name.contains("setting") -> {
            path.begin(); path.circle(0f, 0f, 4f); path.stroke(color, strokeW)
            for (i in 0 until 6) {
                val angle = (i * 60.0 * Math.PI / 180.0).toFloat()
                path.begin()
                path.moveTo(cos(angle) * 6.2f, sin(angle) * 6.2f)
                path.lineTo(cos(angle) * 8.5f, sin(angle) * 8.5f)
                path.stroke(color, strokeW)
            }
        }
        name.contains("man") || name.contains("access") -> {
            path.begin(); path.circle(0f, -4.5f, 2.3f); path.fill(color)
            path.begin(); path.moveTo(-6.5f, -0.5f); path.lineTo(6.5f, -0.5f); path.stroke(color, strokeW)
            path.begin(); path.moveTo(0f, -0.5f); path.lineTo(0f, 5.5f); path.stroke(color, strokeW)
        }
        name.contains("off") || name.contains("quit") -> {
            path.begin(); path.moveTo(0f, -8f); path.lineTo(0f, -0.5f); path.stroke(color, strokeW)
            path.begin()
            path.arc(0f, 0f, 7.5f, (-60.0 * Math.PI / 180.0).toFloat(), (240.0 * Math.PI / 180.0).toFloat(), NVG_CW)
            path.stroke(color, strokeW)
        }
        name.contains("play") -> {
            path.begin(); path.moveTo(-4f, -7f); path.lineTo(7f, 0f); path.lineTo(-4f, 7f); path.close(); path.stroke(color, strokeW)
        }
    }

    nvg.transform.restore()
}
