package cn.reverie.gui.mainmenu

import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Scene state shared by the main menu and the screens that open from it (Singleplayer / Multiplayer / Accounts).
 *
 * The footage depth, parallax and camera push live here instead of in a screen instance, so when the player moves
 * between screens the background keeps moving from where it was instead of restarting.
 */
internal object MenuScene {

    /** Scene depth: 0 on the main menu, 1 on the screens that open from it. Animated by a spring. */
    var scroll = 0f
    var scrollVel = 0f

    /** Smoothed pointer offset from the screen centre, -1..1. */
    var parX = 0f
    var parY = 0f

    /**
     * Camera push (0..1): the main menu raises [pushTarget] while it leaves, which dollies the footage towards the
     * viewer. The next screen never sets it, so [push] relaxes back to 0 and the camera settles smoothly.
     */
    var push = 0f
    var pushTarget = 0f

    /** The transform the backdrop was last drawn with, so frosted surfaces can sample what is behind them. */
    var bgZoom = 1f
    var bgPanX = 0f
    var bgPanY = 0f

    /** True once the first-launch entrance has finished; later screens skip the background fade-in. */
    var introPlayed = false

    /** The most recent main menu instance. */
    var mainMenu: MainMenuScreen? = null

    /** Spring-driven depth plus smoothed pointer parallax. Sub-stepped so it stays stable at low frame rates. */
    fun step(target: Float, dt: Float, mx: Float, my: Float, w: Float, h: Float) {
        val stiffness = 64f
        val damping = 2f * sqrt(stiffness) * 0.95f
        var remaining = dt
        while (remaining > 0f) {
            val step = min(remaining, 1f / 120f)
            val accel = -stiffness * (scroll - target) - damping * scrollVel
            scrollVel += accel * step
            scroll += scrollVel * step
            remaining -= step
        }
        if (abs(scroll - target) < 0.0004f && abs(scrollVel) < 0.003f) {
            scroll = target
            scrollVel = 0f
        }

        val nx = (mx / w * 2f - 1f).coerceIn(-1f, 1f)
        val ny = (my / h * 2f - 1f).coerceIn(-1f, 1f)
        val k = 1f - exp(-dt * 3.2f)
        parX += (nx - parX) * k
        parY += (ny - parY) * k

        // The push eases in quickly and relaxes slowly: a dolly in, then a gentle settle.
        val pk = 1f - exp(-dt * (if (pushTarget > push) 7f else 2.6f))
        push += (pushTarget - push) * pk
        if (abs(push - pushTarget) < 0.001f) push = pushTarget
    }
}

/** Hit regions of the persistent chrome (top bar); written during rendering. */
internal object MenuChrome {
    const val NONE = -1
    const val ACCOUNT = 100
    const val BRAND = 101

    /** Quick icon buttons on the main menu are reported as [QUICK_BASE] + index. */
    const val QUICK_BASE = 200
    const val QUICK_COUNT = 3

    val brandRect = FloatArray(4)
    val accountRect = FloatArray(4)
    val quickRects = Array(QUICK_COUNT) { FloatArray(4) }
    val accountHover = FloatAnimation(0f, 0f, 180L, CubicBezier.EASE_OUT)
    val brandHover = FloatAnimation(0f, 0f, 180L, CubicBezier.EASE_OUT)
    val quickHover = Array(QUICK_COUNT) { FloatAnimation(0f, 0f, 180L, CubicBezier.EASE_OUT) }

    fun beginFrame() {
        brandRect[2] = 0f
        accountRect[2] = 0f
        for (r in quickRects) r[2] = 0f
    }

    private fun hit(r: FloatArray, mx: Float, my: Float): Boolean =
        r[2] > 0f && mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]

    /** Returns [ACCOUNT], [BRAND], [QUICK_BASE] + index, or [NONE]. */
    fun hitTest(mx: Float, my: Float): Int {
        if (hit(accountRect, mx, my)) return ACCOUNT
        if (hit(brandRect, mx, my)) return BRAND
        for (i in 0 until QUICK_COUNT) if (hit(quickRects[i], mx, my)) return QUICK_BASE + i
        return NONE
    }
}
