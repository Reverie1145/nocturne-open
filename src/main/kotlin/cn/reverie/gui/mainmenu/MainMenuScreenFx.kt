package cn.reverie.gui.mainmenu

import cn.reverie.gui.theme.MenuTheme
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGPaint
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.video.VideoBackgroundPlayer
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.nanovg.NanoVG.nvgRoundedRect
import org.lwjgl.nanovg.NanoVG.nvgStroke
import org.lwjgl.nanovg.NanoVG.nvgStrokePaint
import org.lwjgl.nanovg.NanoVG.nvgStrokeWidth
import org.lwjgl.nanovg.NanoVG.nvgBeginPath
import org.lwjgl.nanovg.NanoVG.nvgFill
import org.lwjgl.nanovg.NanoVG.nvgFillPaint
import org.lwjgl.nanovg.NanoVG.nvgRestore
import org.lwjgl.nanovg.NanoVG.nvgRotate
import org.lwjgl.nanovg.NanoVG.nvgSave
import org.lwjgl.nanovg.NanoVG.nvgScale
import org.lwjgl.nanovg.NanoVG.nvgTransform
import org.lwjgl.nanovg.NanoVG.nvgTranslate
import org.lwjgl.system.MemoryStack
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * Motion, lighting and atmosphere helpers shared by the main menu renderer.
 *
 * NanoVG is a 2D renderer, so the "3D" look is built from an affine approximation of a perspective
 * rotation (foreshortening + a small shear), pointer-driven glare, layered parallax and soft shadows.
 */
internal object MenuFx {

    // -----------------------------------------------------------------------------------------
    // Palette (derived from the cherry-blossom footage; surfaces follow the ClickGUI glass language)
    // -----------------------------------------------------------------------------------------

    val SAKURA = MenuTheme.ACCENT
    val PEACH = MenuTheme.ACCENT_WARM
    val VIOLET = NanoVGColor(0.56f, 0.46f, 1.00f, 1f)   // same family as the ClickGUI NOCTURNE accent
    val AQUA = NanoVGColor(0.52f, 0.80f, 0.96f, 1f)
    val SLATE = NanoVGColor(0.80f, 0.82f, 0.92f, 1f)
    val DANGER = MenuTheme.DANGER

    /** Plum-shifted variant of the ClickGUI panel colour (#0C0D11). */
    val PANEL = NanoVGColor(0.055f, 0.047f, 0.075f, 1f)

    /** Warm paper white: primary buttons and headlines. The accent colours stay reserved for small details. */
    val PAPER = NanoVGColor(0.985f, 0.962f, 0.952f, 1f)

    /** Secondary text that still reads on the bright blossom footage. */
    val SOFT = NanoVGColor(0.92f, 0.89f, 0.92f, 1f)

    fun white(a: Float) = NanoVGColor(1f, 1f, 1f, a.coerceIn(0f, 1f))

    // -----------------------------------------------------------------------------------------
    // Glass
    // -----------------------------------------------------------------------------------------

    /**
     * One-pixel specular rim: bright along the top-left, fading to almost nothing at the bottom-right.
     * This is what makes a flat translucent panel read as a thick pane of glass.
     */
    fun edgeLight(x: Float, y: Float, w: Float, h: Float, r: Float, a: Float, strength: Float = 1f) {
        if (a <= 0.004f || w <= 2f || h <= 2f) return
        val ctx = RenderEngine.nanoVG.handle
        if (ctx == 0L) return
        MemoryStack.stackPush().use { stack ->
            val paint = NVGPaint.malloc(stack)
            NanoVGPaint.linearGradient(
                ctx, x, y, x + w * 0.6f, y + h,
                white(0.55f * strength * a), white(0.05f * strength * a), paint
            )
            nvgBeginPath(ctx)
            nvgRoundedRect(ctx, x + 0.5f, y + 0.5f, w - 1f, h - 1f, (r - 0.5f).coerceAtLeast(0f))
            nvgStrokeWidth(ctx, 1f)
            nvgStrokePaint(ctx, paint)
            nvgStroke(ctx)
        }
    }

    /** Fills a rounded rectangle with the blurred footage, aligned with the backdrop transform. */
    fun frost(x: Float, y: Float, w: Float, h: Float, r: Float, a: Float) {
        if (a <= 0.004f) return
        val sw = ResolutionHelper.scaledWidth
        val sh = ResolutionHelper.scaledHeight
        VideoBackgroundPlayer.renderBlurredArea(
            x, y, w, h, r, sw, sh, a.coerceIn(0f, 1f), 0f,
            MenuScene.bgZoom, MenuScene.bgPanX, MenuScene.bgPanY
        )
    }

    // -----------------------------------------------------------------------------------------
    // Easing
    // -----------------------------------------------------------------------------------------

    fun clamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v

    fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = clamp01((x - e0) / (e1 - e0))
        return t * t * (3f - 2f * t)
    }

    fun easeOutCubic(t: Float): Float {
        val u = 1f - clamp01(t)
        return 1f - u * u * u
    }

    /** Overshoots the target and settles back: the "spring" used for things that pop into place. */
    fun easeOutBack(t: Float, overshoot: Float = 1.7f): Float {
        val c = clamp01(t)
        val u = c - 1f
        return 1f + (overshoot + 1f) * u * u * u + overshoot * u * u
    }

    fun easeOutExpo(t: Float): Float {
        val c = clamp01(t)
        return if (c >= 1f) 1f else 1f - 2f.pow(-10f * c)
    }

    fun easeInOutCubic(t: Float): Float {
        val c = clamp01(t)
        return if (c < 0.5f) 4f * c * c * c else 1f - (-2f * c + 2f).pow(3) / 2f
    }

    private fun wrap01(v: Float): Float = v - floor(v)

    // -----------------------------------------------------------------------------------------
    // Gradients
    // -----------------------------------------------------------------------------------------

    /** Fills the path produced by [path] with a radial gradient. The path lambda receives the NanoVG context. */
    inline fun radialFill(
        cx: Float, cy: Float, innerR: Float, outerR: Float,
        inner: NanoVGColor, outer: NanoVGColor,
        path: (Long) -> Unit
    ) {
        if (outerR <= 0f || (inner.a <= 0.003f && outer.a <= 0.003f)) return
        val ctx = RenderEngine.nanoVG.handle
        if (ctx == 0L) return
        MemoryStack.stackPush().use { stack ->
            val paint = NVGPaint.malloc(stack)
            NanoVGPaint.radialGradient(ctx, cx, cy, innerR, outerR, inner, outer, paint)
            nvgBeginPath(ctx)
            path(ctx)
            nvgFillPaint(ctx, paint)
            nvgFill(ctx)
        }
    }

    /** Soft light spot: [color] at the centre fading to fully transparent at [r]. */
    inline fun glowFill(cx: Float, cy: Float, r: Float, color: NanoVGColor, path: (Long) -> Unit) =
        radialFill(cx, cy, 0f, r, color, color.copy(a = 0f), path)

    // -----------------------------------------------------------------------------------------
    // Pseudo-3D
    // -----------------------------------------------------------------------------------------

    const val SHEAR = 0.22f

    /**
     * Rotates the drawing around its centre. [rotXDeg] tips the top edge away from the viewer, [rotYDeg] pushes the
     * right edge away. Content must still be drawn at its absolute coordinates.
     */
    inline fun withTilt(cx: Float, cy: Float, rotXDeg: Float, rotYDeg: Float, scale: Float, block: () -> Unit) {
        val ctx = RenderEngine.nanoVG.handle
        nvgSave(ctx)
        nvgTranslate(ctx, cx, cy)
        val rx = Math.toRadians(rotXDeg.toDouble()).toFloat()
        val ry = Math.toRadians(rotYDeg.toDouble()).toFloat()
        nvgTransform(
            ctx,
            scale * cos(ry), scale * sin(ry) * SHEAR,
            -scale * sin(rx) * SHEAR, scale * cos(rx),
            0f, 0f
        )
        nvgTranslate(ctx, -cx, -cy)
        try {
            block()
        } finally {
            nvgRestore(ctx)
        }
    }

    // -----------------------------------------------------------------------------------------
    // Typography
    // -----------------------------------------------------------------------------------------

    /** Letter-spaced text (the font renderer has no tracking). Returns the drawn width. */
    fun drawTracked(text: String, x: Float, cy: Float, color: NanoVGColor, size: Float, tracking: Float): Float {
        var cx = x
        for (ch in text) {
            val s = ch.toString()
            NanoVGRenderer.drawVerticallyCenteredString(s, cx, cy, color, size)
            cx += NanoVGRenderer.getTextWidth(s, size) + tracking
        }
        return cx - x - tracking
    }

    fun trackedWidth(text: String, size: Float, tracking: Float): Float {
        var w = 0f
        for (ch in text) w += NanoVGRenderer.getTextWidth(ch.toString(), size) + tracking
        return (w - tracking).coerceAtLeast(0f)
    }

    // -----------------------------------------------------------------------------------------
    // Atmosphere: drifting sakura petals and soft bokeh
    // -----------------------------------------------------------------------------------------

    private class Drifter(
        val x: Float, val y: Float, val depth: Float, val speed: Float,
        val sway: Float, val phase: Float, val spin: Float
    )

    private fun makeDrifters(count: Int, seed: Long): List<Drifter> = List(count) { i ->
        val r = java.util.Random(seed + i * 7919L)
        Drifter(
            x = r.nextFloat(), y = r.nextFloat(),
            depth = 0.25f + r.nextFloat() * 0.75f,
            speed = 0.6f + r.nextFloat(),
            sway = 0.5f + r.nextFloat() * 1.3f,
            phase = r.nextFloat() * 6.2831855f,
            spin = (r.nextFloat() - 0.5f) * 2.4f
        )
    }

    private val petals = makeDrifters(30, 0x5A10L)
    private val bokeh = makeDrifters(9, 0xB0CEL)

    fun drawAtmosphere(w: Float, h: Float, t: Float, scroll: Float, parX: Float, parY: Float, alpha: Float) {
        if (alpha <= 0.004f) return

        for (b in bokeh) {
            val bx = wrap01(b.x + t * 0.0035f * b.speed) * (w + 300f) - 150f - parX * 34f * b.depth
            val by = wrap01(b.y - scroll * 0.06f * b.depth + sin(t * 0.18f * b.sway + b.phase) * 0.02f) * h
            val r = 46f + b.depth * 78f
            val twinkle = 0.55f + 0.45f * sin(t * 0.5f * b.sway + b.phase)
            val tint = if (b.phase > 3.1f) SAKURA else PEACH
            glowFill(bx, by, r, tint.copy(a = 0.07f * twinkle * alpha)) { ctx ->
                org.lwjgl.nanovg.NanoVG.nvgCircle(ctx, bx, by, r)
            }
        }

        val ctx = RenderEngine.nanoVG.handle
        for (p in petals) {
            val fall = wrap01(p.y + t * 0.016f * p.speed - scroll * 0.05f * p.depth)
            val py = fall * (h + 80f) - 40f
            val driftX = p.x * (w + 120f) + sin(t * 0.6f * p.sway + p.phase) * 28f * p.depth + fall * h * 0.20f - parX * 24f * p.depth
            val px = ((driftX + 60f) % (w + 120f) + (w + 120f)) % (w + 120f) - 60f

            val flutter = 0.35f + 0.65f * abs(cos(t * p.spin * 1.7f + p.phase))
            val size = 3.0f + p.depth * 6.5f
            val a = (0.20f + 0.50f * p.depth) * alpha * (0.75f + 0.25f * sin(t * 0.9f + p.phase))
            val color = NanoVGColor(1f, 0.70f + 0.16f * p.depth, 0.80f + 0.06f * p.depth, a.coerceIn(0f, 1f))

            nvgSave(ctx)
            nvgTranslate(ctx, px, py)
            nvgRotate(ctx, p.phase + t * p.spin * 0.6f)
            nvgScale(ctx, 1f, 0.55f * flutter)
            Draw2D.circle(0f, 0f, size, color)
            nvgRestore(ctx)
        }
    }
}
