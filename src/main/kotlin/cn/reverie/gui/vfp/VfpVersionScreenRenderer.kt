package cn.reverie.gui.vfp

import cn.reverie.gui.clickgui.CategoryVectorIcons
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGImage
import cn.reverie.render.nanovg.NanoVGRenderer
import com.viaversion.viafabricplus.screen.base.list.VFPList
import com.viaversion.viafabricplus.screen.impl.protocol.AbstractProtocolSelectionScreen
import com.viaversion.viafabricplus.screen.impl.protocol.ProtocolSlot
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.lwjgl.nanovg.NanoVG
class VfpVersionScreenRenderer(private val screen: AbstractProtocolSelectionScreen) {

    private val panelBg = NanoVGColor(0.031f, 0.035f, 0.047f, 0.82f)
    private val hairlineSubtle = NanoVGColor(1f, 1f, 1f, 0.06f)
    private val cardIdle = NanoVGColor(1f, 1f, 1f, 0.02f)
    private val cardHover = NanoVGColor(1f, 1f, 1f, 0.065f)
    private val cardActive = NanoVGColor(1f, 1f, 1f, 0.09f)
    private val pillActiveBorder = NanoVGColor(1f, 1f, 1f, 0.40f)
    private val textPrimary = NanoVGColor.WHITE
    private val textHover = NanoVGColor(0.894f, 0.894f, 0.906f, 1f)
    private val textNormal = NanoVGColor(0.631f, 0.631f, 0.667f, 1f)
    internal val textMuted = NanoVGColor(0.443f, 0.443f, 0.478f, 1f)

    private val openAnim = FloatAnimation(0f, 1f, 220L, CubicBezier.EASE_OUT)
    private val searchFocusAnim = FloatAnimation(0f, 0f, 150L, CubicBezier.EASE_OUT)
    private val pillXAnim = FloatAnimation(0f, 0f, 220L, CubicBezier.EASE_OUT)
    private val pillWAnim = FloatAnimation(0f, 0f, 220L, CubicBezier.EASE_OUT)
    private val pillAlphaAnim = FloatAnimation(1f, 1f, 150L, CubicBezier.EASE_OUT)
    private val scrollAnim = FloatAnimation(0f, 0f, 160L, CubicBezier.EASE_OUT)
    private var pillInitialized = false
    private var scrollInitialized = false
    private var lastList: VFPList? = null
    private val rowHover = HashMap<Int, Float>()
    private var lastFrameMs = 0L
    private var cursorTimer = 0L

    private var tabRects: List<VfpRectF> = emptyList()
    private var clearRect: VfpRectF? = null
    private var scrollbarThumb: VfpRectF? = null
    private var draggingScrollbar = false
    private var dragGrabOffset = 0f

    internal val iconCache = HashMap<String, NanoVGImage?>()

    fun resetForOpen() {
        openAnim.setImmediate(0f)
        openAnim.animateTo(1f)
        pillInitialized = false
        scrollInitialized = false
        lastList = null
        rowHover.clear()
        draggingScrollbar = false
        cursorTimer = System.currentTimeMillis()
    }

    fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        val now = System.currentTimeMillis()
        val dtMs: Float = if (lastFrameMs == 0L) 16f else (now - lastFrameMs).coerceIn(1L, 100L).toFloat()
        lastFrameMs = now

        val screenW = ResolutionHelper.scaledWidth
        val screenH = ResolutionHelper.scaledHeight
        val open = openAnim.value

        val nvg = RenderEngine.nanoVG
        val panelW = VfpVersionLayout.panelWidth(screenW.toInt()).toFloat()
        val panelH = (VfpVersionLayout.panelBottom(screenH.toInt()) - VfpVersionLayout.panelY()).toFloat()
        val panelX = VfpVersionLayout.panelX(screenW.toInt()).toFloat()
        val panelY = VfpVersionLayout.panelY().toFloat()

        nvg.transform.save()

        if (open < 0.999f) {
            val cx = panelX + panelW * 0.5f
            val cy = panelY + panelH * 0.5f
            val scale = 0.97f + 0.03f * open
            nvg.transform.translate(cx, cy)
            nvg.transform.scale(scale, scale)
            nvg.transform.translate(-cx, -cy)
        }
        NanoVG.nvgGlobalAlpha(nvg.handle, open.coerceIn(0f, 1f))

        try {
            RenderEngine.backdropBlurRenderer.renderBackdrop(
                0f, 0f, screenW, screenH,
                radius = 0f,
                tintColor = NanoVGColor(0f, 0f, 0f, 0.45f * open),
                blurRadius = 16f
            )
        } catch (ignored: Throwable) {
            NanoVGRenderer.drawRoundedRect(0f, 0f, screenW, screenH, 0f, NanoVGColor(0f, 0f, 0f, 0.60f * open))
        }

        Draw2D.shadow(panelX, panelY, panelW, panelH, 10f, 14f, NanoVGColor(0f, 0f, 0f, 0.42f))
        try {
            RenderEngine.backdropBlurRenderer.renderBackdrop(
                panelX, panelY, panelW, panelH,
                radius = 10f,
                tintColor = panelBg,
                blurRadius = 16f
            )
        } catch (ignored: Throwable) {
            NanoVGRenderer.drawRoundedRect(panelX, panelY, panelW, panelH, 10f, panelBg)
        }
        NanoVGRenderer.drawRoundedOutline(panelX, panelY, panelW, panelH, 10f, hairlineSubtle, 1f)

        val brand = screen.title.getString()
        NanoVGRenderer.drawCenteredString(brand, panelX + panelW * 0.5f, panelY - 12f, NanoVGColor(1f, 1f, 1f, 0.92f), 14f)

        renderSearchField(mouseX, mouseY, panelX, panelW)
        renderTabBar(mouseX, mouseY, panelX, panelW)
        renderVersionList(mouseX, mouseY, panelX, panelW, dtMs)
        renderButtons(mouseX, mouseY)

        nvg.transform.restore()
    }

    private fun renderSearchField(mouseX: Float, mouseY: Float, panelX: Float, panelW: Float) {
        val box = screen.nvgSearchBox() ?: return
        val s = VfpVersionLayout.searchRect(ResolutionHelper.scaledWidth.toInt())
        val x = s[0].toFloat()
        val y = s[1].toFloat()
        val w = s[2].toFloat()
        val h = s[3].toFloat()

        searchFocusAnim.animateTo(if (box.isFocused) 1f else 0f)
        val focus = searchFocusAnim.value
        val hovered = VfpRectF(x, y, w, h).contains(mouseX, mouseY)

        val bg = NanoVGColor(1f, 1f, 1f, 0.03f).lerp(NanoVGColor(1f, 1f, 1f, 0.05f), focus)
        NanoVGRenderer.drawRoundedRect(x, y, w, h, 9f, bg)
        val borderAlpha = 0.06f + 0.14f * focus + (if (hovered && focus < 0.5f) 0.06f else 0f)
        NanoVGRenderer.drawRoundedOutline(x, y, w, h, 9f, NanoVGColor(1f, 1f, 1f, borderAlpha), 1f)

        val iconColor = textMuted.copy(a = 0.85f).lerp(textPrimary, focus)
        CategoryVectorIcons.drawSearchIcon(x + 11f, y + (h - 10f) * 0.5f, 10f, iconColor)

        val textX = x + 28f
        val centerY = y + h * 0.5f
        val value = box.value
        val maxTextW = w - 28f - 20f

        if (value.isEmpty() && !box.isFocused) {
            NanoVGRenderer.drawVerticallyCenteredString(
                Component.translatable("base.viafabricplus.search").getString(),
                textX, centerY, textMuted.copy(a = 0.75f), 10f
            )
        } else {
            val textW = NanoVGRenderer.drawVerticallyCenteredString(value, textX, centerY, textPrimary, 10f)

            if (box.isFocused && ((now() - cursorTimer) / 500) % 2L == 0L) {
                val cursorX = (textX + textW + 1.5f).coerceAtMost(textX + maxTextW)
                Draw2D.line(cursorX, centerY - 5f, cursorX, centerY + 5f, NanoVGColor(0.95f, 0.95f, 0.98f, 0.9f), 1.1f)
            }

            if (value.isNotEmpty()) {
                val cr = VfpRectF(x + w - 22f, y + (h - 12f) * 0.5f, 12f, 12f)
                clearRect = cr
                val clearHovered = cr.contains(mouseX, mouseY)
                CategoryVectorIcons.drawClearIcon(
                    cr.x + 2f, cr.y + 2f, 8f,
                    textMuted.copy(a = if (clearHovered) 1f else 0.7f)
                )
            } else {
                clearRect = null
            }
        }
    }

    private fun renderTabBar(mouseX: Float, mouseY: Float, panelX: Float, panelW: Float) {
        val titles = screen.nvgTabTitles()
        if (titles.isEmpty()) {
            tabRects = emptyList()
            return
        }

        val barY = 58f
        val barH = 19f
        val innerLeft = panelX + 16f
        val innerRight = panelX + panelW - 16f
        val innerW = innerRight - innerLeft
        val gap = 4f

        var fontSize = 9.5f
        var padding = 9f
        val measure = { text: String, size: Float -> RenderEngine.fontRenderer.getStringWidth(text, size) }
        val totalWidth = { size: Float, pad: Float ->
            var t = 0f
            for (title in titles) t += measure(title, size) + pad * 2f
            t + gap * (titles.size - 1)
        }
        if (totalWidth(fontSize, padding) > innerW) {
            fontSize = 8.5f
            padding = 6f
        }

        val rects = ArrayList<VfpRectF>(titles.size)
        var cursor = innerLeft
        val overflow = totalWidth(fontSize, padding) > innerW
        val slotW = if (overflow) (innerW - gap * (titles.size - 1)) / titles.size else 0f
        for (title in titles) {
            val w = if (overflow) slotW else (measure(title, fontSize) + padding * 2).toFloat()
            rects.add(VfpRectF(cursor, barY, w, barH))
            cursor += w + gap
        }
        tabRects = rects

        val currentIndex = screen.nvgCurrentTabIndex()

        if (currentIndex in rects.indices) {
            val target = rects[currentIndex]
            if (!pillInitialized) {
                pillXAnim.setImmediate(target.x)
                pillWAnim.setImmediate(target.width)
                pillInitialized = true
            } else {
                pillXAnim.animateTo(target.x)
                pillWAnim.animateTo(target.width)
            }
            pillAlphaAnim.animateTo(1f)
        } else {
            pillAlphaAnim.animateTo(0f)
        }

        if (pillAlphaAnim.value > 0.01f) {
            NanoVGRenderer.drawRoundedRect(
                pillXAnim.value, barY, pillWAnim.value.coerceAtLeast(8f), barH, barH * 0.5f,
                NanoVGColor(1f, 1f, 1f, 0.14f * pillAlphaAnim.value)
            )
            NanoVGRenderer.drawRoundedOutline(
                pillXAnim.value, barY, pillWAnim.value.coerceAtLeast(8f), barH, barH * 0.5f,
                NanoVGColor(1f, 1f, 1f, 0.40f * pillAlphaAnim.value), 1f
            )
        }

        for ((index, rect) in rects.withIndex()) {
            val isCurrent = index == currentIndex
            val hovered = rect.contains(mouseX, mouseY)
            val color = when {
                isCurrent -> textPrimary
                hovered -> textHover
                else -> textNormal
            }
            val label = RenderEngine.fontRenderer.trimWithEllipsis(titles[index], rect.width - 6f, fontSize)
            NanoVGRenderer.withScissor(rect.x, rect.y - 2f, rect.width, rect.height + 4f) {
                NanoVGRenderer.drawCenteredString(label, rect.x + rect.width * 0.5f, rect.y + rect.height * 0.5f, color, fontSize)
            }
        }
    }

    private fun renderVersionList(mouseX: Float, mouseY: Float, panelX: Float, panelW: Float, dtMs: Float) {
        val list = screen.nvgCurrentList() ?: return
        val selectable = screen.nvgIsSelectable()

        val realScroll = list.scrollAmount().toFloat()
        if (list !== lastList || !scrollInitialized) {
            scrollAnim.setImmediate(realScroll)
            lastList = list
            scrollInitialized = true
            rowHover.clear()
        } else {
            scrollAnim.animateTo(realScroll)
        }
        val scrollOffset = scrollAnim.value - realScroll

        val listTop = list.y.toFloat()
        val listBottom = (list.y + list.height).toFloat()
        val clipH = listBottom - listTop
        if (clipH <= 4f) return

        val rowLeft = list.rowLeft.toFloat()
        val rowW = list.rowWidth.toFloat()

        NanoVGRenderer.withScissor(panelX + 4f, listTop, panelW - 8f, clipH) {
            val children = list.children()
            var anyRowVisible = false
            for (i in children.indices) {
                val entry = children[i] as? ProtocolSlot ?: continue
                val rowTop = list.getRowTop(i) + scrollOffset
                if (rowTop + VfpVersionLayout.SLOT_HEIGHT < listTop || rowTop > listBottom) continue
                anyRowVisible = true
                renderSlot(entry, rowLeft, rowTop, rowW, mouseX, mouseY, selectable, dtMs)
            }
            if (!anyRowVisible) {
                NanoVGRenderer.drawCenteredString(
                    "···", panelX + panelW * 0.5f, listTop + clipH * 0.5f,
                    textMuted.copy(a = 0.8f), 11f
                )
            }
        }

        renderScrollbar(list, panelX, panelW, listTop, clipH, mouseX, mouseY)
    }

    private fun renderSlot(
        slot: ProtocolSlot, rowLeftF: Float, rowTopF: Float, rowW: Float,
        mouseX: Float, mouseY: Float, selectable: Boolean, dtMs: Float
    ) {
        val version = slot.protocolVersion()
        val metadata = slot.metadata()
        val cardX = rowLeftF + 4f
        val cardY = rowTopF + 2f
        val cardW = rowW - 8f
        val cardH = VfpVersionLayout.SLOT_HEIGHT - 4f
        val hovered = VfpRectF(cardX, cardY, cardW, cardH).contains(mouseX, mouseY)
        val selected = selectable && screen.nvgIsSelected(version)

        val key = System.identityHashCode(slot)
        val target = if (hovered && !selected) 1f else 0f
        val current = rowHover.getOrDefault(key, target)
        val k = (1.0 - Math.exp(-dtMs / 70.0)).toFloat().coerceIn(0f, 1f)
        val smoothed = current + (target - current) * k
        rowHover[key] = smoothed

        val bg = when {
            selected -> cardActive
            else -> NanoVGColor(1f, 1f, 1f, 0.02f + 0.045f * smoothed)
        }
        NanoVGRenderer.drawRoundedRect(cardX, cardY, cardW, cardH, 6f, bg)
        if (selected) {
            NanoVGRenderer.drawRoundedOutline(cardX, cardY, cardW, cardH, 6f, pillActiveBorder, 1f)
        }

        val nameColor = when {
            !selectable -> textMuted
            selected -> textPrimary
            hovered -> textHover
            else -> textNormal
        }
        val subColor = textMuted.copy(a = if (!selectable) 0.6f else 0.95f)

        val iconSize = 15f
        val iconX = cardX + 12f
        val iconY = cardY + (cardH - iconSize) * 0.5f
        drawIcon(slot.icon(), iconX, iconY, iconSize, if (!selectable) 0.55f else 1f)

        val textLeft = iconX + iconSize + 9f
        val dateRight = cardX + cardW - 12f
        val centerY = cardY + cardH * 0.5f

        if (metadata == null) {
            NanoVGRenderer.drawVerticallyCenteredString(version.name, textLeft, centerY, nameColor, 10.5f)
            return
        }

        val dateText = metadata.formattedReleaseDate().getString()
        val dateW = RenderEngine.fontRenderer.getStringWidth(dateText, 8f)
        val nameMaxW = dateRight - dateW - 8f - textLeft

        val nameY = cardY + cardH * 0.5f - 8f
        val titleY = cardY + cardH * 0.5f + 1.5f
        NanoVGRenderer.drawString(RenderEngine.fontRenderer.trimWithEllipsis(version.name, nameMaxW, 10f), textLeft, nameY, nameColor, 10f)
        NanoVGRenderer.drawString(RenderEngine.fontRenderer.trimWithEllipsis(metadata.title ?: "", nameMaxW, 8.5f), textLeft, titleY, subColor, 8.5f)
        NanoVGRenderer.drawRightVerticallyCenteredString(dateText, dateRight, centerY, textMuted, 8f)
    }

    private fun renderScrollbar(
        list: VFPList, panelX: Float, panelW: Float, listTop: Float, clipH: Float,
        mouseX: Float, mouseY: Float
    ) {
        val maxScroll = list.maxScrollAmount().toFloat()
        scrollbarThumb = null
        if (maxScroll <= 0f) return

        val trackX = panelX + panelW - 7f
        val trackW = 3f
        val thumbH = (clipH * clipH / (clipH + maxScroll)).coerceIn(20f, clipH)
        val scrollable = clipH - thumbH
        val frac = (list.scrollAmount().toFloat() / maxScroll).coerceIn(0f, 1f)
        val thumbY = listTop + scrollable * frac

        val thumb = VfpRectF(trackX, thumbY, trackW, thumbH)
        scrollbarThumb = thumb
        val active = draggingScrollbar || thumb.contains(mouseX, mouseY)
        NanoVGRenderer.drawRoundedRect(trackX, thumbY, trackW, thumbH, trackW * 0.5f, NanoVGColor(1f, 1f, 1f, if (active) 0.35f else 0.14f))
    }

    private fun renderButtons(mouseX: Float, mouseY: Float) {
        for (widget in collectButtons()) {
            val rect = VfpRectF(widget.x.toFloat(), widget.y.toFloat(), widget.width.toFloat(), widget.height.toFloat())
            val hovered = rect.contains(mouseX, mouseY) && widget.active
            val bg = when {
                !widget.active -> NanoVGColor(1f, 1f, 1f, 0.015f)
                hovered -> NanoVGColor(1f, 1f, 1f, 0.07f)
                else -> NanoVGColor(1f, 1f, 1f, 0.03f)
            }
            NanoVGRenderer.drawRoundedRect(rect.x, rect.y, rect.width, rect.height, rect.height * 0.5f, bg)
            NanoVGRenderer.drawRoundedOutline(
                rect.x, rect.y, rect.width, rect.height, rect.height * 0.5f,
                NanoVGColor(1f, 1f, 1f, if (!widget.active) 0.04f else if (hovered) 0.16f else 0.08f), 1f
            )

            val labelColor = if (widget.active) textHover.copy(a = if (hovered) 1f else 0.9f) else textMuted.copy(a = 0.7f)
            val label = widget.message.getString()
            val fontSize = 9.5f
            val fitted = RenderEngine.fontRenderer.trimWithEllipsis(label, rect.width - 14f, fontSize)
            NanoVGRenderer.drawCenteredString(fitted, rect.x + rect.width * 0.5f, rect.y + rect.height * 0.5f, labelColor, fontSize)
        }
    }

    private fun collectButtons(): List<Button> {
        val buttons = ArrayList<Button>()
        for (child in screen.children()) {
            if (child is Button && child.visible) {
                buttons.add(child)
            }
        }
        return buttons
    }

    fun onMousePressed(mouseX: Float, mouseY: Float): Boolean {
        if (openAnim.value < 0.9f) return false

        val clear = clearRect
        if (clear != null && clear.contains(mouseX, mouseY)) {
            screen.nvgSearchBox()?.setValue("")
            return true
        }

        val thumb = scrollbarThumb
        if (thumb != null && thumb.copy(width = thumb.width + 8f, x = thumb.x - 4f).contains(mouseX, mouseY)) {
            draggingScrollbar = true
            dragGrabOffset = mouseY - thumb.y
            return true
        }

        for ((index, rect) in tabRects.withIndex()) {
            if (rect.contains(mouseX, mouseY)) {
                if (index != screen.nvgCurrentTabIndex()) {
                    screen.nvgSelectTab(index)
                }
                return true
            }
        }

        val box = screen.nvgSearchBox()
        if (box != null && box.isFocused) {
            val s = VfpVersionLayout.searchRect(ResolutionHelper.scaledWidth.toInt())
            if (!VfpRectF(s[0].toFloat(), s[1].toFloat(), s[2].toFloat(), s[3].toFloat()).contains(mouseX, mouseY)) {
                box.setFocused(false)
            }
        }
        return false
    }

    fun onMouseDragged(mouseX: Float, mouseY: Float): Boolean {
        if (!draggingScrollbar) return false
        val list = screen.nvgCurrentList() ?: return false
        val thumb = scrollbarThumb ?: return false
        val maxScroll = list.maxScrollAmount().toFloat()
        if (maxScroll <= 0f) return false

        val clipH = list.height.toFloat()
        val thumbH = (clipH * clipH / (clipH + maxScroll)).coerceIn(20f, clipH)
        val scrollable = clipH - thumbH
        if (scrollable <= 0f) return false

        val targetY = mouseY - dragGrabOffset
        val frac = ((targetY - list.y) / scrollable).coerceIn(0f, 1f)
        list.setScrollAmount((frac * maxScroll).toDouble())
        return true
    }

    fun onMouseReleased() {
        draggingScrollbar = false
    }

    private fun now(): Long = System.currentTimeMillis()

    private fun drawIcon(identifier: Identifier, x: Float, y: Float, size: Float, alpha: Float) = drawIconImpl(identifier, x, y, size, alpha)
    private fun drawImageRect(image: NanoVGImage, x: Float, y: Float, size: Float, alpha: Float) = drawImageRectImpl(image, x, y, size, alpha)
    private fun loadIcon(identifier: Identifier): NanoVGImage? = loadIconImpl(identifier)
}
