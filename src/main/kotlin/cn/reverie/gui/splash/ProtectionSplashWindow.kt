package cn.reverie.gui.splash

import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
/**
 * Standalone Client Startup Loading Window (Protection By Reverie)
 * Minimalist translucent frosted-glass aesthetic implemented in Java/Kotlin.
 * Unsets JVM Headless restriction to display real progress before Minecraft starts.
 *
 * PROGRESS IS REAL: the bar is driven by [report] calls from the actual
 * bootstrap work, not by a timer. The activation chain is
 *
 *   nocturneClient class-init  -> show()          (window appears)
 *   integrity self-check       -> (phase text only, ~100ms)
 *   NativeGuard.loadNative()   -> 0.30 .. 0.60    native bootstrap
 *   PackBoot.launch()          -> 0.60 .. 1.00    packed class definitions
 *   complete()                 -> 1.00            fade out
 *
 * The reported value is smoothed rather than jumped, so the bar still moves
 * continuously, but it can never advance past what has actually been done.
 */
object ProtectionSplashWindow {

    private const val WINDOW_WIDTH = 680
    private const val WINDOW_HEIGHT = 380
    private const val CORNER_RADIUS = 24f

    /** Progress budget: the native bootstrap occupies this slice of the bar. */
    const val NATIVE_FROM = 0.30f
    const val NATIVE_TO = 0.60f

    /** Progress budget: packed class definitions occupy this slice of the bar. */
    const val PACK_FROM = 0.60f
    const val PACK_TO = 1.00f

    /** The window is held at least this long so a fast boot does not just flash. */
    private const val MIN_DISPLAY_MILLIS = 1500L

    /** How long the completed state stays visible before the fade-out begins. */
    private const val HOLD_AFTER_DONE_MILLIS = 450L

    private const val FADE_MILLIS = 260f

    @Volatile
    private var frame: JFrame? = null

    /** Protection / packed status displayed at bottom */
    @Volatile
    var protectionStatus: String = "NOCTURNE PROTECTION"

    /** REAL progress 0..1, advanced only by [report] / [complete] */
    @Volatile
    var protectionProgress: Float = 0f

    /** Smoothed value actually painted (always <= protectionProgress, trails it) */
    @Volatile
    private var displayedProgress: Float = 0f

    /** current operation text (English) */
    @Volatile
    var currentOperation: String = "Verifying protection..."

    /** set once every bootstrap step has reported completion */
    @Volatile
    private var isComplete = false

    @Volatile
    private var startTimeMillis = 0L

    @Volatile
    private var isWindowReady = false

    /** the window has been disposed (fade-out finished) */
    @Volatile
    private var isGone = false

    @Volatile
    private var isFadingOut = false

    @Volatile
    private var currentElapsed = 0L

    @Volatile
    private var currentOpacity = 1.0f

    private var animationExecutor: ScheduledExecutorService? = null
    private var blurredBackgroundImage: BufferedImage? = null

    init {
        try {
            System.setProperty("java.awt.headless", "false")
        } catch (ignored: Throwable) {
        }
    }

    // ---- real progress reporting (called from the bootstrap steps) ----

    /**
     * Advance the bar to [progress] (0..1, clamped, monotonic) and show
     * [operation]. Called from the actual work; nothing here is time-based.
     */
    @JvmStatic
    fun report(progress: Float, operation: String? = null) {
        val p = progress.coerceIn(0f, 1f)
        if (p > protectionProgress) protectionProgress = p
        if (operation != null) currentOperation = operation
    }

    /** All bootstrap work finished: paint 100% and start the fade-out. */
    @JvmStatic
    fun complete() {
        protectionProgress = 1f
        currentOperation = "Protection ready"
        protectionStatus = "NOCTURNE PROTECTION READY"
        isComplete = true
    }

    @JvmStatic
    fun show() {
        if (frame != null) return

        try {
            System.setProperty("java.awt.headless", "false")
        } catch (ignored: Throwable) {
        }

        if (startTimeMillis == 0L) {
            startTimeMillis = System.currentTimeMillis()
        }

        SwingUtilities.invokeLater {
            try {
                createAndShowSplash()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    private fun createAndShowSplash() {
        if (frame != null) return

        loadAndPrepareBackgroundImage()

        val jf = JFrame("Protection By Reverie")
        frame = jf
        jf.isUndecorated = true
        jf.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        jf.setSize(WINDOW_WIDTH, WINDOW_HEIGHT)
        jf.setLocationRelativeTo(null)
        jf.isAlwaysOnTop = true

        try {
            jf.background = Color(0, 0, 0, 0)
        } catch (ignored: Throwable) {
        }

        try {
            jf.shape = RoundRectangle2D.Float(
                0f, 0f,
                WINDOW_WIDTH.toFloat(),
                WINDOW_HEIGHT.toFloat(),
                CORNER_RADIUS,
                CORNER_RADIUS
            )
        } catch (ignored: Throwable) {
        }

        val canvas = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2d = g as? Graphics2D ?: return
                renderSplash(g2d, width, height)
            }
        }
        canvas.isOpaque = false
        canvas.layout = null
        jf.contentPane = canvas

        start120FpsAnimationLoop(jf, canvas)

        jf.isVisible = true
        jf.toFront()
        jf.requestFocus()
        isWindowReady = true

        println("[ProtectionSplashWindow] Pure Java 120 FPS Translucent Splash is now active! (680x380, AlwaysOnTop)")
    }

    private fun loadAndPrepareBackgroundImage() {
        try {
            val resourcePath = "/assets/reverie/splash/bg.jpg"
            val stream: InputStream? = ProtectionSplashWindow::class.java.getResourceAsStream(resourcePath)
            if (stream != null) {
                val rawImage = ImageIO.read(stream)
                val scaled = BufferedImage(WINDOW_WIDTH, WINDOW_HEIGHT, BufferedImage.TYPE_INT_ARGB)
                val gs = scaled.createGraphics()
                gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                gs.drawImage(rawImage, 0, 0, WINDOW_WIDTH, WINDOW_HEIGHT, null)
                gs.dispose()

                // High quality fast blur radius 8px for depth-of-field frosted glass texture
                blurredBackgroundImage = SplashImageFilters.fastBlur(scaled, 8)
            }
        } catch (t: Throwable) {
            blurredBackgroundImage = null
        }
    }

    private fun start120FpsAnimationLoop(jf: JFrame, canvas: JPanel) {
        val executor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "Reverie-Splash-120FPS-Animator").apply { isDaemon = true }
        }
        animationExecutor = executor

        executor.scheduleAtFixedRate({
            val elapsed = (System.currentTimeMillis() - startTimeMillis).coerceAtLeast(0L)
            currentElapsed = elapsed

            // Smoothly trail the reported value: the bar never claims more than
            // what has actually finished, but it does not jump either.
            val target = protectionProgress.coerceIn(0f, 1f)
            displayedProgress += (target - displayedProgress) * 0.22f
            if (target - displayedProgress < 0.001f) displayedProgress = target

            // Fade out only once the work is done, the bar has caught up, and the
            // window has been visible for the minimum time.
            val done = isComplete && displayedProgress >= 0.995f
            if (!isFadingOut && done && elapsed >= MIN_DISPLAY_MILLIS + HOLD_AFTER_DONE_MILLIS) {
                isFadingOut = true
            }

            if (isFadingOut) {
                val fadeT = (elapsed - (MIN_DISPLAY_MILLIS + HOLD_AFTER_DONE_MILLIS)) / FADE_MILLIS
                currentOpacity = (1.0f - fadeT).coerceIn(0.0f, 1.0f)

                if (currentOpacity <= 0.02f) {
                    executor.shutdown()
                    SwingUtilities.invokeLater {
                        try {
                            jf.isVisible = false
                            jf.dispose()
                            frame = null
                            isGone = true
                        } catch (ignored: Throwable) {
                        }
                    }
                    return@scheduleAtFixedRate
                }

                try {
                    jf.opacity = currentOpacity
                } catch (ignored: Throwable) {
                    // opacity unsupported on this platform: drop the window instead
                    executor.shutdown()
                    jf.isVisible = false
                    jf.dispose()
                    frame = null
                    isGone = true
                    return@scheduleAtFixedRate
                }
            }

            canvas.repaint()
        }, 0L, 8L, TimeUnit.MILLISECONDS)
    }

    private fun renderSplash(g2d: Graphics2D, w: Int, h: Int) = SplashWindowRenderer.render(g2d, w, h, blurredBackgroundImage, displayedProgress, currentElapsed, currentOperation, protectionStatus)
    /**
     * High-performance dual-pass box blur approximating Gaussian depth of field
     */


    /**
     * Block the calling (Minecraft startup) thread until the splash has faded
     * out, so the game window is not revealed while protection is still running.
     *
     * This waits on the REAL completion signal plus the minimum display time; it
     * is not a fixed sleep. [maxWaitMillis] only guards against a hung window.
     */
    @JvmStatic
    @JvmOverloads
    fun awaitSplash(minDisplayMillis: Long = MIN_DISPLAY_MILLIS, maxWaitMillis: Long = 8000L) {
        show()

        val waitReadyStart = System.currentTimeMillis()
        while (!isWindowReady && System.currentTimeMillis() - waitReadyStart < 1200L) {
            try {
                Thread.sleep(15)
            } catch (ignored: InterruptedException) {
                break
            }
        }
        if (!isComplete) {
            // No bootstrap step reported completion (protection disabled or a
            // failure path): do not hold the window hostage.
            complete()
            try {
                Thread.sleep(200)
            } catch (ignored: InterruptedException) {
            }
        }

        val deadline = System.currentTimeMillis() + maxWaitMillis
        while (!isGone && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(15)
            } catch (ignored: InterruptedException) {
                break
            }
        }
        // safety net: the title screen may already be up, never leave it floating
        finish()
    }

    /** Force the window away (title screen reached, or a hard failure path). */
    @JvmStatic
    fun finish() {
        val f = frame ?: return
        try {
            f.isVisible = false
            f.dispose()
        } catch (ignored: Throwable) {
        }
        frame = null
        isGone = true
        try {
            animationExecutor?.shutdown()
        } catch (ignored: Throwable) {
        }
    }

    @JvmStatic
    fun createPreviewImage(
        progress: Float = 0.72f
    ): BufferedImage {
        loadAndPrepareBackgroundImage()
        val img = BufferedImage(WINDOW_WIDTH, WINDOW_HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val g2d = img.createGraphics()
        try {
            val oldDisplayed = displayedProgress
            displayedProgress = progress

            renderSplash(g2d, WINDOW_WIDTH, WINDOW_HEIGHT)

            displayedProgress = oldDisplayed
        } finally {
            g2d.dispose()
        }
        return img
    }
}
