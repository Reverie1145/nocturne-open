package cn.reverie.gui.theme

import cn.reverie.module.misc.YuriGallery
import cn.reverie.render.art.AnimeArt
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGFont
import cn.reverie.render.nanovg.NanoVGPath
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.render.video.VideoBackgroundPlayer
import org.lwjgl.nanovg.NanoVG.*
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Shared design system for Nocturne's modern web-inspired UI.
 *
 * Warm-tuned to harmonise with the anime cherry-blossom video background:
 * - Plum/ink shadows instead of cold blacks
 * - Soft sakura-pink accent (#F5A3B7) and warm peach (#F8C390)
 * - Restrained glass cards, pill controls, and clean typographic hierarchy
 */
object MenuTheme {

    private const val TAU = 6.2831855f

    // -----------------------------------------------------------------------------------------
    // Palette
    // -----------------------------------------------------------------------------------------

    val INK = NanoVGColor(0.06f, 0.04f, 0.08f, 1f)
    val INK_DEEP = NanoVGColor(0.03f, 0.02f, 0.04f, 1f)
    val ACCENT = NanoVGColor(0.96f, 0.64f, 0.72f, 1f)          // Sakura pink
    val ACCENT_WARM = NanoVGColor(0.97f, 0.77f, 0.58f, 1f)     // Peach
    val ACCENT_DEEP = NanoVGColor(0.55f, 0.22f, 0.32f, 1f)     // Plum
    val DANGER = NanoVGColor(0.96f, 0.44f, 0.46f, 1f)
    val TEXT = NanoVGColor(0.98f, 0.96f, 0.96f, 1f)
    val TEXT_MUTED = NanoVGColor(0.82f, 0.76f, 0.80f, 1f)
    val HAIRLINE = NanoVGColor(1f, 0.88f, 0.92f, 0.10f)

    private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    // -----------------------------------------------------------------------------------------
    // Fonts
    // -----------------------------------------------------------------------------------------

    private var headingFont: NanoVGFont? = null

    fun resolveHeadingFont(): NanoVGFont? {
        headingFont?.let { return it }
        val mgr = RenderEngine.fontManager ?: return null
        var font = mgr.getFont("inter-semibold")
        if (font == null) {
            for (f in listOf(
                File("src/main/resources/assets/reverie/fonts/regular_semi.otf"),
                File("assets/reverie/fonts/regular_semi.otf")
            )) {
                if (f.exists()) {
                    font = mgr.loadFontFromFile("inter-semibold", f)
                    if (font != null) break
                }
            }
            if (font == null) {
                val s = javaClass.getResourceAsStream("/assets/reverie/fonts/regular_semi.otf")
                    ?: Thread.currentThread().contextClassLoader.getResourceAsStream("assets/reverie/fonts/regular_semi.otf")
                if (s != null) font = mgr.loadFontFromStream("inter-semibold", s)
            }
        }
        headingFont = font ?: RenderEngine.uiFont
        return headingFont
    }

    fun <T> heading(block: () -> T): T {
        val f = resolveHeadingFont() ?: return block()
        return RenderEngine.fontRenderer.withFont(f, 1f, 0f, block)
    }

    fun <T> cjk(block: () -> T): T = RenderEngine.withNamedFont("default", block)

    // -----------------------------------------------------------------------------------------
    // Layout
    // -----------------------------------------------------------------------------------------

    data class PageLayout(
        val screenW: Float,
        val screenH: Float,
        val marginX: Float,
        val topY: Float,
        val listX: Float,
        val listY: Float,
        val listW: Float,
        val listH: Float,
        val dockY: Float,
        val dockH: Float,
        val cardH: Float = 60f,
        val cardGap: Float = 6f
    )

    fun computePageLayout(screenW: Float, screenH: Float, hasArt: Boolean = true): PageLayout {
        val marginX = (screenW * 0.055f).coerceIn(40f, 80f)
        val showArt = hasArt && YuriGallery.enabled && YuriGallery.menuArt.value && screenW >= 780f
        val listW = if (showArt) (screenW * 0.54f).coerceIn(440f, 680f) else (screenW - marginX * 2f)

        val topY = 24f
        val headerH = 34f
        val toolH = 34f
        val toolY = topY + headerH + 16f
        val listY = toolY + toolH + 14f

        val dockH = 44f
        val dockY = screenH - dockH - 18f
        val listH = (dockY - listY - 12f).coerceAtLeast(140f)

        return PageLayout(
            screenW, screenH, marginX, topY,
            listX = marginX, listY = listY, listW = listW, listH = listH,
            dockY = dockY, dockH = dockH
        )
    }

    // -----------------------------------------------------------------------------------------
    // Surfaces & Background
    // -----------------------------------------------------------------------------------------

    fun drawBackdrop(
        w: Float, h: Float, alpha: Float, now: Long,
        artAnchorXFraction: Float = 0.82f,
        drawScrim: Boolean = true,
        scrimWidthFraction: Float = 0.62f
    ) {
        if (alpha <= 0.003f) return

        // Deep plum canvas guarantees no visual flash during video load
        Draw2D.gradientLinear(
            0f, 0f, w, h, 0f,
            NanoVGColor(0.08f, 0.05f, 0.08f, alpha),
            NanoVGColor(0.04f, 0.02f, 0.05f, alpha)
        )
        VideoBackgroundPlayer.render(w, h, alpha = 1.0f)

        // Warm top/bottom vignettes
        Draw2D.gradientLinear(0f, 0f, w, h * 0.24f, 0f, INK.copy(a = 0.45f * alpha), INK.copy(a = 0f))
        Draw2D.gradientLinear(0f, h * 0.62f, w, h * 0.38f, 0f, INK.copy(a = 0f), INK.copy(a = 0.58f * alpha))

        if (YuriGallery.enabled && YuriGallery.menuArt.value && w >= 740f) {
            val standingId = AnimeArt.dailyPick(AnimeArt.MENU_STANDING_POOL)
            val slideX = (1f - alpha) * 60f
            val breathing = AnimeArt.idleBob(now)
            val artH = h * 0.82f
            val baseY = h - 8f
            val anchorX = w * artAnchorXFraction + slideX
            Draw2D.shadow(
                anchorX - artH * 0.22f, baseY - 10f, artH * 0.44f, 10f, 5f, 9f,
                NanoVGColor(0f, 0f, 0f, 0.32f * alpha), 1f
            )
            AnimeArt.drawStanding(standingId, anchorX, baseY + breathing * 2.0f, artH, alpha = 0.96f * alpha)
        }

        if (drawScrim) {
            val scrimW = min(w * scrimWidthFraction, 820f)
            Draw2D.gradientLinear(0f, 0f, scrimW, h, 0f, INK.copy(a = 0.62f * alpha), INK.copy(a = 0f), horizontal = true)
        }
    }

    fun drawGlass(
        x: Float, y: Float, w: Float, h: Float, radius: Float,
        screenW: Float, screenH: Float, alpha: Float,
        lift: Float = 0f, tint: Float = 0.42f
    ) {
        if (alpha <= 0.003f || w <= 0f || h <= 0f) return
        VideoBackgroundPlayer.renderBlurredArea(x, y, w, h, radius, screenW, screenH, alpha)
        Draw2D.roundedRect(x, y, w, h, radius, INK.copy(a = (tint - 0.05f * lift) * alpha))
        Draw2D.gradientLinear(
            x, y, w, h * 0.55f, radius,
            NanoVGColor(1f, 0.92f, 0.94f, (0.06f + 0.05f * lift) * alpha),
            NanoVGColor(1f, 0.92f, 0.94f, 0f)
        )
        Draw2D.roundedOutline(x, y, w, h, radius, NanoVGColor(1f, 0.88f, 0.92f, (0.10f + 0.14f * lift) * alpha), 1f)
    }

    // -----------------------------------------------------------------------------------------
    // Buttons & Controls
    // -----------------------------------------------------------------------------------------

    enum class ButtonStyle { PRIMARY, GHOST, DANGER_GHOST, SUBTLE }

    fun drawButton(
        x: Float, y: Float, w: Float, h: Float,
        label: String,
        style: ButtonStyle,
        hover: Float,
        press: Float,
        alpha: Float,
        enabled: Boolean = true,
        withArrow: Boolean = false,
        leadingBackArrow: Boolean = false,
        screenW: Float = 0f,
        screenH: Float = 0f
    ) {
        if (alpha <= 0.003f || w <= 0f || h <= 0f) return
        val radius = h * 0.5f
        val a = alpha * (if (enabled) 1f else 0.45f)
        val cx = x + w * 0.5f
        val cy = y + h * 0.5f

        val nvg = RenderEngine.nanoVG
        nvg.transform.save()
        if (press > 0.005f) {
            val scale = 1f - 0.035f * press
            nvg.transform.translate(cx, cy)
            nvg.transform.scale(scale, scale)
            nvg.transform.translate(-cx, -cy)
        }

        when (style) {
            ButtonStyle.PRIMARY -> {
                // Warm paper-white pill: the strongest element on screen, so it never competes with accent colours.
                Draw2D.shadow(x, y, w, h, radius, 24f, NanoVGColor(0f, 0f, 0f, (0.30f + 0.10f * hover) * a), 9f)
                if (hover > 0.01f) Draw2D.glow(x, y, w, h, radius, 18f, ACCENT.copy(a = 0.22f * hover * a))
                val paper = cn.reverie.gui.mainmenu.MenuFx.PAPER.lerp(NanoVGColor(1f, 1f, 1f, 1f), 0.55f * hover)
                Draw2D.gradientLinear(
                    x, y, w, h, radius,
                    paper.copy(a = a),
                    paper.lerp(NanoVGColor(0.95f, 0.86f, 0.86f, 1f), 0.45f).copy(a = a)
                )
                cn.reverie.gui.mainmenu.MenuFx.edgeLight(x, y, w, h, radius, a, 0.9f)
                val ink = INK_DEEP.copy(a = a)
                heading {
                    if (withArrow) {
                        NanoVGRenderer.drawVerticallyCenteredString(label, x + 20f, cy, ink, 14f)
                        drawArrow(x + w - 22f + 2f * hover, cy, ink.copy(a = 0.85f * a), hover)
                    } else {
                        NanoVGRenderer.drawCenteredString(label, cx, cy, ink, 13f)
                    }
                }
            }
            ButtonStyle.GHOST -> {
                // Frosted glass pill with a specular rim; brightens on hover.
                cn.reverie.gui.mainmenu.MenuFx.frost(x, y, w, h, radius, 0.9f * a)
                Draw2D.roundedRect(x, y, w, h, radius, NanoVGColor(0.08f, 0.06f, 0.10f, (0.30f - 0.08f * hover) * a))
                Draw2D.roundedRect(x, y, w, h, radius, NanoVGColor(1f, 1f, 1f, (0.05f + 0.09f * hover) * a))
                Draw2D.gradientLinear(
                    x, y, w, h * 0.55f, radius,
                    NanoVGColor(1f, 1f, 1f, 0.10f * a), NanoVGColor(1f, 1f, 1f, 0f)
                )
                cn.reverie.gui.mainmenu.MenuFx.edgeLight(x, y, w, h, radius, a, 0.8f + 0.5f * hover)
                val textColor = TEXT.lerp(NanoVGColor(1f, 1f, 1f, 1f), hover).copy(a = a)
                heading {
                    if (leadingBackArrow) {
                        drawBackArrow(x + 16f - 1.5f * hover, cy, textColor, 1.6f)
                        NanoVGRenderer.drawVerticallyCenteredString(label, x + 28f, cy, textColor, 12f)
                    } else if (withArrow) {
                        NanoVGRenderer.drawVerticallyCenteredString(label, x + 20f, cy, textColor, 14f)
                        drawArrow(x + w - 22f + 2f * hover, cy, textColor.copy(a = 0.85f * a), hover)
                    } else {
                        NanoVGRenderer.drawCenteredString(label, cx, cy, textColor, 12.5f)
                    }
                }
            }
            ButtonStyle.DANGER_GHOST -> {
                val fillA = (if (hover > 0.05f) 0.16f else 0.05f) * a
                Draw2D.roundedRect(x, y, w, h, radius, DANGER.copy(a = fillA))
                Draw2D.roundedOutline(x, y, w, h, radius, DANGER.copy(a = (0.20f + 0.45f * hover) * a), 1f)
                val c = NanoVGColor(
                    mix(0.95f, 1f, hover),
                    mix(0.72f, 0.50f, hover),
                    mix(0.72f, 0.50f, hover),
                    a
                )
                heading { NanoVGRenderer.drawCenteredString(label, cx, cy, c, 12f) }
            }
            ButtonStyle.SUBTLE -> {
                Draw2D.roundedRect(x, y, w, h, radius, NanoVGColor(1f, 1f, 1f, (0.03f + 0.08f * hover) * a))
                val textColor = if (hover > 0.05f) TEXT.copy(a = a) else TEXT_MUTED.copy(a = a)
                NanoVGRenderer.drawCenteredString(label, cx, cy, textColor, 12f)
            }
        }

        nvg.transform.restore()
    }

    fun drawSearchBox(
        x: Float, y: Float, w: Float, h: Float,
        query: String,
        focused: Boolean,
        hover: Boolean,
        hint: String,
        alpha: Float,
        cursorVisible: Boolean,
        screenW: Float,
        screenH: Float
    ) {
        val radius = h * 0.5f
        drawGlass(x, y, w, h, radius, screenW, screenH, alpha, lift = if (focused) 0.6f else (if (hover) 0.25f else 0f), tint = 0.38f)
        if (focused) {
            Draw2D.roundedOutline(x, y, w, h, radius, ACCENT.copy(a = 0.55f * alpha), 1.2f)
        }

        val searchIconX = x + 16f
        val cy = y + h * 0.5f
        drawSearchIcon(searchIconX, cy, 13f, NanoVGColor(1f, 0.92f, 0.95f, (if (focused) 0.95f else 0.55f) * alpha))

        val textX = x + 34f
        val textMaxW = w - 62f
        if (query.isEmpty() && !focused) {
            NanoVGRenderer.drawVerticallyCenteredString(hint, textX, cy, TEXT_MUTED.copy(a = 0.70f * alpha), 12f)
        } else {
            val font = RenderEngine.fontRenderer
            val fitted = font.trimWithEllipsis(query, textMaxW, 12f)
            val drawnW = NanoVGRenderer.drawVerticallyCenteredString(fitted, textX, cy, TEXT.copy(a = alpha), 12f)
            if (focused && cursorVisible) {
                val cx = (textX + drawnW + 1.5f).coerceAtMost(x + w - 24f)
                Draw2D.line(cx, cy - 6f, cx, cy + 6f, ACCENT.copy(a = alpha), 1.3f)
            }
        }

        if (query.isNotEmpty()) {
            drawClearIcon(x + w - 20f, cy, 11f, NanoVGColor(1f, 1f, 1f, 0.60f * alpha))
        }
    }

    fun drawChip(
        x: Float, cy: Float, text: String,
        alpha: Float,
        dotColor: NanoVGColor? = null,
        accented: Boolean = false
    ): Float {
        val font = RenderEngine.fontRenderer
        val tw = font.getStringWidth(text, 11f)
        val hasDot = dotColor != null
        val dotW = if (hasDot) 10f else 0f
        val pw = tw + 20f + dotW
        val ph = 22f
        val py = cy - ph * 0.5f
        val radius = ph * 0.5f

        val bg = if (accented) ACCENT.copy(a = 0.14f * alpha) else NanoVGColor(1f, 1f, 1f, 0.05f * alpha)
        val outline = if (accented) ACCENT.copy(a = 0.38f * alpha) else HAIRLINE.copy(a = 0.14f * alpha)
        val textC = if (accented) ACCENT.copy(a = alpha) else TEXT_MUTED.copy(a = alpha)

        Draw2D.roundedRect(x, py, pw, ph, radius, bg)
        Draw2D.roundedOutline(x, py, pw, ph, radius, outline, 1f)

        var tx = x + 10f
        if (dotColor != null) {
            Draw2D.circle(tx + 2.5f, cy, 2.5f, dotColor.copy(a = alpha))
            tx += dotW
        }
        NanoVGRenderer.drawVerticallyCenteredString(text, tx, cy, textC, 11f)
        return pw
    }

    fun drawScrollThumb(trackX: Float, thumbY: Float, trackW: Float, thumbH: Float, active: Boolean, alpha: Float) {
        val radius = trackW * 0.5f
        val a = (if (active) 0.50f else 0.18f) * alpha
        Draw2D.roundedRect(trackX, thumbY, trackW, thumbH, radius, NanoVGColor(1f, 0.92f, 0.95f, a))
    }

    // -----------------------------------------------------------------------------------------
    // Page Header & Brand
    // -----------------------------------------------------------------------------------------

    fun drawBrand(x: Float, cy: Float, alpha: Float) {
        drawCrescent(x + 8f, cy, 8f, alpha)
        heading {
            NanoVGRenderer.drawVerticallyCenteredString("Nocturne", x + 24f, cy, TEXT.copy(a = alpha), 16f)
        }
    }

    fun drawPageHeader(
        layout: PageLayout,
        title: String,
        chipText: String?,
        chipDot: NanoVGColor?,
        alpha: Float
    ) {
        val x = layout.marginX
        val cy = layout.topY + 16f

        drawBrand(x, cy, alpha)

        // Title + badge anchored further right
        val titleX = x + 130f
        heading {
            NanoVGRenderer.drawVerticallyCenteredString(title, titleX, cy, TEXT.copy(a = alpha), 18f)
        }

        if (chipText != null) {
            val titleW = heading { RenderEngine.fontRenderer.getStringWidth(title, 18f) }
            drawChip(titleX + titleW + 12f, cy, chipText, alpha, dotColor = chipDot)
        }
    }

    // -----------------------------------------------------------------------------------------
    // Geometry & Vector Icons
    // -----------------------------------------------------------------------------------------

    fun drawArrow(cx: Float, cy: Float, color: NanoVGColor, t: Float = 0f) {
        if (color.a <= 0.003f) return
        val ctx = RenderEngine.nanoVG.handle
        if (ctx == 0L) return
        nvgLineCap(ctx, NVG_ROUND)
        nvgLineJoin(ctx, NVG_ROUND)
        val half = 5.5f + 1.5f * t
        val path = NanoVGPath(ctx)
        path.begin(); path.moveTo(cx - half, cy); path.lineTo(cx + half, cy); path.stroke(color, 1.6f)
        path.begin(); path.moveTo(cx + half - 4.5f, cy - 4.5f); path.lineTo(cx + half, cy); path.lineTo(cx + half - 4.5f, cy + 4.5f); path.stroke(color, 1.6f)
    }

    fun drawBackArrow(cx: Float, cy: Float, color: NanoVGColor, strokeW: Float = 1.6f) {
        val ctx = RenderEngine.nanoVG.handle
        if (ctx == 0L) return
        nvgLineCap(ctx, NVG_ROUND)
        nvgLineJoin(ctx, NVG_ROUND)
        val path = NanoVGPath(ctx)
        path.begin(); path.moveTo(cx - 4.5f, cy); path.lineTo(cx + 4.5f, cy); path.stroke(color, strokeW)
        path.begin(); path.moveTo(cx - 1.5f, cy - 3.2f); path.lineTo(cx - 4.5f, cy); path.lineTo(cx - 1.5f, cy + 3.2f); path.stroke(color, strokeW)
    }

    fun drawCrescent(cx: Float, cy: Float, r: Float, alpha: Float) {
        val ctx = RenderEngine.nanoVG.handle
        if (ctx == 0L || alpha <= 0.003f) return
        val k = cos(0.20f * TAU)
        val rx = r * k
        val segments = 24
        val half = PI.toFloat()
        val path = NanoVGPath(ctx)
        path.begin()
        for (i in 0..segments) {
            val t = -half * 0.5f + half * i / segments
            val px = cx + r * cos(t)
            val py = cy + r * sin(t)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        for (i in 0..segments) {
            val t = half * 0.5f - half * i / segments
            path.lineTo(cx + rx * cos(t), cy + r * sin(t))
        }
        path.close()
        path.fill(ACCENT.copy(a = 0.95f * alpha))
    }

    /**
     * Bespoke vector icon for the AI Agent: an elegant dual-sparkle / four-point star.
     * Replaces the old generic AGI chip bitmap.
     */
    fun drawAgentIcon(cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        val ctx = RenderEngine.nanoVG.handle
        if (ctx == 0L || color.a <= 0.003f) return

        nvgLineCap(ctx, NVG_ROUND)
        nvgLineJoin(ctx, NVG_ROUND)
        val path = NanoVGPath(ctx)

        // Main 4-point concave star
        val rOuter = size * 0.46f
        val rInner = size * 0.14f
        val ox = cx - size * 0.06f
        val oy = cy - size * 0.06f

        path.begin()
        for (i in 0 until 8) {
            val angle = -PI.toFloat() * 0.5f + i * (PI.toFloat() * 0.25f)
            val r = if (i % 2 == 0) rOuter else rInner
            val px = ox + cos(angle) * r
            val py = oy + sin(angle) * r
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        path.stroke(color, 1.6f)

        // Secondary small filled sparkle in the upper right
        val sx = cx + size * 0.32f
        val sy = cy - size * 0.30f
        val srOuter = size * 0.18f
        val srInner = size * 0.05f

        path.begin()
        for (i in 0 until 8) {
            val angle = -PI.toFloat() * 0.5f + i * (PI.toFloat() * 0.25f)
            val r = if (i % 2 == 0) srOuter else srInner
            val px = sx + cos(angle) * r
            val py = sy + sin(angle) * r
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        path.fill(color.copy(a = color.a * 0.90f))
    }

    fun drawSearchIcon(cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        val ctx = RenderEngine.nanoVG.handle
        if (ctx == 0L || color.a <= 0.003f) return
        nvgLineCap(ctx, NVG_ROUND)
        val path = NanoVGPath(ctx)
        val r = size * 0.36f
        val glassX = cx - size * 0.08f
        val glassY = cy - size * 0.08f
        path.begin(); path.circle(glassX, glassY, r); path.stroke(color, 1.5f)
        val handleStart = r * 0.707f
        val handleEnd = r * 1.55f
        path.begin()
        path.moveTo(glassX + handleStart, glassY + handleStart)
        path.lineTo(glassX + handleEnd, glassY + handleEnd)
        path.stroke(color, 1.5f)
    }

    fun drawClearIcon(cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        val ctx = RenderEngine.nanoVG.handle
        if (ctx == 0L || color.a <= 0.003f) return
        nvgLineCap(ctx, NVG_ROUND)
        val path = NanoVGPath(ctx)
        val half = size * 0.35f
        path.begin(); path.moveTo(cx - half, cy - half); path.lineTo(cx + half, cy + half); path.stroke(color, 1.4f)
        path.begin(); path.moveTo(cx + half, cy - half); path.lineTo(cx - half, cy + half); path.stroke(color, 1.4f)
    }
}
