package cn.reverie.render.core

import cn.reverie.gui.clickgui.ClickGuiScreen
import cn.reverie.gui.mainmenu.MainMenuScreen
import cn.reverie.module.render.ChatBarModule
import cn.reverie.app.event.EventBus
import cn.reverie.app.event.render.Render2DEvent
import cn.reverie.app.event.render.RenderScreenEvent
import cn.reverie.render.font.FontManager
import cn.reverie.render.font.FontRenderer
import cn.reverie.render.gl.GLStateGuard
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.hud.HudRenderer
import cn.reverie.render.nanovg.NanoVGContext
import cn.reverie.render.nanovg.NanoVGFont
import cn.reverie.render.shader.blur.BlurEngine
import cn.reverie.render.ui.ModernScreen
import cn.reverie.render.ui.NvgScreen
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL30C.*
import cn.reverie.gui.clickgui.window.WindowClickGuiScreen
import cn.reverie.render.ui.notification.NotificationManager
object RenderEngine {

    private const val VFP_BLUR_RADIUS = 16f

    val nanoVG = NanoVGContext()
    val context: Long get() = nanoVG.handle
    val blurEngine by lazy { BlurEngine() }
    val backdropBlurRenderer: cn.reverie.render.shader.blur.BackdropBlurRenderer get() = blurEngine.backdropRenderer
    val hudRenderer = HudRenderer()
    val pipeline = RenderingPipeline()

    var fontManager: FontManager? = null
        private set
    lateinit var fontRenderer: FontRenderer
        private set

    var defaultFont: NanoVGFont? = null
        private set

    var minecraftFont: NanoVGFont? = null
        private set

    var uiFont: NanoVGFont? = null
        private set

    var arialFont: NanoVGFont? = null
        private set

    var wqyFont: NanoVGFont? = null
        private set

    var sansFont: NanoVGFont? = null
        private set

    var comfortaaFont: NanoVGFont? = null
        private set

    var kuriyamaFont: NanoVGFont? = null
        private set

    var verdanaFont: NanoVGFont? = null
        private set

    var tahomaFont: NanoVGFont? = null
        private set

    var bahnschriftFont: NanoVGFont? = null
        private set

    var consolasFont: NanoVGFont? = null
        private set

    var segoeUiFont: NanoVGFont? = null
        private set

    var comicFont: NanoVGFont? = null
        private set

    fun getFontByName(name: String): NanoVGFont? = RenderFontFacade.getFontByName(name)
    fun <T> withNamedFont(name: String, block: () -> T): T = RenderFontFacade.withNamedFont(name, block)
    fun <T> withUiFont(block: () -> T): T = withNamedFont("UI", block)
    fun <T> withSansFont(block: () -> T): T = withNamedFont("Product Sans", block)
    fun <T> withArialFont(block: () -> T): T = withNamedFont("Arial", block)
    fun <T> withWqyFont(block: () -> T): T = withNamedFont("WQY", block)
    fun <T> withComfortaaFont(block: () -> T): T = withNamedFont("Comfortaa", block)
    fun <T> withKuriyamaFont(block: () -> T): T = withNamedFont("Kuriyama", block)

    private fun loadFont(
        mgr: FontManager,
        fontName: String,
        fileNames: List<String>,
        systemFallback: String? = null
    ): NanoVGFont? = RenderFontFacade.loadFont(mgr, fontName, fileNames, systemFallback)

    var isInitialized = false
        private set

    fun init() {
        if (isInitialized) return

        try {
            GLStateGuard.withStateGuard {
                nanoVG.init()
                val mgr = FontManager(nanoVG.handle)
                fontManager = mgr

                // 1. Default (MiSans / CJK base)
                val def = loadFont(
                    mgr, "default",
                    listOf("misans.ttf", "misans-bold.ttf", "sans_medium.ttf"),
                    "C:\\Windows\\Fonts\\msyh.ttc"
                ) ?: NanoVGFont(nanoVG.handle, "sans", -1)
                defaultFont = def
                minecraftFont = NanoVGFont(nanoVG.handle, "minecraft", -1, isMinecraftBitmap = true)

                // 2. UI (Inter)
                uiFont = loadFont(
                    mgr, "ui",
                    listOf("regular_medium.otf", "regular.otf", "regular_semi.otf")
                )

                // 3. Sans (Product Sans)
                sansFont = loadFont(
                    mgr, "sans",
                    listOf("sans_medium.ttf", "sans_regular.ttf", "sans_bold.ttf")
                )

                // 4. Arial
                arialFont = loadFont(
                    mgr, "arial",
                    listOf("arial.ttf"),
                    "C:\\Windows\\Fonts\\arial.ttf"
                )

                // 5. WQY (WenQuanYi Micro Hei)
                wqyFont = loadFont(
                    mgr, "wqy",
                    listOf("wqy_microhei.ttf")
                )

                // 6. Comfortaa
                comfortaaFont = loadFont(
                    mgr, "comfortaa",
                    listOf("Comfortaa.ttf")
                )

                // 7. Kuriyama
                kuriyamaFont = loadFont(
                    mgr, "kuriyama",
                    listOf("kuriyama.ttf")
                )

                // 8. Verdana
                verdanaFont = loadFont(
                    mgr, "verdana",
                    listOf("verdana.ttf"),
                    "C:\\Windows\\Fonts\\verdana.ttf"
                )

                // 9. Tahoma
                tahomaFont = loadFont(
                    mgr, "tahoma",
                    listOf("tahoma.ttf"),
                    "C:\\Windows\\Fonts\\tahoma.ttf"
                )

                // 10. Bahnschrift
                bahnschriftFont = loadFont(
                    mgr, "bahnschrift",
                    listOf("bahnschrift.ttf"),
                    "C:\\Windows\\Fonts\\bahnschrift.ttf"
                )

                // 11. Consolas
                consolasFont = loadFont(
                    mgr, "consolas",
                    listOf("consola.ttf"),
                    "C:\\Windows\\Fonts\\consola.ttf"
                )

                // 12. Segoe UI
                segoeUiFont = loadFont(
                    mgr, "segoeui",
                    listOf("segoeui.ttf"),
                    "C:\\Windows\\Fonts\\segoeui.ttf"
                )

                // 13. Comic Sans
                comicFont = loadFont(
                    mgr, "comic",
                    listOf("comic.ttf"),
                    "C:\\Windows\\Fonts\\comic.ttf"
                )

                // Additional portable HUD fonts from Google Fonts (SIL Open Font License).
                val bundledHudFonts = listOf(
                    "Manrope" to "manrope.ttf",
                    "Space Grotesk" to "space-grotesk.ttf",
                    "Outfit" to "outfit.ttf",
                    "IBM Plex Sans" to "ibm-plex-sans.ttf",
                    "DM Sans" to "dm-sans.ttf",
                    "JetBrains Mono" to "jetbrains-mono.ttf",
                    "Urbanist" to "urbanist.ttf",
                    "Plus Jakarta Sans" to "plus-jakarta-sans.ttf",
                    "Sora" to "sora.ttf",
                    "Rubik" to "rubik.ttf",
                    "Space Mono" to "space-mono.ttf",
                    "Fira Sans" to "fira-sans.ttf",
                    "Lato" to "lato.ttf",
                    "Lexend" to "lexend.ttf",
                    "Montserrat" to "montserrat.ttf",
                    "Nunito" to "nunito.ttf",
                    "Source Sans 3" to "source-sans-3.ttf",
                    "Oswald" to "oswald.ttf",
                    "Orbitron" to "orbitron.ttf",
                    "Souvenir Tenacity" to "souvenir-tenacity.ttf",
                    "Souvenir Tenacity Bold" to "souvenir-tenacity-bold.ttf",
                    "Souvenir AaLGH" to "souvenir-aalgh.ttf",
                    "Souvenir USans" to "souvenir-usans.ttf"
                ).mapNotNull { (name, fileName) -> loadFont(mgr, name, listOf(fileName)) }

                // Attach Chinese glyph fallback (nvgAddFallbackFontId) to ensure zero missing glyphs
                if (def.handle >= 0) {
                    val otherFonts = listOfNotNull(
                        uiFont, sansFont, arialFont, comfortaaFont,
                        verdanaFont, tahomaFont, bahnschriftFont,
                        consolasFont, segoeUiFont, comicFont
                    ) + bundledHudFonts
                    for (f in otherFonts) {
                        if (f.handle >= 0 && f.handle != def.handle) {
                            org.lwjgl.nanovg.NanoVG.nvgAddFallbackFontId(nanoVG.handle, f.handle, def.handle)
                        }
                    }
                }

                fontRenderer = FontRenderer(def)
                ResolutionHelper.syncWithMinecraft()
                isInitialized = true
                println("[Nocturne] NanoVG Client Rendering Initialized Successfully with font: ${def.name} (handle: ${def.handle})")
            }
        } catch (e: Throwable) {
            System.err.println("[Nocturne] RenderEngine init deferred until GL context is ready: ${e.message}")
        }
    }

    fun onTick() {
        RenderTickCoordinator.onTick(this)
    }

    private var hudTargetFbo = 0
    private var hudDepthStencilRbo = 0
    private var lastFboWidth = 0
    private var lastFboHeight = 0
    private var diagFrameCounter = 0L

    fun onBeforeGuiRender(deltaTracker: DeltaTracker) {
        if (!isInitialized) init()
        if (!isInitialized) return

        val mc = Minecraft.getInstance()
        val inGame = mc.level != null && mc.player != null
        if (!inGame) return

        val currentScreen = mc.gui.screen()
        val isChat = currentScreen is ChatScreen
        val isClickGui = currentScreen is ClickGuiScreen || currentScreen is WindowClickGuiScreen
        val isEditingHud = isChat
        val shouldRenderHud = currentScreen == null || isEditingHud || isClickGui

        if (!shouldRenderHud) return

        val partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false)

        GLStateGuard.withStateGuard {
            ResolutionHelper.syncWithMinecraft()
            // Reset blur caches before refreshing the scene texture so all HUD panels in
            // this frame can share the same full-screen result.
            backdropBlurRenderer.beginFrame()

            val mainTarget = mc.gameRenderer.mainRenderTarget()
            val colorTexture = mainTarget.colorTexture as? com.mojang.blaze3d.opengl.GlTexture
            val texId = colorTexture?.glId() ?: 0

            val backdropBlurRadius = maxOf(
                if (HUDModule.isAnyBlurEnabled()) HUDModule.blurRadius.getFloat() else 0f,
                if (ChatBarModule.shouldDrawBackdropBlur()) HUDModule.blurRadius.getFloat() else 0f
            )
            if (backdropBlurRadius > 0f && texId > 0) {
                try {
                    blurEngine.prepareScreenBlurFromTexture(texId, backdropBlurRadius)
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }

            if (texId > 0) {
                renderHudToTexture(texId, partialTicks, isEditingHud)
            }
        }
    }

    private fun renderHudToTexture(targetTextureId: Int, partialTicks: Float, isEditing: Boolean) {
        if (hudTargetFbo == 0) {
            hudTargetFbo = glGenFramebuffers()
        }

        val mc = Minecraft.getInstance()
        val window = mc.window
        val winW = ResolutionHelper.framebufferWidth
        val winH = ResolutionHelper.framebufferHeight
        val guiW = ResolutionHelper.scaledWidth
        val guiH = ResolutionHelper.scaledHeight
        if (winW <= 0 || winH <= 0 || guiW <= 0f || guiH <= 0f) return

        glBindFramebuffer(GL_FRAMEBUFFER, hudTargetFbo)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, targetTextureId, 0)

        if (hudDepthStencilRbo == 0) {
            hudDepthStencilRbo = glGenRenderbuffers()
        }
        if (lastFboWidth != winW || lastFboHeight != winH) {
            glBindRenderbuffer(GL_RENDERBUFFER, hudDepthStencilRbo)
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, winW, winH)
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, hudDepthStencilRbo)
            lastFboWidth = winW
            lastFboHeight = winH
        }

        glViewport(0, 0, winW, winH)

        glStencilMask(0xFF)
        glClear(GL_STENCIL_BUFFER_BIT)

        nanoVG.beginFrame(guiW, guiH, ResolutionHelper.devicePixelRatio)

        val mouseX = ResolutionHelper.toCanvasX(mc.mouseHandler.getScaledXPos(window)).toFloat()
        val mouseY = ResolutionHelper.toCanvasY(mc.mouseHandler.getScaledYPos(window)).toFloat()

        val currentScreen = mc.gui.screen()
        val isChat = currentScreen is net.minecraft.client.gui.screens.ChatScreen

        if (isChat) {
            val handle = window.handle()
            val isLeftDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(handle, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
            val isRightDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(handle, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
            hudRenderer.handleFrameInput(mouseX, mouseY, isLeftDown, isRightDown)
        } else {
            hudRenderer.clearDrag()
        }

        ChatBarModule.renderChatBar()
        hudRenderer.render(partialTicks, isEditing = isEditing)
        EventBus.post(Render2DEvent(partialTicks))

        LayerManager.renderAll()
        nanoVG.endFrame()

        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    fun onRender2D(deltaTracker: DeltaTracker) {
        if (!isInitialized) init()
        if (!isInitialized) return
        val partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false)

        val mc = Minecraft.getInstance()
        val currentScreen = mc.gui.screen()
        val isMainMenu = currentScreen is MainMenuScreen
        val isNvgScreen = currentScreen is NvgScreen
        cn.reverie.render.video.VideoBackgroundPlayer.setPlaybackActive(isMainMenu || isNvgScreen)

        val shouldRenderScreen = currentScreen is ClickGuiScreen || currentScreen is WindowClickGuiScreen || currentScreen is ModernScreen || isMainMenu || isNvgScreen
        if (!shouldRenderScreen) {
            return
        }

        diagFrameCounter++
        val diag = diagFrameCounter % 120L == 0L
        if (diag) println("[Nocturne-Diag] enter screen=${currentScreen?.javaClass?.name}")

        GLStateGuard.withStateGuard {
            ResolutionHelper.syncWithMinecraft()
            RenderStats.beginFrame()
            // Begin before blur preparation; a prepared scene blur is reused by every panel.
            backdropBlurRenderer.beginFrame()

            if (currentScreen is ClickGuiScreen || currentScreen is WindowClickGuiScreen) {
                if (HUDModule.blur.value) {
                    try {
                        val r = HUDModule.blurRadius.getFloat()
                        blurEngine.prepareScreenBlur(r)
                    } catch (ignored: Throwable) {}
                }
            } else if (isNvgScreen) {
                try {
                    blurEngine.prepareScreenBlur(VFP_BLUR_RADIUS)
                } catch (ignored: Throwable) {}
            }
            val window = mc.window
            val guiW = ResolutionHelper.scaledWidth
            val guiH = ResolutionHelper.scaledHeight
            if (guiW <= 0f || guiH <= 0f) return@withStateGuard

            nanoVG.beginFrame(guiW, guiH, ResolutionHelper.devicePixelRatio)
            if (diag) println("[Nocturne-Diag] beginFrame done")

            val mouseX = ResolutionHelper.toCanvasX(mc.mouseHandler.getScaledXPos(window)).toFloat()
            val mouseY = ResolutionHelper.toCanvasY(mc.mouseHandler.getScaledYPos(window)).toFloat()

            if (currentScreen is ClickGuiScreen) {
                HUDModule.withHudFont { currentScreen.renderClickGui(mouseX, mouseY, partialTicks) }
                EventBus.post(RenderScreenEvent(currentScreen, mouseX, mouseY, partialTicks))
            } else if (currentScreen is WindowClickGuiScreen) {
                HUDModule.withHudFont { currentScreen.renderClickGui(mouseX, mouseY, partialTicks) }
                EventBus.post(RenderScreenEvent(currentScreen, mouseX, mouseY, partialTicks))
            } else if (currentScreen is MainMenuScreen) {
                withUiFont { currentScreen.renderMainMenu(mouseX, mouseY, partialTicks) }
                if (diag) println("[Nocturne-Diag] renderMainMenu done")
                EventBus.post(RenderScreenEvent(currentScreen, mouseX, mouseY, partialTicks))
            } else if (currentScreen is NvgScreen) {
                currentScreen.renderNvg(mouseX, mouseY, partialTicks)
                if (diag) println("[Nocturne-Diag] renderNvg done")
                EventBus.post(RenderScreenEvent(currentScreen, mouseX, mouseY, partialTicks))
            } else if (currentScreen is ModernScreen) {
                currentScreen.renderModernScreen(mouseX, mouseY, partialTicks)
                if (diag) println("[Nocturne-Diag] renderModernScreen done")
                EventBus.post(RenderScreenEvent(currentScreen, mouseX, mouseY, partialTicks))
            }

            LayerManager.renderAll()
            if (diag) println("[Nocturne-Diag] layers done")
            NotificationManager.render()
            if (diag) println("[Nocturne-Diag] notifications done")
            nanoVG.endFrame()
            if (diag) println("[Nocturne-Diag] endFrame done")
        }
    }

    fun onRenderHud(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        RenderTickCoordinator.onRenderHud(this, graphics, deltaTracker)
    }

    fun onRenderScreenPre(screen: Screen, mouseX: Int, mouseY: Int, a: Float): Boolean = RenderScreenInput.onRenderScreenPre(screen)

    fun onRenderScreenPost(screen: Screen, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        RenderScreenInput.onRenderScreenPost(this, screen, graphics)
    }

    fun onMouseClicked(screen: Screen?, mouseX: Double, mouseY: Double, button: Int): Boolean = RenderScreenInput.onMouseClicked(this, screen, mouseX, mouseY, button)

    fun onMouseReleased(screen: Screen?, mouseX: Double, mouseY: Double, button: Int): Boolean = RenderScreenInput.onMouseReleased(this, screen, mouseX, mouseY, button)

    fun onMouseDragged(screen: Screen?, mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean = RenderScreenInput.onMouseDragged(this, screen, mouseX, mouseY)

    fun onMouseMoved(screen: Screen?, mouseX: Double, mouseY: Double) = RenderScreenInput.onMouseMoved(this, screen, mouseX, mouseY)

    fun onMouseScrolled(screen: Screen, mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean = RenderScreenInput.onMouseScrolled(this, screen, mouseX, mouseY, scrollY)

    fun onKeyPressed(screen: Screen, keyCode: Int, scanCode: Int, modifiers: Int): Boolean = RenderScreenInput.onKeyPressed(this, screen, keyCode, modifiers)

    fun onCharTyped(screen: Screen, codePoint: Char, modifiers: Int): Boolean = RenderScreenInput.onCharTyped()

    fun onScreenClosed(screen: Screen) = RenderScreenInput.onScreenClosed(screen)

    fun destroy() {
        if (!isInitialized) return
        GLStateGuard.withStateGuard {
            cn.reverie.render.video.VideoBackgroundPlayer.destroy()
            blurEngine.close()
            if (hudTargetFbo != 0) {
                glDeleteFramebuffers(hudTargetFbo)
                hudTargetFbo = 0
            }
            if (hudDepthStencilRbo != 0) {
                glDeleteRenderbuffers(hudDepthStencilRbo)
                hudDepthStencilRbo = 0
            }
            lastFboWidth = 0
            lastFboHeight = 0
            fontManager?.close()
            nanoVG.close()
            isInitialized = false
        }
    }
}
