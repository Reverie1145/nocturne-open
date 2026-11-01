package cn.reverie.module.render

import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.module.BooleanSetting
import cn.reverie.render.ChatComponentAccess
import cn.reverie.render.ChatScreenAccess
import cn.reverie.render.EditBoxAccess
import cn.reverie.render.animation.Easing
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.CornerRadii
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.draw.RoundedRect
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGConstants
import cn.reverie.render.nanovg.NanoVGTransform
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.render.math.Rect2f
import cn.reverie.util.misc.ChatUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.multiplayer.chat.GuiMessage
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.entity.player.ChatVisiblity
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import java.util.WeakHashMap
import cn.reverie.gui.clickgui.irc.IrcImageCache
import cn.reverie.gui.clickgui.irc.IrcImageViewerScreen

/** Replaces vanilla chat only while this optional module setting is enabled. */
object ChatBarModule : Module(
    name = "ChatBar",
    description = "Render chat messages and input with NanoVG",
    category = Category.RENDER
) {
    val customBackground = BooleanSetting("NanoVG Chat", "Replace the vanilla chat renderer with NanoVG", true)

    private data class StyledRun(
        val text: StringBuilder,
        val color: Int,
        val bold: Boolean,
        val italic: Boolean,
        val underline: Boolean,
        val strikethrough: Boolean
    )

    private data class ChatLine(
        val sourceIndex: Int,
        val line: net.minecraft.client.multiplayer.chat.GuiMessage.Line,
        val fade: Float,
        val entrance: Float
    )

    private data class ImageHitBox(val url: String, val x: Float, val y: Float, val width: Float, val height: Float)

    private val messageAnimationStarts = WeakHashMap<GuiMessage, Long>()
    private val imageHitBoxes = mutableListOf<ImageHitBox>()
    private val inputReveal = FloatAnimation(0f, 0f, 320L, Easing.EASE_OUT_QUINT)
    private var inputRevealInitialized = false

    init {
        addSetting(customBackground)
    }

    fun shouldReplaceVanillaRenderer(): Boolean = enabled && customBackground.value

    fun styleVanillaBackground(color: Int): Int =
        if (shouldReplaceVanillaRenderer() && (color and 0x00FFFFFF) == 0) 0 else color

    fun shouldDrawBackdropBlur(): Boolean = shouldReplaceVanillaRenderer() && HUDModule.blur.value

    /** Native chat extraction is cancelled by the mixin; this NanoVG pass draws the optional replacement. */
    fun renderChatBar() {
        imageHitBoxes.clear()
        val replaceRenderer = shouldReplaceVanillaRenderer()
        if (!replaceRenderer) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN) return

        val screenW = ResolutionHelper.scaledWidth
        val screenH = ResolutionHelper.scaledHeight
        val chat = mc.gui.hud.getChat()
        val chatScreen = mc.gui.screen() as? ChatScreen
        val focused = chatScreen != null
        val reveal = getInputReveal(focused)
        val scale = mc.options.chatScale().get().toFloat().coerceAtLeast(0.01f)
        val chatWidth = ChatComponent.getWidth(mc.options.chatWidth().get()).toFloat() / ResolutionHelper.resolutionScaleFactor
        val chatHeight = if (focused) mc.options.chatHeightFocused().get() else mc.options.chatHeightUnfocused().get()
        val lineHeight = (9.0 * (mc.options.chatLineSpacing().get() + 1.0)).toInt().coerceAtLeast(1)
        val scaledChatHeight = ChatComponent.getHeight(chatHeight).toFloat() / ResolutionHelper.resolutionScaleFactor
        val pageLines = (scaledChatHeight / lineHeight).toInt()
        val access = chat as ChatComponentAccess
        val lines = access.`nocturne$getTrimmedMessages`()
        val scroll = access.`nocturne$getChatScrollbarPos`().coerceAtLeast(0)
        val localBottom = floor((screenH - 40f) / scale).toInt()
        val localWidth = ceil(chatWidth / scale).toInt()
        val now = mc.gui.hud.getGuiTicks()
        val shownLineCount = minOf((lines.size - scroll).coerceAtLeast(0), pageLines)
        val visibleHasImage = (scroll until (scroll + shownLineCount).coerceAtMost(lines.size))
            .any { IrcImageCache.imageUrl(lines[it].parent().content().string) != null }
        if (visibleHasImage) {
            drawChatRows(
                lines, scroll, localBottom, localWidth, lineHeight, scaledChatHeight * scale,
                scale, now, (0.1f + 0.9f * mc.options.chatOpacity().get().toFloat()).coerceIn(0f, 1f)
            )
        } else if (replaceRenderer) {
            // Preserve the established vanilla-sized layout when there are no image messages.
            val chatLines = (shownLineCount - 1 downTo 0).mapNotNull { index ->
                val line = lines.getOrNull(scroll + index) ?: return@mapNotNull null
                ChatLine(index, line, 1f, messageEntrance(line, now))
            }
            drawMessagePanels(chatLines, localBottom, lineHeight, localWidth, scale)
            drawMessageText(chatLines, localBottom, lineHeight, scale)
        }
        if (focused) drawScrollbar(shownLineCount, lines.size, scroll, localBottom, lineHeight, localWidth, scale)
        if (chatScreen != null && replaceRenderer) {
            val inputOpacity = if (mc.options.backgroundForChatOnly().get()) 0.5f else mc.options.textBackgroundOpacity().get().toFloat()
            val inputY = screenH - 14f + 3f * (1f - reveal)
            val inputBounds = Rect2f(2f, inputY, screenW - 4f, 12f)
            drawPanel(inputBounds.x, inputBounds.y, inputBounds.width, inputBounds.height, CornerRadii(6f), inputOpacity * reveal)
            drawInput(chatScreen, reveal)
        }
    }

    /** Draw normal trimmed text rows, replacing authenticated IRC image URLs with inline previews. */
    private fun drawChatRows(
        lines: List<GuiMessage.Line>,
        scroll: Int,
        localBottom: Int,
        localWidth: Int,
        lineHeight: Int,
        chatHeight: Float,
        scale: Float,
        nowTicks: Int,
        textAlpha: Float
    ) {
        if (lines.isEmpty() || scroll >= lines.size) return
        val mc = Minecraft.getInstance()
        val lineH = lineHeight * scale
        val bottom = localBottom * scale
        val topLimit = bottom - chatHeight
        val panelW = (localWidth + 8f) * scale
        val textBottomOffset = (8.0 * (mc.options.chatLineSpacing().get() + 1.0) - 4.0 * mc.options.chatLineSpacing().get()).roundToInt()
        val imageScale = scale.coerceIn(0.65f, 1.5f)
        val imageH = minOf(72f * imageScale, (chatHeight - 24f * imageScale).coerceAtLeast(20f * imageScale))
        val imageW = minOf(180f * imageScale, (panelW - 10f * imageScale).coerceAtLeast(36f * imageScale))
        val fontSize = 8f * scale
        val backgroundAlpha = mc.options.textBackgroundOpacity().get().toFloat()
        var yBottom = bottom
        var index = scroll.coerceAtLeast(0)

        while (index < lines.size && yBottom > topLimit) {
            val firstLine = lines[index]
            val parent = firstLine.parent()
            var groupEnd = index + 1
            while (groupEnd < lines.size && lines[groupEnd].parent() === parent) groupEnd++
            val imageUrl = IrcImageCache.imageUrl(parent.content().string)
            val entrance = messageEntrance(firstLine, nowTicks)
            val slide = (1f - entrance) * 6f * scale
            if (imageUrl != null) {
                val entryH = imageH + 20f * imageScale
                if (yBottom - entryH < topLimit && yBottom < bottom) break
                val cardY = yBottom - entryH + slide
                drawPanel(0f, cardY, panelW, entryH, CornerRadii(3f * imageScale), backgroundAlpha * entrance)
                val prefix = parent.content().string.replace(imageUrl, "").trim().ifEmpty { "[Image]" }.take(96)
                val prefixSequence = NameProtect.protectChatSequence(FormattedCharSequence.forward(prefix, Style.EMPTY))
                NanoVGRenderer.withScissor(4f * scale, cardY + 2f * imageScale, panelW - 8f * scale, 10f * imageScale) {
                    drawFormattedLine(prefixSequence, 4f * scale, cardY + 2f * imageScale, fontSize, textAlpha * entrance)
                }
                val imageX = 4f * scale
                val imageY = cardY + 14f * imageScale
                IrcImageCache.draw(imageUrl, imageX, imageY, imageW, imageH, textAlpha * entrance)
                imageHitBoxes += ImageHitBox(imageUrl, imageX, imageY, imageW, imageH)
                yBottom -= entryH
                index = groupEnd
            } else {
                for (lineIndex in index until groupEnd) {
                    if (yBottom - lineH < topLimit && yBottom < bottom) break
                    val row = lines[lineIndex]
                    val rowY = yBottom - lineH + slide
                    drawPanel(0f, rowY, panelW, lineH, CornerRadii(minOf(2.5f * scale, lineH * 0.35f)), backgroundAlpha * entrance)
                    val baselineY = (yBottom / scale - textBottomOffset) * scale + slide
                    drawFormattedLine(ChatUtil.animateNocturnePrefix(row.content()), 4f * scale, baselineY, fontSize, textAlpha * entrance)
                    yBottom -= lineH
                    if (yBottom <= topLimit) break
                }
                index = groupEnd
            }
        }
    }

    @JvmStatic
    fun onImageDoubleClick(mouseX: Double, mouseY: Double): Boolean {
        val hit = imageHitBoxes.lastOrNull { box ->
            mouseX >= box.x && mouseX <= box.x + box.width && mouseY >= box.y && mouseY <= box.y + box.height
        } ?: return false
        IrcImageViewerScreen.open(hit.url)
        return true
    }

    private fun getInputReveal(focused: Boolean): Float {
        if (!inputRevealInitialized) {
            inputReveal.setImmediate(if (focused) 1f else 0f)
            inputRevealInitialized = true
        } else {
            inputReveal.animateTo(if (focused) 1f else 0f)
        }
        return inputReveal.value.coerceIn(0f, 1f)
    }

    private fun messageEntrance(line: net.minecraft.client.multiplayer.chat.GuiMessage.Line, nowTicks: Int): Float {
        val age = nowTicks - line.addedTime()
        if (age !in 0..20) {
            messageAnimationStarts.remove(line.parent())
            return 1f
        }
        val now = System.nanoTime()
        val parent = line.parent()
        val started = messageAnimationStarts[parent]
        if (started == FINISHED_ANIMATION) return 1f
        val animationStart = started ?: now.also { messageAnimationStarts[parent] = it }
        val t = ((now - animationStart) / MESSAGE_ENTRANCE_NANOS).coerceIn(0f, 1f)
        if (t >= 1f) {
            messageAnimationStarts[parent] = FINISHED_ANIMATION
            return 1f
        }
        return Easing.EASE_OUT_QUINT(t)
    }

    private fun drawMessagePanels(
        lines: List<ChatLine>,
        localBottom: Int,
        lineHeight: Int,
        localWidth: Int,
        scale: Float
    ) {
        if (lines.isEmpty()) return
        val configuredAlpha = Minecraft.getInstance().options.textBackgroundOpacity().get().toFloat()
        val width = (localWidth + 8f) * scale
        val height = lineHeight * scale
        val radius = minOf(3f * scale, height * 0.45f)
        lines.forEachIndexed { visibleIndex, row ->
            val slide = (1f - row.entrance) * 6f * scale
            val top = (localBottom - (row.sourceIndex + 1) * lineHeight) * scale + slide
            val alpha = (configuredAlpha * row.fade * row.entrance).coerceIn(0f, 1f)
            val radii = when {
                lines.size == 1 -> CornerRadii(radius)
                visibleIndex == 0 -> CornerRadii(radius, radius, 0f, 0f)
                visibleIndex == lines.lastIndex -> CornerRadii(0f, 0f, radius, radius)
                else -> CornerRadii.ZERO
            }
            drawPanel(0f, top, width, height, radii, alpha)
        }
    }

    private fun drawMessageText(lines: List<ChatLine>, localBottom: Int, lineHeight: Int, scale: Float) {
        val mc = Minecraft.getInstance()
        val chatOpacity = mc.options.chatOpacity().get().toFloat()
        val textAlpha = 0.1f + 0.9f * chatOpacity
        val spacing = mc.options.chatLineSpacing().get()
        val textBottomOffset = (8.0 * (spacing + 1.0) - 4.0 * spacing).roundToInt()
        val fontSize = 8f * scale
        val x = 4f * scale

        lines.forEach { row ->
            // Vanilla's handleMessage y is the row bottom minus the text baseline offset.
            // Background rectangles use (index + 1), but text does not.
            val localY = localBottom - row.sourceIndex * lineHeight - textBottomOffset
            val y = localY * scale + (1f - row.entrance) * 6f * scale
            drawFormattedLine(ChatUtil.animateNocturnePrefix(row.line.content()), x, y, fontSize, (textAlpha * row.fade * row.entrance).coerceIn(0f, 1f))
        }
    }

    private fun drawFormattedLine(sequence: FormattedCharSequence, x: Float, y: Float, fontSize: Float, alpha: Float) {
        val runs = mutableListOf<StyledRun>()
        sequence.accept { _, style, codePoint ->
            val color = style.color?.value ?: 0xFFFFFF
            val bold = style.isBold
            val italic = style.isItalic
            val underline = style.isUnderlined
            val strike = style.isStrikethrough
            val last = runs.lastOrNull()
            if (last != null && last.color == color && last.bold == bold && last.italic == italic &&
                last.underline == underline && last.strikethrough == strike
            ) {
                last.text.appendCodePoint(codePoint)
            } else {
                runs += StyledRun(StringBuilder().appendCodePoint(codePoint), color, bold, italic, underline, strike)
            }
            true
        }

        val font = RenderEngine.getFontByName(HUDModule.fontChoice.value)
            ?: RenderEngine.wqyFont ?: RenderEngine.defaultFont ?: RenderEngine.fontRenderer.defaultFont
        font.prepare(fontSize, NanoVGConstants.ALIGN_LEFT or NanoVGConstants.ALIGN_TOP)
        var cursorX = x
        for (run in runs) {
            val text = run.text.toString()
            if (text.isEmpty()) continue
            val rgb = run.color
            val color = NanoVGColor(
                ((rgb shr 16) and 0xFF) / 255f,
                ((rgb shr 8) and 0xFF) / 255f,
                (rgb and 0xFF) / 255f,
                alpha
            )
            val width = font.measureWidth(text, fontSize)
            val shadow = NanoVGColor(0f, 0f, 0f, alpha * 0.65f)
            if (run.italic) {
                val transform = NanoVGTransform(RenderEngine.nanoVG.handle)
                transform.withTransform {
                    transform.translate(cursorX, y).skewX(-0.18f)
                    font.drawText(text, 0.65f * scaleFactor(fontSize), 0.55f * scaleFactor(fontSize), shadow, fontSize)
                    if (run.bold) font.drawText(text, 0.35f * scaleFactor(fontSize), 0f, color, fontSize)
                    font.drawText(text, 0f, 0f, color, fontSize)
                }
            } else {
                font.drawText(text, cursorX + 0.65f * scaleFactor(fontSize), y + 0.55f * scaleFactor(fontSize), shadow, fontSize)
                if (run.bold) font.drawText(text, cursorX + 0.35f * scaleFactor(fontSize), y, color, fontSize)
                font.drawText(text, cursorX, y, color, fontSize)
            }
            val decorationY = y + fontSize * 0.82f
            if (run.underline) Draw2D.rect(cursorX, decorationY, width, maxOf(0.65f, fontSize * 0.07f), color)
            if (run.strikethrough) Draw2D.rect(cursorX, y + fontSize * 0.48f, width, maxOf(0.65f, fontSize * 0.07f), color)
            cursorX += width
        }
    }

    private fun scaleFactor(fontSize: Float): Float = fontSize / 8f

    private fun drawScrollbar(visibleCount: Int, totalCount: Int, scroll: Int, localBottom: Int, lineHeight: Int, localWidth: Int, scale: Float) {
        if (visibleCount <= 0 || totalCount <= visibleCount) return
        val visibleHeight = visibleCount * lineHeight
        val totalHeight = totalCount * lineHeight
        val trackHeight = visibleHeight.toFloat()
        val thumbHeight = (trackHeight * visibleHeight / totalHeight).coerceAtLeast(6f).coerceAtMost(trackHeight)
        val trackTop = (localBottom - visibleHeight).toFloat()
        val maxScroll = (totalCount - visibleCount).coerceAtLeast(1)
        val scrollProgress = (scroll.toFloat() / maxScroll).coerceIn(0f, 1f)
        val thumbY = trackTop + (trackHeight - thumbHeight) * (1f - scrollProgress)
        val screenWidth = ResolutionHelper.scaledWidth
        val thumbWidth = 2f * scale
        val x = ((localWidth + 4f) * scale).coerceIn(0f, (screenWidth - thumbWidth).coerceAtLeast(0f))
        Draw2D.roundedRect(x, trackTop * scale, thumbWidth, trackHeight * scale, thumbWidth * 0.5f,
            NanoVGColor(0.12f, 0.12f, 0.12f, 0.48f))
        Draw2D.roundedRect(x, thumbY * scale, thumbWidth, thumbHeight * scale, thumbWidth * 0.5f,
            NanoVGColor(0.80f, 0.80f, 0.80f, 0.66f))
    }

    private fun drawInput(screen: ChatScreen, reveal: Float) {
        if (screen !is ChatScreenAccess) return
        val input = screen.`nocturne$getInput`()
        val inputAccess = input as EditBoxAccess
        val font = RenderEngine.getFontByName(HUDModule.fontChoice.value)
            ?: RenderEngine.wqyFont ?: RenderEngine.defaultFont ?: RenderEngine.fontRenderer.defaultFont
        val fontSize = 9f
        font.prepare(fontSize, NanoVGConstants.ALIGN_LEFT or NanoVGConstants.ALIGN_TOP)
        val inputX = 4f
        val inputY = ResolutionHelper.scaledHeight - 12f
        val inputWidth = (ResolutionHelper.scaledWidth - inputX - 4f).coerceAtLeast(0f)
        val x = inputX + 4f
        val y = inputY + 2f
        val text = input.value
        val displayStart = inputAccess.`nocturne$getDisplayPos`().coerceIn(0, text.length)
        val visibleText = text.substring(displayStart)
        val cursor = input.cursorPosition.coerceIn(displayStart, text.length)
        val highlight = inputAccess.`nocturne$getHighlightPos`().coerceIn(displayStart, text.length)
        val prefix = text.substring(displayStart, cursor)
        val selectionStartIndex = minOf(cursor, highlight)
        val selectionEndIndex = maxOf(cursor, highlight)
        val selectionPrefix = text.substring(displayStart, selectionStartIndex)
        val selected = text.substring(selectionStartIndex, selectionEndIndex)
        val baseColor = NanoVGColor(1f, 1f, 1f, reveal)
        val textRight = (inputX + inputWidth - 4f).coerceAtLeast(x)
        val maxTextWidth = (textRight - x).coerceAtLeast(0f)

        NanoVGRenderer.withScissor(x, y, maxTextWidth, fontSize + 2f) {
            if (selected.isNotEmpty()) {
                val selectionStart = font.measureWidth(selectionPrefix, fontSize).coerceIn(0f, maxTextWidth)
                val selectionEnd = (selectionStart + font.measureWidth(selected, fontSize)).coerceIn(selectionStart, maxTextWidth)
                if (selectionEnd > selectionStart) {
                    Draw2D.roundedRect(x + selectionStart, y, selectionEnd - selectionStart, fontSize + 1f, 1.5f,
                        NanoVGColor(0.22f, 0.48f, 0.82f, 0.72f * reveal))
                }
            }

            font.drawText(visibleText, x, y, baseColor, fontSize)
            val suggestion = inputAccess.`nocturne$getSuggestion`()
            if (!suggestion.isNullOrEmpty()) {
                val suggestionX = if (text.isEmpty()) x else x + font.measureWidth(prefix, fontSize)
                if (text.isEmpty() || cursor == text.length) {
                    font.drawText(suggestion, suggestionX, y, NanoVGColor(0.72f, 0.72f, 0.72f, reveal), fontSize)
                }
            }

            val cursorX = (x + font.measureWidth(prefix, fontSize)).coerceIn(x, x + maxTextWidth)
            if (input.isFocused && reveal > 0.01f && (Minecraft.getInstance().gui.hud.getGuiTicks() / 6) % 2 == 0) {
                val caretHeight = minOf(fontSize, 8f)
                val caretY = y + (fontSize - caretHeight) * 0.5f
                Draw2D.rect(cursorX, caretY, 1f, caretHeight, baseColor)
            }
        }
    }

    private fun drawPanel(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radii: CornerRadii,
        opacity: Float
    ) {
        if (width <= 0f || height <= 0f) return
        val alpha = opacity.coerceIn(0f, 1f)
        val context = RenderEngine.nanoVG.handle
        val tintAlpha = alpha * 0.12f
        val blurred = HUDModule.blur.value && RenderEngine.backdropBlurRenderer.withBlurPaint(
            // Use the full-screen blur prepared before the HUD pass. Sampling a second region
            // from the HUD target while NanoVG is active was unreliable on the game's GL pipeline.
            HUDModule.blurRadius.getFloat(), 1f
        ) { paint -> RoundedRect.draw(context, x, y, width, height, radii, paint) }
        val finalTintAlpha = if (blurred) tintAlpha else alpha
        Draw2D.roundedRect(x, y, width, height, radii, NanoVGColor(0.025f, 0.03f, 0.04f, finalTintAlpha))
    }

    private const val MESSAGE_ENTRANCE_NANOS = 360_000_000f
    private const val FINISHED_ANIMATION = -1L
}
