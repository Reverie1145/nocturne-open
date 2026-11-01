package cn.reverie.gui.mainmenu

import cn.reverie.app.account.AccountManager
import cn.reverie.app.music.CloudMusicManager
import cn.reverie.app.music.player.AudioVisualizer
import cn.reverie.app.music.player.CloudMusicPlayer
import cn.reverie.client.nocturneClient
import cn.reverie.gui.mainmenu.MenuFx.clamp01
import cn.reverie.gui.mainmenu.MenuFx.drawTracked
import cn.reverie.gui.mainmenu.MenuFx.easeInOutCubic
import cn.reverie.gui.mainmenu.MenuFx.easeOutBack
import cn.reverie.gui.mainmenu.MenuFx.easeOutCubic
import cn.reverie.gui.mainmenu.MenuFx.easeOutExpo
import cn.reverie.gui.mainmenu.MenuFx.edgeLight
import cn.reverie.gui.mainmenu.MenuFx.frost
import cn.reverie.gui.mainmenu.MenuFx.glowFill
import cn.reverie.gui.mainmenu.MenuFx.radialFill
import cn.reverie.gui.mainmenu.MenuFx.smoothstep
import cn.reverie.gui.mainmenu.MenuFx.trackedWidth
import cn.reverie.gui.mainmenu.MenuFx.white
import cn.reverie.gui.theme.MenuTheme
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.HudVectorIcons
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.render.video.VideoBackgroundPlayer
import org.lwjgl.nanovg.NanoVG.nvgCircle
import org.lwjgl.nanovg.NanoVG.nvgGlobalAlpha
import org.lwjgl.nanovg.NanoVG.nvgRect
import org.lwjgl.nanovg.NanoVG.nvgRestore
import org.lwjgl.nanovg.NanoVG.nvgRoundedRect
import org.lwjgl.nanovg.NanoVG.nvgSave
import org.lwjgl.nanovg.NanoVG.nvgScale
import org.lwjgl.nanovg.NanoVG.nvgTranslate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal typealias Item = MainMenuScreen.MenuItem

/** Headlines and primary text. */
internal val TEXT = MenuFx.PAPER
/** Secondary text: readable on top of the bright footage. */
internal val SOFT = MenuFx.SOFT
internal val MUTED = MenuTheme.TEXT_MUTED
internal val ACCENT = MenuTheme.ACCENT
internal val WARM = MenuTheme.ACCENT_WARM
internal val INK = MenuTheme.INK
internal val INK_DEEP = MenuTheme.INK_DEEP
internal val PANEL = MenuFx.PANEL

internal fun NanoVGColor.mulA(f: Float): NanoVGColor = multiplyAlpha(f)
internal fun <T> head(block: () -> T): T = MenuTheme.heading(block)

internal fun inRect(r: FloatArray, mx: Float, my: Float): Boolean =
    r[2] > 0f && mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]

internal fun setRect(r: FloatArray, x: Float, y: Float, w: Float, h: Float) {
    r[0] = x; r[1] = y; r[2] = w; r[3] = h
}

/** Layout metrics for one frame. The canvas is ~1280x720 on a 16:9 window and scales with height. */
internal class MenuFrame(val w: Float, val h: Float) {
    val k = (h / 720f).coerceIn(0.8f, 1.4f)
    private val kt = k.coerceAtMost(1.25f)
    val margin = (w * 0.065f).coerceIn(56f, 130f)
    val contentW = w - margin * 2f
    val top = 108f * k.coerceAtMost(1.2f)
    /** Header of the child screens: eyebrow at [top], then the title and its subtitle. */
    val titleCy = top + 58f * kt
    val subCy = titleCy + 46f * kt
    /** Where the first row of content starts. */
    val bodyY = subCy + 70f * k
}

/**
 * Arrival / departure phase of a child-screen element. [rel] is negative while the screen is still arriving from
 * below and grows towards 0 as it settles; later [order] values arrive later, which gives the staggered fly-in.
 */
internal class Stage(val vis: Float, val dy: Float, val rot: Float)

internal fun stage(rel: Float, order: Int, h: Float): Stage {
    val a = if (rel < 0f) rel * (1f + order * 0.30f) else rel
    return Stage(1f - smoothstep(0.03f, 0.92f, abs(a)), -a * h * 0.28f, -a * 30f)
}

internal inline fun rise(e: Float, dy: Float, block: () -> Unit) {
    val tf = RenderEngine.nanoVG.transform
    tf.save()
    tf.translate(0f, (1f - e) * dy)
    try {
        block()
    } finally {
        tf.restore()
    }
}

// ---------------------------------------------------------------------------------------------
// Frame entry point
// ---------------------------------------------------------------------------------------------

private const val EXIT_MS = 400f

fun MainMenuScreen.renderMainMenuImpl(mouseX: Float, mouseY: Float, partialTicks: Float) {
    val intro = entranceAnim.value
    // The very first frames stay blank; when returning from a child screen the scene is already on screen.
    if (!MenuScene.introPlayed && intro <= 0.001f) return
    if (intro >= 0.999f) MenuScene.introPlayed = true
    val bgIntro = if (MenuScene.introPlayed) 1f else intro

    val w = ResolutionHelper.scaledWidth
    val h = ResolutionHelper.scaledHeight
    if (w <= 0f || h <= 0f || RenderEngine.nanoVG.handle == 0L) return

    val nowNs = System.nanoTime()
    val dt = if (lastFrameNs == 0L) 1f / 60f else ((nowNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
    lastFrameNs = nowNs
    val now = System.currentTimeMillis()

    val exitT = if (exitStartMs > 0L) easeInOutCubic((now - exitStartMs) / EXIT_MS) else 0f
    // Leaving dollies the camera into the footage; the next screen picks the motion up from there.
    MenuScene.pushTarget = if (exitStartMs > 0L) 1f else 0f
    MenuScene.step(0f, dt, mouseX, mouseY, w, h)
    updateInteraction(mouseX, mouseY, dt)
    for (item in items) item.clearBounds()
    MenuChrome.beginFrame()

    val f = MenuFrame(w, h)
    drawMenuBackdrop(f, bgIntro, now, scroll, parX, parY)

    // The content moves towards the viewer while it fades: the camera is pushing through it.
    val ctx = RenderEngine.nanoVG.handle
    nvgSave(ctx)
    if (exitT > 0f) {
        nvgGlobalAlpha(ctx, (1f - exitT * exitT).coerceIn(0f, 1f))
        val s = 1f + 0.07f * exitT
        nvgTranslate(ctx, w * 0.5f, h * 0.62f)
        nvgScale(ctx, s, s)
        nvgTranslate(ctx, -w * 0.5f, -h * 0.62f)
    }
    renderHero(f, intro, now)
    nvgRestore(ctx)

    val navReveal = if (MenuScene.introPlayed) 1f else easeOutCubic((now - enterStartMs - 500f) / 900f)
    renderMenuNav(f, mouseX, mouseY, bgIntro * (1f - exitT), navReveal, null, quickActions, now - enterStartMs)

    if (exitStartMs > 0L && exitT >= 1f) {
        val action = pendingAction
        pendingAction = null
        action?.invoke()
    }
}

// ---------------------------------------------------------------------------------------------
// Interaction
// ---------------------------------------------------------------------------------------------

private fun MainMenuScreen.updateInteraction(mx: Float, my: Float, dt: Float) {
    val moved = mx != lastMouseX || my != lastMouseY
    lastMouseX = mx
    lastMouseY = my

    var hovered: Item? = null
    for (item in items) {
        if (item.contains(mx, my)) hovered = item
    }
    if (moved) {
        showFocus = false
        if (hovered != null) focusItem = hovered
    }

    val k = 1f - exp(-dt * 11f)
    for (item in items) {
        val hot = if (showFocus) item === focusItem else item === hovered
        item.hoverAnim.animateTo(if (hot && pendingAction == null) 1f else 0f)
        item.pressAnim.animateTo(if (item.isPressed) 1f else 0f)

        var tx = 0f
        var ty = 0f
        if (item === hovered && item.w > 0f && item.h > 0f) {
            tx = ((mx - item.x) / item.w * 2f - 1f).coerceIn(-1f, 1f)
            ty = ((my - item.y) / item.h * 2f - 1f).coerceIn(-1f, 1f)
        }
        item.tiltX += (tx - item.tiltX) * k
        item.tiltY += (ty - item.tiltY) * k
    }
}

// ---------------------------------------------------------------------------------------------
// Backdrop
// ---------------------------------------------------------------------------------------------

/**
 * The footage, its depth of field and the lighting. Shared by the main menu and its child screens so the scene
 * stays continuous across them: [scroll] is the scene depth (0 = main menu, 1 = child screens) and
 * [MenuScene.push] is the camera dolly used while moving between them.
 */
internal fun drawMenuBackdrop(f: MenuFrame, intro: Float, now: Long, scroll: Float, parX: Float, parY: Float) {
    val w = f.w
    val h = f.h
    val s = scroll.coerceIn(0f, 1f)
    val d = smoothstep(0f, 1f, s)
    val push = MenuScene.push
    val t = now / 1000f
    val tf = RenderEngine.nanoVG.transform

    // First launch: the camera starts close and dark, then pulls back while the picture comes up.
    val introE = easeOutExpo(intro)
    val vis = easeOutCubic(min(1f, intro * 1.7f))

    Draw2D.rect(0f, 0f, w, h, NanoVGColor(0.05f, 0.03f, 0.06f, 1f))

    val zoom = 1.05f + 0.07f * d + (1f - introE) * 0.16f + 0.11f * push
    val shiftX = -parX * 16f + sin(t * 0.21f) * 5f
    val shiftY = -parY * 10f - s * 9f + cos(t * 0.17f) * 3f
    MenuScene.bgZoom = zoom
    MenuScene.bgPanX = shiftX
    MenuScene.bgPanY = shiftY
    tf.save()
    tf.translate(w * 0.5f + shiftX, h * 0.5f + shiftY)
    tf.scale(zoom, zoom)
    tf.translate(-w * 0.5f, -h * 0.5f)
    VideoBackgroundPlayer.render(w, h, vis)
    // Depth of field: the footage defocuses as the scene moves from the main menu to the screens behind it.
    val blur = (0.90f * d + 0.45f * push).coerceIn(0f, 1f)
    if (blur > 0.01f) {
        VideoBackgroundPlayer.renderBlurredArea(0f, 0f, w, h, 0f, w, h, (blur * vis).coerceIn(0f, 1f))
    }
    tf.restore()

    // Sun bloom from the top-left corner of the footage, fading as the scene deepens.
    glowFill(0f, 0f, w * 0.6f, WARM.mulA(0.12f * (1f - d) * vis)) { c -> nvgRect(c, 0f, 0f, w, h) }

    // Light follows the pointer, which makes every glass surface feel lit rather than printed.
    val lx = w * (0.5f + 0.5f * parX)
    val ly = h * (0.5f + 0.5f * parY)
    glowFill(lx, ly, 420f, white(0.085f * vis)) { c -> nvgRect(c, 0f, 0f, w, h) }

    // Child screens sit on a calmer plate; the main menu stays bright so the blossoms keep their colour.
    Draw2D.rect(0f, 0f, w, h, INK.mulA((0.28f * d + 0.12f * push) * vis))

    // Vignette and top/bottom falloff.
    radialFill(w * 0.5f, h * 0.5f, h * 0.55f, max(w, h) * 0.85f, INK.mulA(0f), INK.mulA((0.40f + 0.20f * (1f - introE)) * vis)) { c ->
        nvgRect(c, 0f, 0f, w, h)
    }
    Draw2D.gradientLinear(0f, 0f, w, h * 0.22f, 0f, INK.mulA(0.38f * vis), INK.mulA(0f))
    Draw2D.gradientLinear(0f, h * 0.50f, w, h * 0.50f, 0f, INK.mulA(0f), INK.mulA((0.58f + 0.10f * d) * vis))

    // A soft pool behind the title lockup, nothing more.
    val hs = (1f - d) * vis
    glowFill(f.margin + 200f, h * 0.80f, 560f, INK.mulA(0.50f * hs)) { c -> nvgRect(c, 0f, 0f, w, h) }

    // Opening veil: the whole picture fades up from black.
    val veil = (1f - introE) * 0.55f + push * 0.22f
    if (veil > 0.004f) Draw2D.rect(0f, 0f, w, h, NanoVGColor(0.02f, 0.01f, 0.03f, veil))

    MenuFx.drawAtmosphere(w, h, t, scroll, parX, parY, vis)
}

// ---------------------------------------------------------------------------------------------
// Shared surface language
// ---------------------------------------------------------------------------------------------

/**
 * Thick frosted glass: blurred footage, a plum tint, a bright top sheen, a darker underside and a specular rim.
 * [bgA] is the tint strength (higher = more opaque, used by modals).
 */
internal fun drawSurface(x: Float, y: Float, w: Float, h: Float, r: Float, a: Float, hv: Float, bgA: Float = 0.50f) {
    if (a <= 0.004f) return
    Draw2D.shadow(x, y, w, h, r, 40f, NanoVGColor(0f, 0f, 0f, (0.34f + 0.10f * hv) * a), 18f)
    frost(x, y, w, h, r, 0.95f * a)
    Draw2D.roundedRect(x, y, w, h, r, PANEL.mulA(bgA * 0.78f * a))
    Draw2D.gradientLinear(x, y, w, h * 0.5f, r, white(0.09f + 0.04f * hv).mulA(a), white(0f))
    Draw2D.gradientLinear(x, y + h * 0.62f, w, h * 0.38f, r, NanoVGColor(0f, 0f, 0f, 0f), NanoVGColor(0f, 0f, 0f, 0.16f * a))
    edgeLight(x, y, w, h, r, a, 0.85f + 0.6f * hv)
}

internal fun MainMenuScreen.drawFocusRing(item: Item, x: Float, y: Float, w: Float, h: Float, r: Float) {
    if (showFocus && item === focusItem) {
        Draw2D.roundedOutline(x - 3f, y - 3f, w + 6f, h + 6f, r + 3f, ACCENT.mulA(0.95f), 1.6f)
    }
}

internal fun placeItem(item: Item, x: Float, y: Float, w: Float, h: Float) {
    item.x = x; item.y = y; item.w = w; item.h = h
}

// ---------------------------------------------------------------------------------------------
// Main menu
// ---------------------------------------------------------------------------------------------

private fun greeting(): String {
    val hour = LocalTime.now().hour
    return when {
        hour in 5..11 -> "Good morning"
        hour in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
}

/**
 * A title card: one wordmark, one line of context and one decision (play), with the rest of the client one glance
 * away in the dock. Everything is timed from [MainMenuScreen.enterStartMs] so the entrance reads as one sequence:
 * the camera settles, the eyebrow is wiped in, the letters tighten into place, then the buttons and the dock spring up.
 */
private fun MainMenuScreen.renderHero(f: MenuFrame, intro: Float, now: Long) {
    val k = f.k
    val x = f.margin
    val elapsed = (now - enterStartMs).toFloat()
    val exitT = if (exitStartMs > 0L) easeInOutCubic((now - exitStartMs) / EXIT_MS) else 0f
    val master = intro.coerceIn(0f, 1f) * (1f - exitT * exitT)
    if (master <= 0.004f) return

    val btnH = 56f * k
    val ctaTop = f.h - 108f * k - btnH
    val subCy = ctaTop - 36f * k
    val wordSize = 100f * k
    val wordCy = subCy - 34f * k - wordSize * 0.46f
    val eyeCy = wordCy - wordSize * 0.5f - 30f * k

    val tf = RenderEngine.nanoVG.transform
    // Returning from another screen the content settles in from slightly too close, as if the camera pulls back.
    val settle = 1f + 0.035f * (1f - easeOutCubic(intro))
    val pivotY = f.h * 0.62f
    tf.save()
    tf.translate(x, pivotY)
    tf.scale(settle, settle)
    tf.translate(-x, -pivotY)
    // The lockup drifts a little against the pointer, in front of the footage.
    tf.translate(-parX * 9f, -parY * 5f)

    drawEyebrow(f, x, eyeCy, elapsed, master, now)
    drawWordmark(x, wordCy, wordSize, elapsed, master, now)

    val sub = easeOutCubic((elapsed - 760f) / 800f)
    rise(sub, 16f) {
        NanoVGRenderer.drawVerticallyCenteredString(
            "Pick up where you left off.", x + 2f, subCy, SOFT.mulA(0.90f * master * sub), 18f * k
        )
    }

    val primaryW = 232f * k
    val ghostW = 196f * k
    val gap = 12f * k
    drawHeroButton(heroPlay, true, x, ctaTop, primaryW, btnH, easeOutBack((elapsed - 950f) / 700f), master)
    drawHeroButton(heroMulti, false, x + primaryW + gap, ctaTop, ghostW, btnH, easeOutBack((elapsed - 1080f) / 700f), master)
    tf.restore()

    renderDock(f, master, elapsed, now, ctaTop + btnH * 0.5f)
}

/** Small tracked line (release, greeting, name), wiped in from the left behind a pulsing dot. */
private fun MainMenuScreen.drawEyebrow(f: MenuFrame, x: Float, cy: Float, elapsed: Float, master: Float, now: Long) {
    val e = easeOutCubic((elapsed - 120f) / 600f)
    if (e <= 0.004f) return
    val accountName = try { AccountManager.currentAccount?.name } catch (_: Throwable) { null }
    val line = buildString {
        append("RELEASE ${nocturneClient.RELEASE_VERSION}")
        append("   \u00b7   ")
        append(greeting().uppercase())
        if (accountName != null) append(", ${accountName.uppercase()}")
    }
    val pulse = (0.7f + 0.3f * sin(now / 700.0)).toFloat()
    Draw2D.circle(x + 3f, cy, 3f * (0.4f + 0.6f * e), ACCENT.mulA(master * e))
    Draw2D.glow(x, cy - 3f, 6f, 6f, 3f, 8f, ACCENT.mulA(0.5f * pulse * master * e))
    head {
        val fitted = if (trackedWidth(line, 10.5f, 2.2f) > f.w * 0.46f) line.take(48) else line
        val tw = trackedWidth(fitted, 10.5f, 2.2f)
        val wipe = easeOutExpo((elapsed - 220f) / 1000f)
        NanoVGRenderer.withScissor(x + 14f, cy - 12f, (tw + 12f) * wipe, 24f) {
            drawTracked(fitted, x + 18f, cy, SOFT.mulA(0.92f * master), 10.5f, 2.2f)
        }
    }
}

/**
 * Mixed-case wordmark. On entry the tracking tightens from loose to tight while each letter rises out of a mask
 * and sharpens; afterwards a soft glow breathes behind it.
 */
private fun drawWordmark(x: Float, cy: Float, size: Float, elapsed: Float, a: Float, now: Long) {
    val text = "Nocturne"
    val settle = easeOutExpo((elapsed - 200f) / 1700f)
    // Loose (+0.22em) to tight (-0.035em).
    val tracking = size * (0.22f + (-0.035f - 0.22f) * settle)
    head {
        val widths = FloatArray(text.length) { NanoVGRenderer.getTextWidth(text[it].toString(), size) }
        val total = widths.sum() + tracking * (text.length - 1)

        val breathe = (0.85f + 0.15f * sin(now / 1500.0)).toFloat()
        glowFill(x + total * 0.4f, cy, total * 0.85f, ACCENT.mulA(0.11f * breathe * settle * a)) { c ->
            nvgCircle(c, x + total * 0.4f, cy, total * 0.85f)
        }

        NanoVGRenderer.withScissor(x - 14f, cy - size * 0.72f, total + 60f, size * 1.44f) {
            var cx = x
            for (i in text.indices) {
                val t = easeOutExpo((elapsed - 260f - i * 80f) / 1100f)
                val rise = (1f - t) * size * 0.65f
                val blur = (1f - t) * 14f
                val s = text[i].toString()
                NanoVGRenderer.drawVerticallyCenteredString(s, cx, cy + rise + 5f, NanoVGColor(0f, 0f, 0f, 0.55f * t * a), size, false, 14f)
                NanoVGRenderer.drawVerticallyCenteredString(s, cx, cy + rise, TEXT.mulA(t * a), size, false, blur)
                cx += widths[i] + tracking
            }
        }
    }
}

/** Hero button: springs up on entry and leans towards the pointer while hovered. */
private fun MainMenuScreen.drawHeroButton(
    item: Item, primary: Boolean, x: Float, y: Float, w: Float, h: Float, pop: Float, master: Float
) {
    val alpha = master * clamp01(pop * 1.2f)
    if (alpha <= 0.004f) return
    placeItem(item, x, y, w, h)

    val hv = item.hoverAnim.value
    val tf = RenderEngine.nanoVG.transform
    val cx = x + w * 0.5f
    val cy = y + h * 0.5f
    val scale = (0.86f + 0.14f * pop) * (1f + 0.035f * hv)
    tf.save()
    tf.translate(cx + item.tiltX * 5f * hv, cy + item.tiltY * 3f * hv + (1f - pop) * 28f)
    tf.scale(scale, scale)
    tf.translate(-cx, -cy)
    MenuTheme.drawButton(
        x, y, w, h,
        label = item.title,
        style = if (primary) MenuTheme.ButtonStyle.PRIMARY else MenuTheme.ButtonStyle.GHOST,
        hover = hv,
        press = item.pressAnim.value,
        alpha = alpha,
        withArrow = true
    )
    drawFocusRing(item, x, y, w, h, h * 0.5f)
    tf.restore()
}

// ---------------------------------------------------------------------------------------------
// Dock: quick access to the rest of the client, with pointer magnification
// ---------------------------------------------------------------------------------------------

private fun MainMenuScreen.renderDock(f: MenuFrame, master: Float, elapsed: Float, now: Long, rowCy: Float) {
    val dockPop = easeOutBack((elapsed - 1150f) / 700f, 1.3f)
    val a = master * clamp01(dockPop * 1.3f)
    if (a <= 0.004f) return
    val k = f.k
    val dock = listOf(dockAlt, dockClick, dockMusic, dockAgent, dockSettings, dockQuit)
    val base = 44f * k
    val gap = 6f * k
    val pad = 9f * k
    val divider = 14f * k
    val capH = base + pad * 2f
    val rightX = f.w - f.margin
    val mx = lastMouseX
    val my = lastMouseY
    val capTopY = rowCy - capH * 0.5f

    // Unmagnified centres decide how strongly each icon is pulled towards the pointer.
    val baseTotal = dock.size * base + (dock.size - 1) * gap + pad * 2f + divider
    var cursor = rightX - baseTotal + pad
    val pointerNear = !mx.isNaN() && !my.isNaN() && my > capTopY - 70f && my < capTopY + capH + 24f &&
        mx > rightX - baseTotal - 40f && mx < rightX + 40f
    for ((i, item) in dock.withIndex()) {
        if (i == dock.size - 1) cursor += divider
        val c = cursor + base * 0.5f
        val target = if (pointerNear && pendingAction == null) 1f + 0.30f * exp(-((mx - c) * (mx - c)) / (2f * 52f * 52f)) else 1f
        item.dockMag += (target - item.dockMag) * 0.24f
        cursor += base + gap
    }

    var total = pad * 2f + divider + (dock.size - 1) * gap
    for (item in dock) total += base * item.dockMag
    val capX = rightX - total
    val tf = RenderEngine.nanoVG.transform

    tf.save()
    tf.translate(0f, (1f - dockPop) * 34f)

    Draw2D.shadow(capX, capTopY, total, capH, capH * 0.5f, 34f, NanoVGColor(0f, 0f, 0f, 0.34f * a), 14f)
    frost(capX, capTopY, total, capH, capH * 0.5f, 0.95f * a)
    Draw2D.roundedRect(capX, capTopY, total, capH, capH * 0.5f, NanoVGColor(0.07f, 0.05f, 0.09f, 0.34f * a))
    Draw2D.gradientLinear(capX, capTopY, total, capH * 0.55f, capH * 0.5f, white(0.11f).mulA(a), white(0f))
    edgeLight(capX, capTopY, total, capH, capH * 0.5f, a, 0.95f)

    var tooltipItem: Item? = null
    var tooltipCx = 0f
    var tooltipTop = 0f
    var x = capX + pad
    val bottom = capTopY + capH - pad
    for ((i, item) in dock.withIndex()) {
        if (i == dock.size - 1) {
            Draw2D.rect(x + divider * 0.5f - 0.5f, capTopY + capH * 0.28f, 1f, capH * 0.44f, white(0.18f).mulA(a))
            x += divider
        }
        // Each icon pops out of the capsule on its own beat.
        val pop = easeOutBack((elapsed - 1250f - i * 70f) / 650f, 1.9f)
        val ia = a * clamp01(pop * 1.4f)
        val s = base * item.dockMag
        val hv = item.hoverAnim.value
        val pv = item.pressAnim.value
        val ty = bottom - s - 3f * hv
        placeItem(item, x, ty, s, s)

        val cx = x + s * 0.5f
        val cy = ty + s * 0.5f + (1f - pop) * 26f
        val press = (0.55f + 0.45f * pop) * (1f - 0.08f * pv)
        tf.save()
        tf.translate(cx, cy); tf.scale(press, press); tf.translate(-cx, -cy)

        // Bare line icons: no coloured tiles. Hover is a soft disc behind the glyph, nothing else changes.
        val r = s * 0.5f
        if (hv > 0.01f) {
            Draw2D.circle(cx, cy, s * 0.5f, (if (item.isDanger) MenuFx.DANGER else NanoVGColor.WHITE).mulA(0.16f * hv * ia))
        }
        val iconColor = if (item.isDanger) TEXT.lerp(MenuFx.DANGER.lerp(NanoVGColor.WHITE, 0.25f), hv) else TEXT
        drawItemIcon(item.iconName, cx, cy, s * 0.46f, iconColor.mulA((0.80f + 0.20f * hv) * ia))
        tf.restore()

        drawFocusRing(item, x, ty, s, s, r)
        if (hv > 0.5f) {
            tooltipItem = item
            tooltipCx = cx
            tooltipTop = ty
        }
        x += s + gap
    }

    tooltipItem?.let { item ->
        val tw = head { NanoVGRenderer.getTextWidth(item.title, 12.5f) } + 26f
        val th = 28f
        val tx = (tooltipCx - tw * 0.5f).coerceIn(8f, f.w - tw - 8f)
        val tyy = tooltipTop - th - 10f
        Draw2D.shadow(tx, tyy, tw, th, th * 0.5f, 18f, NanoVGColor(0f, 0f, 0f, 0.40f * a), 6f)
        frost(tx, tyy, tw, th, th * 0.5f, a)
        Draw2D.roundedRect(tx, tyy, tw, th, th * 0.5f, NanoVGColor(0.07f, 0.05f, 0.09f, 0.62f * a))
        edgeLight(tx, tyy, tw, th, th * 0.5f, a, 0.8f)
        head { NanoVGRenderer.drawCenteredString(item.title, tx + tw * 0.5f, tyy + th * 0.5f, TEXT.mulA(a), 12.5f) }
    }

    // Now playing sits just above the dock, right aligned.
    drawNowPlayingPill(rightX, capTopY - 62f * k, 42f, a)
    tf.restore()
}

/** Compact now-playing capsule. Returns false when nothing is playing. */
private fun drawNowPlayingPill(rx: Float, cy: Float, pillH: Float, a: Float): Boolean {
    if (a <= 0.004f) return false
    val player = try { CloudMusicManager.player } catch (_: Throwable) { return false }
    val track = player.currentTrack ?: return false
    val state = player.playbackState
    if (state == CloudMusicPlayer.PlaybackState.IDLE || state == CloudMusicPlayer.PlaybackState.ERROR) return false
    val playing = state == CloudMusicPlayer.PlaybackState.PLAYING

    var title = ""
    var artist = ""
    var textW = 0f
    cjk {
        title = NanoVGRenderer.trimWithEllipsis(track.name, 190f, 12f)
        artist = NanoVGRenderer.trimWithEllipsis(track.artists, 190f, 10.5f)
        textW = max(NanoVGRenderer.getTextWidth(title, 12f), NanoVGRenderer.getTextWidth(artist, 10.5f))
    }
    val barsW = 12.5f
    val pillW = 18f + barsW + 12f + textW + 20f
    val x = rx - pillW
    val y = cy - pillH * 0.5f
    frost(x, y, pillW, pillH, pillH * 0.5f, 0.95f * a)
    Draw2D.roundedRect(x, y, pillW, pillH, pillH * 0.5f, NanoVGColor(0.07f, 0.05f, 0.09f, 0.36f * a))
    edgeLight(x, y, pillW, pillH, pillH * 0.5f, a, 0.85f)

    val bands = AudioVisualizer.bands
    for ((i, band) in intArrayOf(2, 6, 10).withIndex()) {
        val level = if (playing) (bands.getOrElse(band) { 0f } * 1.6f).coerceIn(0f, 1f) else 0f
        val bh = 3f + 14f * level
        Draw2D.roundedRect(x + 18f + i * 5f, cy - bh * 0.5f, 2.5f, bh, 1.25f, ACCENT.mulA((0.65f + 0.35f * level) * a))
    }
    val tx = x + 18f + barsW + 12f
    cjk {
        NanoVGRenderer.drawVerticallyCenteredString(title, tx, cy - 7f, TEXT.mulA(0.95f * a), 12f)
        NanoVGRenderer.drawVerticallyCenteredString(artist, tx + 0.5f, cy + 6f, SOFT.mulA(0.70f * a), 10.5f)
    }
    val progress = if (player.durationMs > 0L) (player.currentPositionMs.toFloat() / player.durationMs).coerceIn(0f, 1f) else 0f
    val lineX = x + pillH * 0.5f
    val lineW = pillW - pillH
    val lineY = y + pillH - 6f
    Draw2D.roundedRect(lineX, lineY, lineW, 2f, 1f, white(0.14f).mulA(a))
    if (progress > 0.002f) Draw2D.roundedRect(lineX, lineY, lineW * progress, 2f, 1f, TEXT.mulA(0.92f * a))
    return true
}

// ---------------------------------------------------------------------------------------------
// Child-screen header
// ---------------------------------------------------------------------------------------------

/** Eyebrow, large title and subtitle for the screens that open from the main menu. */
internal fun pageHeader(f: MenuFrame, rel: Float, intro: Float, num: String, name: String, title: String, sub: String) {
    val kt = f.k.coerceAtMost(1.25f)
    val x = f.margin
    val eyeCy = f.top
    val tf = RenderEngine.nanoVG.transform

    val s0 = stage(rel, 0, f.h)
    val a0 = s0.vis * intro
    if (a0 > 0.004f) {
        tf.save(); tf.translate(0f, s0.dy * 0.5f)
        Draw2D.circle(x + 3f, eyeCy, 3f, ACCENT.mulA(a0))
        Draw2D.glow(x, eyeCy - 3f, 6f, 6f, 3f, 8f, ACCENT.mulA(0.45f * a0))
        val label = if (num.isBlank()) name else "$num   /   $name"
        head { drawTracked(label, x + 18f, eyeCy, SOFT.mulA(0.92f * a0), 10.5f, 2.2f) }
        tf.restore()
    }
    val s1 = stage(rel, 1, f.h)
    val a1 = s1.vis * intro
    if (a1 > 0.004f) {
        tf.save(); tf.translate(0f, s1.dy * 0.5f)
        val size = 60f * kt
        val tracking = -size * 0.03f
        head {
            drawTracked(title, x, f.titleCy + 4f, NanoVGColor(0f, 0f, 0f, 0.30f * a1), size, tracking)
            drawTracked(title, x, f.titleCy, TEXT.mulA(a1), size, tracking)
        }
        tf.restore()
    }
    val s2 = stage(rel, 2, f.h)
    val a2 = s2.vis * intro
    if (a2 > 0.004f) {
        tf.save(); tf.translate(0f, s2.dy * 0.5f)
        NanoVGRenderer.drawVerticallyCenteredString(sub, x + 1f, f.subCy, SOFT.mulA(0.88f * a2), 16f)
        tf.restore()
    }
}

internal fun drawSignOutIcon(cx: Float, cy: Float, size: Float, color: NanoVGColor) {
    val ctx = RenderEngine.nanoVG.handle
    if (ctx == 0L || color.a <= 0.003f) return
    val s = size / 24f
    org.lwjgl.nanovg.NanoVG.nvgLineCap(ctx, org.lwjgl.nanovg.NanoVG.NVG_ROUND)
    org.lwjgl.nanovg.NanoVG.nvgLineJoin(ctx, org.lwjgl.nanovg.NanoVG.NVG_ROUND)
    val path = cn.reverie.render.nanovg.NanoVGPath(ctx)
    val sw = 1.85f * max(s, 0.7f)
    path.begin()
    path.moveTo(cx - 1f * s, cy - 8f * s); path.lineTo(cx - 8f * s, cy - 8f * s)
    path.lineTo(cx - 8f * s, cy + 8f * s); path.lineTo(cx - 1f * s, cy + 8f * s)
    path.stroke(color, sw)
    path.begin(); path.moveTo(cx - 2f * s, cy); path.lineTo(cx + 8f * s, cy); path.stroke(color, sw)
    path.begin()
    path.moveTo(cx + 4f * s, cy - 4f * s); path.lineTo(cx + 8f * s, cy); path.lineTo(cx + 4f * s, cy + 4f * s)
    path.stroke(color, sw)
}

internal fun drawItemIcon(name: String, cx: Float, cy: Float, size: Float, color: NanoVGColor) {
    if (name == "signout") drawSignOutIcon(cx, cy, size, color) else drawDetailedIcon(name, cx, cy, size, color)
}

// ---------------------------------------------------------------------------------------------
// Persistent chrome: the top bar
// ---------------------------------------------------------------------------------------------

/**
 * Top bar: brand (a button back to the previous screen), an optional breadcrumb, quick icon buttons (main menu
 * only) and the account capsule. [reveal] drives the slide-in; [elapsed] staggers the quick buttons.
 */
internal fun renderMenuNav(
    f: MenuFrame, mx: Float, my: Float, intro: Float, reveal: Float,
    crumb: String?, quick: List<MainMenuScreen.QuickAction>? = null, elapsed: Long = 100000L
) {
    val e = reveal.coerceIn(0f, 1f)
    val a = e * intro
    if (a <= 0.004f) return
    val cy = 40f
    val tf = RenderEngine.nanoVG.transform

    Draw2D.gradientLinear(0f, 0f, f.w, 96f, 0f, INK.mulA(0.42f * a), INK.mulA(0f))

    tf.save()
    tf.translate(0f, -(1f - e) * 14f)

    // Brand (and breadcrumb)
    val brandW = 26f + head { NanoVGRenderer.getTextWidth("Nocturne", 17f) }
    setRect(MenuChrome.brandRect, f.margin - 8f, cy - 18f, brandW + 16f, 36f)
    MenuChrome.brandHover.animateTo(if (crumb != null && inRect(MenuChrome.brandRect, mx, my)) 1f else 0f)
    val bh = MenuChrome.brandHover.value
    HudVectorIcons.drawMoon(f.margin, cy - 8f, 16f, TEXT.mulA(a))
    head { NanoVGRenderer.drawVerticallyCenteredString("Nocturne", f.margin + 26f, cy, TEXT.lerp(NanoVGColor.WHITE, bh).mulA((0.92f + 0.08f * bh) * a), 17f) }
    if (crumb != null) {
        val cx = f.margin + brandW + 14f
        head { NanoVGRenderer.drawVerticallyCenteredString("/", cx, cy, SOFT.mulA(0.45f * a), 17f) }
        head { NanoVGRenderer.drawVerticallyCenteredString(crumb, cx + 18f, cy, SOFT.mulA(0.95f * a), 14f) }
    } else {
        MenuChrome.brandRect[2] = 0f
    }

    // Account capsule (right edge)
    val account = try { AccountManager.currentAccount } catch (_: Throwable) { null }
    val name = account?.name ?: "No account"
    var shown = name
    var nameW = 0f
    cjk {
        shown = NanoVGRenderer.trimWithEllipsis(name, 120f, 12f)
        nameW = NanoVGRenderer.getTextWidth(shown, 12f)
    }
    val chipH = 38f
    val chipW = 8f + 26f + 10f + nameW + 18f
    val chipX = f.w - f.margin - chipW
    val chipY = cy - chipH * 0.5f
    MenuChrome.accountHover.animateTo(if (inRect(MenuChrome.accountRect, mx, my)) 1f else 0f)
    val ah = MenuChrome.accountHover.value
    setRect(MenuChrome.accountRect, chipX, chipY, chipW, chipH)
    frost(chipX, chipY, chipW, chipH, chipH * 0.5f, 0.95f * a)
    Draw2D.roundedRect(chipX, chipY, chipW, chipH, chipH * 0.5f, NanoVGColor(0.07f, 0.05f, 0.09f, (0.34f - 0.10f * ah) * a))
    Draw2D.roundedRect(chipX, chipY, chipW, chipH, chipH * 0.5f, white(0.07f * ah).mulA(a))
    edgeLight(chipX, chipY, chipW, chipH, chipH * 0.5f, a, 0.85f + 0.5f * ah)
    val badge = 26f
    val bx = chipX + 6f
    Draw2D.gradientDiagonal(bx, cy - badge * 0.5f, badge, badge, badge * 0.5f, ACCENT.mulA(0.60f * a), WARM.mulA(0.60f * a))
    HudVectorIcons.drawMoon(bx + 5.5f, cy - 7.5f, 15f, white(0.98f).mulA(a))
    cjk { NanoVGRenderer.drawVerticallyCenteredString(shown, bx + badge + 10f, cy, TEXT.mulA(0.94f * a), 12f) }

    // Quick icon buttons, left of the account capsule
    if (quick != null) {
        var qx = chipX - 14f
        var tooltip: String? = null
        var tooltipCx = 0f
        for (i in quick.indices.reversed()) {
            val q = quick[i]
            val size = 38f
            qx -= size
            val pop = easeOutBack((elapsed - 1300f - i * 90f) / 600f, 1.7f)
            val qa = a * clamp01(pop * 1.3f)
            setRect(MenuChrome.quickRects[i], qx, cy - size * 0.5f, size, size)
            val hover = inRect(MenuChrome.quickRects[i], mx, my)
            MenuChrome.quickHover[i].animateTo(if (hover) 1f else 0f)
            val hv = MenuChrome.quickHover[i].value
            val ccx = qx + size * 0.5f
            val s = (0.6f + 0.4f * pop) * (1f + 0.08f * hv)
            tf.save()
            tf.translate(ccx, cy); tf.scale(s, s); tf.translate(-ccx, -cy)
            // Bare icon; a soft disc appears under the pointer.
            if (hv > 0.01f) Draw2D.circle(ccx, cy, size * 0.5f, white(0.14f * hv).mulA(qa))
            drawItemIcon(q.iconName, ccx, cy, 18f, TEXT.mulA((0.78f + 0.22f * hv) * qa))
            tf.restore()
            if (hover) {
                tooltip = q.tooltip
                tooltipCx = ccx
            }
            qx -= 8f
        }
        tooltip?.let { text ->
            val tw = head { NanoVGRenderer.getTextWidth(text, 12f) } + 24f
            val th = 28f
            val tx = (tooltipCx - tw * 0.5f).coerceIn(8f, f.w - tw - 8f)
            val ty = cy + 19f + 10f
            Draw2D.shadow(tx, ty, tw, th, th * 0.5f, 18f, NanoVGColor(0f, 0f, 0f, 0.40f * a), 6f)
            frost(tx, ty, tw, th, th * 0.5f, a)
            Draw2D.roundedRect(tx, ty, tw, th, th * 0.5f, NanoVGColor(0.07f, 0.05f, 0.09f, 0.62f * a))
            edgeLight(tx, ty, tw, th, th * 0.5f, a, 0.8f)
            head { NanoVGRenderer.drawCenteredString(text, tx + tw * 0.5f, ty + th * 0.5f, TEXT.mulA(a), 12f) }
        }
    }

    tf.restore()
}
