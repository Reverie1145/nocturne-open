package cn.reverie.gui.clickgui.arcade

import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.CornerRadii
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.hud.HudVectorIcons
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import org.lwjgl.glfw.GLFW

object NocturneArcadeModal {
    const val WIDTH = 560f
    const val HEIGHT = 380f
    private const val HEADER_HEIGHT = 48f
    private const val CORNER_RADIUS = 13f

    var isOpen = false
        private set
    private var isClosing = false

    private val anim = FloatAnimation(0f, 0f, 260L, CubicBezier.EASE_OUT_BACK)
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f

    val games = listOf<ArcadeGame>(
        FlappyMoonGame(),
        MoonPopGame(),
        MoonPianoGame()
    )

    private var activeGameIndex = 0
    val currentGame: ArcadeGame get() = games[activeGameIndex]

    fun open() {
        isOpen = true
        isClosing = false
        anim.durationMs = 260L
        anim.setImmediate(0f)
        anim.animateTo(1f)
        dragOffsetX = 0f
        dragOffsetY = 0f
        currentGame.init()
        ArcadeSoundHelper.playUnlock()
    }

    fun close() {
        if (!isOpen || isClosing) return
        isClosing = true
        anim.durationMs = 180L
        anim.animateTo(0f)
    }

    fun switchGame(index: Int) {
        if (index == activeGameIndex || index !in games.indices) return
        activeGameIndex = index
        currentGame.reset()
        ArcadeSoundHelper.playClick(1.2f)
    }

    fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isOpen) return

        if (isClosing && (anim.isFinished || anim.value <= 0.02f)) {
            isOpen = false
            isClosing = false
            return
        }

        val animVal = anim.value.coerceIn(0f, 1f)
        if (animVal <= 0.01f) return

        // Update physics
        currentGame.update()

        val screenW = ResolutionHelper.scaledWidth
        val screenH = ResolutionHelper.scaledHeight
        val theme = HUDModule.sampleColor(0f)

        val windowX = ((screenW - WIDTH) * 0.5f + dragOffsetX).coerceIn(4f, screenW - WIDTH - 4f)
        val windowY = ((screenH - HEIGHT) * 0.5f + dragOffsetY).coerceIn(4f, screenH - HEIGHT - 4f)

        // Backdrop Dim (matching ClickGUI dim)
        Draw2D.rect(0f, 0f, screenW, screenH, NanoVGColor(0f, 0f, 0f, 0.35f * animVal))

        val nvg = RenderEngine.nanoVG
        nvg.transform.save()

        // Center Scale Animation
        val centerX = windowX + WIDTH * 0.5f
        val centerY = windowY + HEIGHT * 0.5f
        val scale = 0.94f + 0.06f * animVal
        nvg.transform.translate(centerX, centerY)
        nvg.transform.scale(scale, scale)
        nvg.transform.translate(-centerX, -centerY)

        // Window Shadow (matching ClickGUI panel elevation)
        val shadowAlpha = 0.47f * animVal * HUDModule.shadowStrength.getFloat()
        Draw2D.shadow(windowX, windowY, WIDTH, HEIGHT, CORNER_RADIUS, 24f, NanoVGColor(0f, 0f, 0f, shadowAlpha))

        // Frosted Glass Backdrop (matching WindowClickGui exactly: 0.047f, 0.051f, 0.067f)
        val panelBg = NanoVGColor(0.047f, 0.051f, 0.067f, HUDModule.bgOpacity.getFloat() * animVal)
        try {
            RenderEngine.backdropBlurRenderer.renderBackdrop(
                windowX, windowY, WIDTH, HEIGHT,
                radius = CORNER_RADIUS,
                tintColor = panelBg,
                blurRadius = HUDModule.blurRadius.getFloat(),
                enableBlur = HUDModule.blur.value,
                backdropOpacity = HUDModule.bgOpacity.getFloat() * animVal
            )
        } catch (_: Throwable) {
            NanoVGRenderer.drawRoundedRect(windowX, windowY, WIDTH, HEIGHT, CORNER_RADIUS, panelBg)
        }

        // Window Outline (matching WindowClickGui: 0.12f alpha)
        NanoVGRenderer.drawRoundedOutline(windowX, windowY, WIDTH, HEIGHT, CORNER_RADIUS, NanoVGColor(1f, 1f, 1f, 0.12f * animVal), 1.0f)

        // Header Divider Line (matching WindowClickGui: 0.08f alpha at y + HEADER_HEIGHT)
        Draw2D.line(windowX, windowY + HEADER_HEIGHT, windowX + WIDTH, windowY + HEADER_HEIGHT, NanoVGColor(1f, 1f, 1f, 0.08f * animVal), 1.0f)

        // Header Left: Moon Icon + NOCTURNE + ARCADE
        val moonIconX = windowX + 18f
        val moonIconY = windowY + 17f
        HudVectorIcons.drawMoon(moonIconX, moonIconY, 13f, theme.withAlpha(0.95f * animVal))

        NanoVGRenderer.drawString(
            "NOCTURNE",
            moonIconX + 18f,
            windowY + 18.5f,
            NanoVGColor(0.94f, 0.95f, 0.97f, animVal),
            12f
        )
        NanoVGRenderer.drawString(
            "ARCADE",
            moonIconX + 92f,
            windowY + 18.5f,
            theme.withAlpha(0.90f * animVal),
            12f
        )

        // Header Right: Close Button '×' (matching WindowClickGui exactly)
        val closeBtnX = windowX + WIDTH - 34f
        val closeBtnY = windowY + 12f
        val isHoverClose = mouseX in closeBtnX..(closeBtnX + 22f) && mouseY in closeBtnY..(closeBtnY + 24f)
        NanoVGRenderer.drawRoundedRect(
            closeBtnX, closeBtnY, 22f, 24f, 6f,
            NanoVGColor(1f, 1f, 1f, (if (isHoverClose) 0.16f else 0.06f) * animVal)
        )
        NanoVGRenderer.drawCenteredString(
            "×",
            closeBtnX + 11f,
            closeBtnY + 12f,
            NanoVGColor(if (isHoverClose) 1.0f else 0.80f, if (isHoverClose) 0.5f else 0.81f, if (isHoverClose) 0.5f else 0.84f, animVal),
            17f
        )

        // Segmented Tab Bar (Matching ClickGUI's rounded controls)
        val tabsStartX = windowX + 185f
        val tabW = 98f
        val tabH = 26f
        val tabY = windowY + 11f
        val tabsTotalW = games.size * tabW + (games.size - 1) * 4f

        NanoVGRenderer.drawRoundedRect(
            tabsStartX - 3f, tabY - 2f, tabsTotalW + 6f, tabH + 4f, 7f,
            NanoVGColor(0f, 0f, 0f, 0.22f * animVal)
        )
        NanoVGRenderer.drawRoundedOutline(
            tabsStartX - 3f, tabY - 2f, tabsTotalW + 6f, tabH + 4f, 7f,
            NanoVGColor(1f, 1f, 1f, 0.08f * animVal), 1.0f
        )

        for (i in games.indices) {
            val game = games[i]
            val tx = tabsStartX + i * (tabW + 4f)
            val isSelected = i == activeGameIndex
            val isHover = mouseX in tx..(tx + tabW) && mouseY in tabY..(tabY + tabH)

            if (isSelected) {
                NanoVGRenderer.drawRoundedRect(tx, tabY, tabW, tabH, 5f, theme.withAlpha(0.26f * animVal))
                NanoVGRenderer.drawRoundedOutline(tx, tabY, tabW, tabH, 5f, theme.withAlpha(0.85f * animVal), 1.0f)
            } else if (isHover) {
                NanoVGRenderer.drawRoundedRect(tx, tabY, tabW, tabH, 5f, NanoVGColor(1f, 1f, 1f, 0.06f * animVal))
            }

            // Tab Moon Icon
            val tabMoonColor = if (isSelected) theme.withAlpha(animVal) else NanoVGColor(0.64f, 0.65f, 0.70f, 0.80f * animVal)
            HudVectorIcons.drawMoon(tx + 8f, tabY + 7f, 11f, tabMoonColor)

            val tLabel = game.tabLabel
            val textColor = if (isSelected) NanoVGColor(0.97f, 0.98f, 1f, animVal) else NanoVGColor(0.70f, 0.71f, 0.75f, 0.85f * animVal)
            NanoVGRenderer.drawString(tLabel, tx + 24f, tabY + 8f, textColor, 9.5f)
        }

        // Active Game Canvas
        val gameAreaX = windowX
        val gameAreaY = windowY + HEADER_HEIGHT
        val gameAreaW = WIDTH
        val gameAreaH = HEIGHT - HEADER_HEIGHT

        currentGame.render(
            x = gameAreaX,
            y = gameAreaY,
            width = gameAreaW,
            height = gameAreaH,
            mouseX = mouseX,
            mouseY = mouseY,
            alpha = animVal,
            theme = theme
        )

        nvg.transform.restore()
    }

    fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isOpen || isClosing) return false

        val screenW = ResolutionHelper.scaledWidth
        val screenH = ResolutionHelper.scaledHeight
        val windowX = ((screenW - WIDTH) * 0.5f + dragOffsetX).coerceIn(4f, screenW - WIDTH - 4f)
        val windowY = ((screenH - HEIGHT) * 0.5f + dragOffsetY).coerceIn(4f, screenH - HEIGHT - 4f)

        // Outside click closes modal
        if (mouseX !in windowX..(windowX + WIDTH) || mouseY !in windowY..(windowY + HEIGHT)) {
            close()
            return true
        }

        // Close button click
        val closeBtnX = windowX + WIDTH - 34f
        val closeBtnY = windowY + 12f
        if (mouseX in closeBtnX..(closeBtnX + 22f) && mouseY in closeBtnY..(closeBtnY + 24f)) {
            ArcadeSoundHelper.playClick(0.9f)
            close()
            return true
        }

        // Header Tab clicks
        val tabsStartX = windowX + 185f
        val tabW = 98f
        val tabH = 26f
        val tabY = windowY + 11f
        for (i in games.indices) {
            val tx = tabsStartX + i * (tabW + 4f)
            if (mouseX in tx..(tx + tabW) && mouseY in tabY..(tabY + tabH)) {
                switchGame(i)
                return true
            }
        }

        // Header drag start
        if (mouseY in windowY..(windowY + HEADER_HEIGHT)) {
            isDragging = true
            dragStartX = mouseX
            dragStartY = mouseY
            return true
        }

        // Game canvas click
        val gameAreaX = windowX
        val gameAreaY = windowY + HEADER_HEIGHT
        val localMx = mouseX - gameAreaX
        val localMy = mouseY - gameAreaY
        return currentGame.mouseClicked(localMx, localMy, button)
    }

    fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (isDragging) {
            isDragging = false
            return true
        }
        if (!isOpen) return false

        val screenW = ResolutionHelper.scaledWidth
        val screenH = ResolutionHelper.scaledHeight
        val windowX = ((screenW - WIDTH) * 0.5f + dragOffsetX).coerceIn(4f, screenW - WIDTH - 4f)
        val windowY = ((screenH - HEIGHT) * 0.5f + dragOffsetY).coerceIn(4f, screenH - HEIGHT - 4f)
        val localMx = mouseX - windowX
        val localMy = mouseY - (windowY + HEADER_HEIGHT)
        return currentGame.mouseReleased(localMx, localMy, button)
    }

    fun mouseDragged(mouseX: Float, mouseY: Float, button: Int, dx: Float, dy: Float): Boolean {
        if (isDragging) {
            dragOffsetX += dx
            dragOffsetY += dy
            return true
        }
        return false
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (!isOpen || isClosing) return false

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close()
            return true
        }

        // Tab shortcuts: Ctrl+1, Ctrl+2, Ctrl+3
        if ((modifiers and GLFW.GLFW_MOD_CONTROL) != 0) {
            when (keyCode) {
                GLFW.GLFW_KEY_1 -> { switchGame(0); return true }
                GLFW.GLFW_KEY_2 -> { switchGame(1); return true }
                GLFW.GLFW_KEY_3 -> { switchGame(2); return true }
            }
        }

        return currentGame.keyPressed(keyCode, scanCode, modifiers)
    }
}
