package cn.reverie.gui.vanilla

import cn.reverie.gui.mainmenu.MenuChrome
import cn.reverie.gui.mainmenu.MenuFrame
import cn.reverie.gui.mainmenu.MenuFx
import cn.reverie.gui.mainmenu.MenuScene
import cn.reverie.gui.mainmenu.drawMenuBackdrop
import cn.reverie.gui.mainmenu.renderMenuNav
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.nanovg.NanoVGImage
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.level.storage.LevelSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Singleplayer / Multiplayer, restyled as the "Play" page of the Nocturne main menu.
 *
 * It shares the background scene ([MenuScene]), the top navigation and page rail, the page header, the glass surface
 * language and the arrival / departure motion with [cn.reverie.gui.mainmenu.MainMenuScreen], so moving between them
 * reads as one continuous space rather than two screens.
 *
 * Layout: header (eyebrow, title, subtitle, back) / toolbar (search + actions) / list panel + detail panel.
 */
class VanillaListScreenRenderer(
    val screen: Screen,
    internal val painter: VanillaEntryPainter,
    internal val searchHint: Component?
) {

    data class RectF(val x: Float, val y: Float, val width: Float, val height: Float) {
        fun contains(px: Float, py: Float): Boolean =
            px >= x && px <= x + width && py >= y && py <= y + height
    }

    /** One visible list row as drawn this frame; used for hit testing. */
    internal class RowHit(val entry: Any, val rect: RectF, val quick: RectF)

    internal class InteractivePillButton(
        val label: String,
        val isPrimary: Boolean = false,
        val isDanger: Boolean = false,
        val needsSelection: Boolean = false,
        val onClick: () -> Unit
    ) {
        val hoverAnim = FloatAnimation(0f, 0f, 180L, CubicBezier.EASE_OUT)
        val pressAnim = FloatAnimation(0f, 0f, 100L, CubicBezier.EASE_OUT)
        var isHovered = false
        var isPressed = false
        var isEnabled = true
        var x = 0f
        var y = 0f
        var w = 0f
        var h = 0f

        fun contains(mx: Float, my: Float): Boolean =
            w > 0f && mx >= x && mx <= x + w && my >= y && my <= y + h
    }

    internal val isSingleplayer = screen is SelectWorldScreen
    internal val isMultiplayer = screen is JoinMultiplayerScreen

    internal val searchFocusAnim = FloatAnimation(0f, 0f, 160L, CubicBezier.EASE_OUT)
    internal val cardHover = HashMap<Int, Float>()

    internal var lastLevels: List<LevelSummary>? = null
    internal var cursorTimer = 0L

    // Motion
    internal var enterMs = 0L
    internal var closeStartMs = 0L
    internal var pendingClose: (() -> Unit)? = null
    internal var lastFrameNs = 0L

    // List state
    internal var scrollPos = 0f
    internal var scrollTarget = 0f
    internal var scrollMax = 0f
    internal var scrollbarThumb: RectF? = null
    internal var draggingScrollbar = false
    internal var dragGrabOffset = 0f
    internal var listRect = RectF(0f, 0f, 0f, 0f)
    internal var filtered: List<Any> = emptyList()
    internal val visibleRows = ArrayList<RowHit>()

    // Search
    internal var searchQuery = ""
    internal var searchFocused = false
    internal var searchBoxRect = RectF(0f, 0f, 0f, 0f)
    internal var searchClearRect = RectF(0f, 0f, 0f, 0f)

    internal var lastRenderW = 0f
    internal var lastRenderH = 0f

    internal var backButton = InteractivePillButton("Back") {
        requestClose { screen.onClose() }
    }

    internal val toolbarButtons = mutableListOf<InteractivePillButton>()
    internal val dockButtons = mutableListOf<InteractivePillButton>()

    internal var lastClickedEntry: Any? = null
    internal var lastClickTime = 0L

    internal class IconCacheKey(val id: Any) {
        override fun equals(other: Any?) = other is IconCacheKey && other.id == id
        override fun hashCode() = id.hashCode()
    }
    internal val iconCache = HashMap<IconCacheKey, NanoVGImage?>()

    init {
        initToolbarAndDock()
    }

    fun resetForOpen() {
        enterMs = System.currentTimeMillis()
        closeStartMs = 0L
        pendingClose = null
        scrollPos = 0f
        scrollTarget = 0f
        cardHover.clear()
        draggingScrollbar = false
        searchFocused = false
        searchQuery = ""
        cursorTimer = System.currentTimeMillis()
        lastFrameNs = 0L
        initToolbarAndDock()
    }

    /** Plays the departure motion, then runs [action] (closing this screen or opening another one). */
    internal fun requestClose(action: () -> Unit) {
        if (closeStartMs > 0L) return
        pendingClose = action
        closeStartMs = System.currentTimeMillis()
        for (b in toolbarButtons) b.isPressed = false
        for (b in dockButtons) b.isPressed = false
        backButton.isPressed = false
    }

    private fun initToolbarAndDock() {
        toolbarButtons.clear()
        dockButtons.clear()
        val mc = Minecraft.getInstance()

        if (isSingleplayer) {
            toolbarButtons.add(
                InteractivePillButton("New World", isPrimary = true) {
                    val list = findList() as? WorldSelectionList
                    CreateWorldScreen.openFresh(mc, list?.let { it::returnToScreen } ?: { mc.gui.setScreen(screen) })
                }
            )

            dockButtons.add(
                InteractivePillButton("Play World", isPrimary = true, needsSelection = true) {
                    (findList()?.selected as? WorldSelectionList.WorldListEntry)?.joinWorld()
                }
            )
            dockButtons.add(
                InteractivePillButton("Edit", needsSelection = true) {
                    (findList()?.selected as? WorldSelectionList.WorldListEntry)?.editWorld()
                }
            )
            dockButtons.add(
                InteractivePillButton("Re-Create", needsSelection = true) {
                    (findList()?.selected as? WorldSelectionList.WorldListEntry)?.recreateWorld()
                }
            )
            dockButtons.add(
                InteractivePillButton("Delete", isDanger = true, needsSelection = true) {
                    (findList()?.selected as? WorldSelectionList.WorldListEntry)?.deleteWorld()
                }
            )
        } else if (isMultiplayer) {
            toolbarButtons.add(
                InteractivePillButton("Add Server", isPrimary = true) {
                    triggerVanillaButton("add", "manageServer.add.title")
                }
            )
            toolbarButtons.add(
                InteractivePillButton("Direct Connect") {
                    triggerVanillaButton("direct", "selectServer.direct")
                }
            )
            toolbarButtons.add(
                InteractivePillButton("Refresh") {
                    triggerVanillaButton("refresh", "selectServer.refresh")
                }
            )
            try {
                toolbarButtons.add(
                    InteractivePillButton("ViaFabricPlus") {
                        try {
                            com.viaversion.viafabricplus.ViaFabricPlus.api().screens().openViaFabricPlusScreen(screen)
                        } catch (ignored: Throwable) {
                            triggerVanillaButton("viafabricplus")
                        }
                    }
                )
            } catch (ignored: Throwable) {}

            dockButtons.add(
                InteractivePillButton("Join Server", isPrimary = true, needsSelection = true) {
                    (findList()?.selected as? ServerSelectionList.Entry)?.join()
                }
            )
            dockButtons.add(
                InteractivePillButton("Edit", needsSelection = true) {
                    triggerVanillaButton("edit", "selectServer.edit")
                }
            )
            dockButtons.add(
                InteractivePillButton("Delete", isDanger = true, needsSelection = true) {
                    triggerVanillaButton("delete", "selectServer.delete")
                }
            )
        }
    }

    private fun triggerVanillaButton(vararg keywords: String): Boolean {
        for (child in screen.children()) {
            if (child is Button) {
                val text = child.message.string.lowercase()
                for (kw in keywords) {
                    if (text.contains(kw.lowercase())) {
                        child.onClick(MouseButtonEvent(0.0, 0.0, net.minecraft.client.input.MouseButtonInfo(0, 0)), false)
                        return true
                    }
                }
            }
        }
        return false
    }

    fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        val w = ResolutionHelper.scaledWidth
        val h = ResolutionHelper.scaledHeight
        if (w <= 0f || h <= 0f || RenderEngine.nanoVG.handle == 0L) return
        lastRenderW = w
        lastRenderH = h

        if (enterMs == 0L) enterMs = System.currentTimeMillis()
        suppressVanillaWidgets()

        val nowNs = System.nanoTime()
        val dt = if (lastFrameNs == 0L) 1f / 60f else ((nowNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNs = nowNs
        val now = System.currentTimeMillis()

        // The scene keeps moving from wherever the previous screen left it, settling on the "Play" depth.
        MenuScene.step(1f, dt, mouseX, mouseY, w, h)
        MenuChrome.beginFrame()
        visibleRows.clear()

        val f = MenuFrame(w, h)
        val enterT = ((now - enterMs) / ENTER_MS).coerceIn(0f, 1f)
        val closeT = if (closeStartMs > 0L) MenuFx.easeInOutCubic((now - closeStartMs) / CLOSE_MS) else 0f
        // Same convention as the main menu pages: negative = still arriving from below.
        val rel = if (closeStartMs > 0L) -closeT else -(1f - MenuFx.easeOutCubic(enterT))

        RenderEngine.withUiFont {
            drawMenuBackdrop(f, 1f, now, MenuScene.scroll, MenuScene.parX, MenuScene.parY)
            renderSubScreen(f, rel, mouseX, mouseY, dt)
            renderMenuNav(f, mouseX, mouseY, 1f, 1f, if (isSingleplayer) "Singleplayer" else "Multiplayer")
        }

        if (closeStartMs > 0L && closeT >= 1f) {
            val action = pendingClose
            pendingClose = null
            closeStartMs = 0L
            enterMs = now
            action?.invoke()
        }
    }

    fun onMouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean = handleMouseClicked(event, doubleClick)
    fun onMouseReleased(event: MouseButtonEvent): Boolean = handleMouseReleased(event)
    fun onMouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean = handleMouseDragged(event, dx, dy)
    fun onMouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean = handleMouseScrolled(x, y, scrollX, scrollY)
    fun onKeyPressed(event: KeyEvent): Boolean = handleKeyPressed(event)
    fun onCharTyped(event: CharacterEvent): Boolean = handleCharTyped(event)

    companion object {
        private const val ENTER_MS = 950f
        private const val CLOSE_MS = 260f

        fun formatDate(millis: Long): String =
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
    }
}
