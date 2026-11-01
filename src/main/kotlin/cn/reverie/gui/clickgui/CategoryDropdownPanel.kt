package cn.reverie.gui.clickgui

import cn.reverie.module.misc.YuriGallery
import cn.reverie.render.art.AnimeArt
import cn.reverie.module.Category
import cn.reverie.module.ModuleManager
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.math.Rect2f
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer

class CategoryDropdownPanel(
    val category: Category,
    initialX: Float,
    initialY: Float,
    var customWidth: Float = 140f
) {
    var x: Float = initialX
    var y: Float = initialY

    val width: Float
        get() = ClickGUIModule.panelWidth.getFloat()

    val headerHeight: Float
        get() = ClickGUIModule.headerHeight.getFloat()

    var isExpanded: Boolean = true

    var isDragging = false
        private set
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    var scrollAmount: Float = 0f
    var targetScroll: Float = 0f

    val moduleButtons = mutableListOf<ModuleButton>()
    private var moduleRevision = -1L

    val expandAnim = FloatAnimation(
        if (isExpanded) 1f else 0f,
        if (isExpanded) 1f else 0f,
        340L,
        CubicBezier.EASE_IN_OUT
    )
    val headerHoverAnim = FloatAnimation(0f, 0f, 200L, CubicBezier.EASE_OUT)
    val dragElevateAnim = FloatAnimation(0f, 0f, 240L, CubicBezier.EASE_OUT)
    private val dragTracker = cn.reverie.render.animation.MotionTracker(accelFactor = 0.45f, decelFactor = 0.10f, deadzone = 0.02f)

    init {
        val modules = ModuleManager.getModulesByCategory(category)
        for (m in modules) {
            moduleButtons.add(ModuleButton(m))
        }
        val saved = scrollMemory[category]
        if (saved != null && saved > 0f) {
            targetScroll = saved
            scrollAmount = saved
        }
        val savedExpanded = expandMemory[category]
        if (savedExpanded != null) {
            isExpanded = savedExpanded
            expandAnim.setImmediate(if (isExpanded) 1f else 0f)
        }
    }

    val contentHeight: Float
        get() {
            val base = if (moduleButtons.isEmpty()) 0f else moduleButtons.sumOf { (it.totalHeight + 2f).toDouble() }.toFloat() + 4f
            return if (isGalleryEnabled) base + GALLERY_CARD_H + 2f else base
        }

    private val isGalleryEnabled: Boolean
        get() = YuriGallery.enabled && YuriGallery.guiArt.value

    val maxVisibleContentHeight: Float
        get() {
            val screenH = ResolutionHelper.scaledHeight
            return (screenH - y - headerHeight - 20f).coerceIn(160f, 460f)
        }

    val visibleContentHeight: Float
        get() = minOf(contentHeight, maxVisibleContentHeight)

    val totalHeight: Float
        get() = headerHeight + visibleContentHeight * expandAnim.value

    fun isMouseOverHeader(mouseX: Float, mouseY: Float): Boolean {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + headerHeight
    }

    fun isMouseOverTotal(mouseX: Float, mouseY: Float): Boolean {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + totalHeight
    }

    fun render(mouseX: Float, mouseY: Float, partialTicks: Float, searchFilter: String = "") {
        if (moduleRevision != ModuleManager.revision) {
            val retained = moduleButtons.associateBy { it.module }
            moduleButtons.clear()
            ModuleManager.getModulesByCategory(category).forEach { module -> moduleButtons += retained[module] ?: ModuleButton(module) }
            moduleRevision = ModuleManager.revision
        }
        val animMultiplier = ClickGUIModule.animSpeed.getFloat()
        expandAnim.durationMs = (240L / animMultiplier).toLong().coerceIn(80L, 600L)
        headerHoverAnim.durationMs = (140L / animMultiplier).toLong().coerceIn(40L, 350L)
        dragElevateAnim.durationMs = (160L / animMultiplier).toLong().coerceIn(50L, 400L)

        expandAnim.animateTo(if (isExpanded) 1f else 0f)
        headerHoverAnim.animateTo(if (isMouseOverHeader(mouseX, mouseY)) 1f else 0f)
        dragElevateAnim.animateTo(if (isDragging) 1f else 0f)

        val currentW = width
        val currentH = totalHeight
        val cornerRadius = ClickGUIModule.cornerRadius.getFloat()
        val panelRect = Rect2f(x, y, currentW, currentH)

        val maxVisibleH = visibleContentHeight
        val isScrollable = contentHeight > maxVisibleH && expandAnim.value > 0.7f
        val maxScroll = (contentHeight - maxVisibleH).coerceAtLeast(0f)

        val prevScroll = scrollAmount
        targetScroll = targetScroll.coerceIn(0f, maxScroll)
        scrollMemory[category] = targetScroll
        if (kotlin.math.abs(targetScroll - scrollAmount) < 0.02f) {
            scrollAmount = targetScroll
        } else {
            scrollAmount += (targetScroll - scrollAmount) * (0.30f * animMultiplier).coerceIn(0.1f, 1f)
        }
        val scrollSpeed = scrollAmount - prevScroll

        dragTracker.update(x, y)

        if (dragTracker.isMoving && dragElevateAnim.value > 0.01f) {
            cn.reverie.render.animation.MotionBlurHelper.renderMotionBlur(
                x, y, currentW, currentH,
                cornerRadius,
                dragTracker.vx, dragTracker.vy,
                NanoVGColor(1f, 1f, 1f, 0.25f * dragElevateAnim.value),
                maxBlur = 18f,
                samples = 4
            )
        }

        val panelAlpha = HUDModule.bgOpacity.getFloat()
        val shadowBlur = 10f + 8f * dragElevateAnim.value
        val shadowAlpha = (0.35f + 0.15f * dragElevateAnim.value) * (panelAlpha / 0.90f).coerceIn(0.3f, 1.2f)
        if (HUDModule.widgetShadow.value) Draw2D.shadow(x, y, currentW, currentH, cornerRadius, shadowBlur, NanoVGColor(0f, 0f, 0f, shadowAlpha * HUDModule.shadowStrength.getFloat()))

        val blurRadius = HUDModule.blurRadius.getFloat()
        val panelBg = NanoVGColor(0.047f, 0.051f, 0.067f, panelAlpha)
        try {
            RenderEngine.backdropBlurRenderer.renderBackdrop(
                x, y, currentW, currentH,
                radius = cornerRadius,
                tintColor = panelBg,
                blurRadius = blurRadius,
                enableBlur = HUDModule.blur.value,
                backdropOpacity = panelAlpha
            )
        } catch (ignored: Throwable) {
            NanoVGRenderer.drawRoundedRect(x, y, currentW, currentH, cornerRadius, panelBg)
        }

        CategoryPanelChrome.draw(x, y, currentW, currentH, headerHeight, cornerRadius, headerHoverAnim.value, expandAnim.value)

        val iconSize = 16f
        val iconX = x + 10f
        val iconY = y + (headerHeight - iconSize) * 0.5f
        val iconColor = NanoVGColor(0.85f, 0.88f, 0.92f, 0.90f).lerp(NanoVGColor.WHITE, headerHoverAnim.value)

        CategoryVectorIcons.drawCategoryIcon(category, iconX, iconY, iconSize, iconColor)

        val catTitleX = iconX + iconSize + 8f
        val catName = category.displayName.uppercase()
        val titleBlur = if (expandAnim.isRunning) kotlin.math.sin(expandAnim.value * Math.PI).toFloat() * 3.8f else 0f

        NanoVGRenderer.drawVerticallyCenteredString(
            catName,
            catTitleX,
            y + headerHeight * 0.5f,
            NanoVGColor(0.95f, 0.96f, 0.98f, 0.95f),
            11.5f,
            shadow = HUDModule.textShadow.value,
            blur = titleBlur
        )

        val chevronColor = NanoVGColor(0.55f, 0.58f, 0.65f, 0.70f).lerp(NanoVGColor.WHITE, headerHoverAnim.value * 0.4f)
        CategoryVectorIcons.drawChevronDown(x + currentW - 12f, y + headerHeight * 0.5f, 7f, 1f - expandAnim.value, chevronColor)

        if (expandAnim.value > 0.01f && moduleButtons.isNotEmpty()) {
            val contentBoxH = currentH - headerHeight
            val isExpandingTransition = expandAnim.isRunning && expandAnim.value < 0.995f
            val catBlur = if (isExpandingTransition) {
                cn.reverie.render.animation.MotionBlurHelper.getBlurForProgress(expandAnim.value, maxBlur = 5.0f)
            } else 0f

            NanoVGRenderer.withScissor(x, y + headerHeight + 1f, currentW, contentBoxH) {
                var currentBtnY = y + headerHeight + 3f - scrollAmount

                RenderEngine.fontRenderer.withAmbientBlur(catBlur) {
                    for (btn in moduleButtons) {
                        val matches = searchFilter.isEmpty() || btn.module.name.contains(searchFilter, ignoreCase = true)

                        btn.x = x + 3f
                        btn.y = currentBtnY
                        btn.width = if (isScrollable) currentW - 8f else currentW - 6f

                        if (currentBtnY + btn.totalHeight >= y + headerHeight - 10f && currentBtnY <= y + headerHeight + contentBoxH + 10f) {
                            if (matches) {
                                btn.render(mouseX, mouseY, partialTicks)
                            } else if (searchFilter.isNotEmpty()) {

                                org.lwjgl.nanovg.NanoVG.nvgSave(RenderEngine.nanoVG.handle)
                                org.lwjgl.nanovg.NanoVG.nvgGlobalAlpha(RenderEngine.nanoVG.handle, 0.20f)
                                btn.render(mouseX, mouseY, partialTicks)
                                org.lwjgl.nanovg.NanoVG.nvgRestore(RenderEngine.nanoVG.handle)
                            }
                        }

                        currentBtnY += btn.totalHeight + 2f
                    }
                }

                if (isGalleryEnabled) {
                    renderGalleryCard(currentBtnY, currentW, isScrollable, expandAnim.value)
                }

                if (isExpandingTransition && contentBoxH > 4f) {
                    val overlayAlpha = (1f - expandAnim.value).coerceIn(0f, 1f)
                    val blurSpread = (catBlur * 2.0f).coerceIn(4f, 16f)
                    if (HUDModule.widgetShadow.value) cn.reverie.render.draw.ShadowRenderer.drawShadow(
                        RenderEngine.nanoVG.handle,
                        x + 2f, y + headerHeight + 1f, currentW - 4f, contentBoxH,
                        cornerRadius,
                        blur = blurSpread,
                        spread = 0.5f,
                        color = NanoVGColor(1f, 1f, 1f, 0.18f * overlayAlpha * HUDModule.shadowStrength.getFloat()),
                        offsetY = (1f - expandAnim.value) * 6f
                    )
                }
            }

            if (isExpandingTransition && contentBoxH > 4f) {
                val panelVy = (1f - expandAnim.value) * 16f
                cn.reverie.render.animation.MotionBlurHelper.renderVerticalMotionBlur(
                    x, y, currentW, currentH,
                    cornerRadius,
                    panelVy,
                    NanoVGColor(1f, 1f, 1f, 0.22f * (1f - expandAnim.value)),
                    maxBlur = 18f
                )
            }

            if (isScrollable) {
                val trackX = x + currentW - 3.5f
                val trackY = y + headerHeight + 4f
                val trackH = contentBoxH - 8f

                val thumbH = (maxVisibleH / contentHeight * trackH).coerceAtLeast(16f)
                val thumbY = trackY + (scrollAmount / maxScroll).coerceIn(0f, 1f) * (trackH - thumbH)

                NanoVGRenderer.drawRoundedRect(trackX, trackY, 1.5f, trackH, 0.75f, NanoVGColor(1f, 1f, 1f, 0.05f))

                NanoVGRenderer.drawRoundedRect(trackX, thumbY, 1.5f, thumbH, 0.75f, NanoVGColor(1f, 1f, 1f, 0.25f))
            }
        }
    }

    private fun renderGalleryCard(cardY: Float, currentW: Float, isScrollable: Boolean, expandVal: Float) {
        val art = AnimeArt.PANEL_FOOTER_ART[category.displayName] ?: return
        val cardX = x + 3f
        val cardW = if (isScrollable) currentW - 8f else currentW - 6f
        val cardH = GALLERY_CARD_H - 2f
        if (cardW < 60f) return
        val fade = expandVal.coerceIn(0f, 1f)

        NanoVGRenderer.drawRoundedRect(cardX, cardY, cardW, cardH, 5f, NanoVGColor(1f, 1f, 1f, 0.02f * fade))

        if (art.isScene) {
            AnimeArt.drawCover(
                art.id, cardX, cardY, cardW, cardH,
                alpha = 0.88f * fade,
                radius = 5f,
                biasY = -0.2f
            )

            Draw2D.gradientLinear(
                cardX, cardY, cardW, cardH, 5f,
                NanoVGColor(0f, 0f, 0f, 0.05f * fade),
                NanoVGColor(0f, 0f, 0f, 0.62f * fade),
                horizontal = false
            )
        } else {
            AnimeArt.drawFitted(
                art.id,
                cardX + cardW * 0.46f, cardY + 3f, cardW * 0.54f - 8f, cardH - 6f,
                alpha = fade,
                alignBottom = true
            )
        }

        val font = RenderEngine.fontRenderer
        val caption = art.caption
        val captionW = font.getStringWidth(caption, 7.5f)
        font.drawString(
            caption,
            cardX + cardW - captionW - 8f,
            cardY + cardH - 13f,
            NanoVGColor(0.85f, 0.88f, 0.95f, 0.85f * fade),
            7.5f
        )

        NanoVGRenderer.drawRoundedOutline(cardX, cardY, cardW, cardH, 5f, NanoVGColor(1f, 1f, 1f, 0.05f * fade), 0.75f)
    }

    fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (isMouseOverHeader(mouseX, mouseY)) {
            if (button == 0) {
                isDragging = true
                dragOffsetX = mouseX - x
                dragOffsetY = mouseY - y
                return true
            } else if (button == 1) {
                isExpanded = !isExpanded
                expandMemory[category] = isExpanded
                return true
            }
        }

        if (isExpanded && expandAnim.value > 0.2f) {
            val contentBoxH = totalHeight - headerHeight
            if (mouseX in x..(x + width) && mouseY in (y + headerHeight)..(y + headerHeight + contentBoxH)) {
                for (btn in moduleButtons) {
                    if (btn.isMouseOverTotal(mouseX, mouseY)) {
                        if (btn.mouseClicked(mouseX, mouseY, button)) {
                            return true
                        }
                    }
                }
            }
        }

        return false
    }

    fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean {
        isDragging = false
        var handled = false
        for (btn in moduleButtons) {
            if (btn.mouseReleased(mouseX, mouseY, button)) {
                handled = true
            }
        }
        return handled
    }

    fun mouseDragged(mouseX: Float, mouseY: Float, button: Int, dragX: Float, dragY: Float): Boolean {
        if (isDragging) {
            val screenW = ResolutionHelper.scaledWidth
            val screenH = ResolutionHelper.scaledHeight
            x = (mouseX - dragOffsetX).coerceIn(4f, screenW - width - 4f)
            y = (mouseY - dragOffsetY).coerceIn(4f, screenH - headerHeight - 4f)
            return true
        }

        for (btn in moduleButtons) {
            if (btn.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true
            }
        }
        return false
    }

    fun mouseScrolled(mouseX: Float, mouseY: Float, scrollX: Float, scrollY: Float): Boolean {
        if (isMouseOverTotal(mouseX, mouseY) && isExpanded) {
            targetScroll -= scrollY * 24f
            return true
        }
        return false
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (isExpanded) {
            for (btn in moduleButtons) {
                if (btn.keyPressed(keyCode, scanCode, modifiers)) {
                    return true
                }
            }
        }
        return false
    }

    fun charTyped(codePoint: Int): Boolean {
        if (!isExpanded || codePoint <= 0) return false
        for (btn in moduleButtons) {
            if (btn.charTyped(codePoint)) return true
        }
        return false
    }

    companion object {
        private const val GALLERY_CARD_H = 56f
        val scrollMemory = mutableMapOf<Category, Float>()
        val expandMemory = mutableMapOf<Category, Boolean>()
    }
}
