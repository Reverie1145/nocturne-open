package cn.reverie.app.agent.ui

import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.math.Rect2f
import cn.reverie.render.ui.UIElement
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.nanovg.NanoVG.*
import kotlin.math.floor

/** Drawing, selection and scrollbar input share the same clipped text viewport. */
internal class AgentTextArea(initial: String = "", val multiline: Boolean = true, var readOnly: Boolean = false,
                             var lineNumbers: Boolean = false, maxLength: Int = 256000) : UIElement() {
    val buffer = EditorBuffer(initial, maxLength)
    var password = false
    var placeholder = ""
    var inputClip: Rect2f = Rect2f.EMPTY
    var onEdit: (() -> Unit)? = null
    private val vertical = AgentScrollState()
    private val horizontal = AgentScrollState()
    var scrollLine: Int
        get() = (vertical.target / lineHeight).toInt()
        set(value) { updateExtent(); vertical.jump(value * lineHeight) }
    var scrollX: Float
        get() = horizontal.value
        set(value) { updateHorizontalExtent(); horizontal.jump(value) }
    private val focusAnim = FloatAnimation(0f, 0f, 150L, CubicBezier.EASE_OUT)
    private var cachedText = "\u0000"
    private var cachedLines = listOf("")
    private var cachedWidthText = "\u0000"
    private var cachedPassword = false
    private var widestLine = 0f
    private var dragging = false
    private var draggingBar = false
    private var barGrab = 0f
    private var lastRenderAt = 0L
    private val size = 13f
    private val lineHeight = 20f
    private val gutter get() = if (lineNumbers) 52f else 12f
    private val textHeight get() = (height - 16f).coerceAtLeast(lineHeight)
    private fun lines(): List<String> {
        if (cachedText != buffer.text) { cachedText = buffer.text; cachedLines = buffer.text.split('\n') }
        return cachedLines
    }
    private fun display(text: String) = if (password) "•".repeat(text.length) else text.replace("\t", "    ")
    private fun measuredPrefix(line: String, offset: Int): Float =
        RenderEngine.fontRenderer.getStringWidth(display(AgentTextLayout.prefix(line, offset)), size)
    private fun updateExtent() {
        vertical.extent(if (multiline) lines().size * lineHeight else textHeight, textHeight)
    }
    private fun updateHorizontalExtent() {
        if (cachedWidthText != buffer.text || cachedPassword != password) {
            cachedWidthText = buffer.text; cachedPassword = password
            widestLine = lines().maxOfOrNull { RenderEngine.fontRenderer.getStringWidth(display(it), size) } ?: 0f
        }
        horizontal.extent(widestLine, (width - gutter - 14f).coerceAtLeast(1f))
    }
    fun setText(value: String, follow: Boolean = false) {
        if (buffer.text == value) return
        updateExtent()
        val previous = vertical.target
        val atEnd = vertical.target >= vertical.maximum - lineHeight
        val preserveSelection = readOnly && isFocused && buffer.selectionStart != buffer.selectionEnd && value.startsWith(buffer.text)
        val cursor = buffer.cursor; val anchor = buffer.anchor
        buffer.load(value)
        if (preserveSelection) { buffer.move(anchor); buffer.move(cursor, true) }
        updateExtent()
        vertical.jump(if (follow && atEnd && !preserveSelection) vertical.maximum else previous)
    }
    private fun visibleLines() = (textHeight / lineHeight).toInt().coerceAtLeast(1)
    fun tail() { updateExtent(); vertical.jump(vertical.maximum) }
    fun blur() { isFocused = false; dragging = false; draggingBar = false }
    override fun isMouseOver(mouseX: Float, mouseY: Float): Boolean =
        super.isMouseOver(mouseX, mouseY) && inputClip.contains(mouseX, mouseY)
    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (width <= 0f || height <= 0f) return
        val now = System.nanoTime()
        val dt = if (lastRenderAt == 0L) 0f else ((now - lastRenderAt) / 1_000_000_000f).coerceIn(0f, 0.1f)
        lastRenderAt = now
        updateExtent(); updateHorizontalExtent()
        vertical.advance(dt); horizontal.advance(dt)
        if (dragging) setCaret(mouseX, mouseY, true)
        if (draggingBar) moveScrollbar(mouseY)
        val theme = AgentStyle.theme()
        val ax = absoluteX; val ay = absoluteY
        focusAnim.durationMs = (150L / AgentStyle.speed).toLong()
        focusAnim.animateTo(if (isFocused) 1f else 0f)
        Draw2D.roundedRect(ax, ay, width, height, AgentStyle.radius, theme.surface)
        Draw2D.roundedOutline(ax, ay, width, height, AgentStyle.radius,
            theme.border.lerp(theme.borderFocus, focusAnim.value), 0.8f)
        val font = RenderEngine.fontRenderer
        val context = RenderEngine.nanoVG.handle
        if (context == 0L) return
        val all = lines()
        val first = floor(vertical.value / lineHeight).toInt().coerceIn(0, all.lastIndex)
        val textClip = Rect2f(ax + gutter - 1f, ay + 4f, (width - gutter - 9f).coerceAtLeast(0f), (height - 8f).coerceAtLeast(0f))
        nvgSave(context); nvgIntersectScissor(context, ax + 3f, ay + 3f, (width - 6f).coerceAtLeast(0f), (height - 6f).coerceAtLeast(0f))
        try {
            var offset = all.take(first).sumOf { it.length + 1 }
            if (lineNumbers) {
                Draw2D.line(ax + gutter - 10f, ay + 8f, ax + gutter - 10f, ay + height - 8f, theme.border, 0.8f)
            }
            for (index in first until minOf(all.size, first + visibleLines() + 2)) {
                val line = all[index]
                val y = if (multiline) ay + 10f + index * lineHeight - vertical.value else ay + (height - 16f) * 0.5f
                if (lineNumbers) {
                    val number = (index + 1).toString()
                    val numberWidth = font.getStringWidth(number, 11f)
                    font.drawString(number, ax + gutter - 18f - numberWidth, y, theme.textDisabled, 11f)
                }
                nvgSave(context); nvgIntersectScissor(context, textClip.x, textClip.y, textClip.width, textClip.height)
                try {
                    val selStart = (buffer.selectionStart - offset).coerceIn(0, line.length)
                    val selEnd = (buffer.selectionEnd - offset).coerceIn(0, line.length)
                    if (isFocused && selEnd > selStart) {
                        val left = measuredPrefix(line, selStart); val right = measuredPrefix(line, selEnd)
                        Draw2D.roundedRect(ax + gutter + left - horizontal.value, y - 2f, right - left, lineHeight, 2f, theme.primary.withAlpha(0.22f))
                    }
                    val shown = display(line)
                    var start = 0
                    var prefixWidth = 0f
                    if (shown.length > 2000 && horizontal.value > 0f) {
                        var low = 0; var high = shown.length
                        while (low < high) {
                            val mid = (low + high + 1) / 2
                            if (font.getStringWidth(AgentTextLayout.prefix(shown, mid), size) < horizontal.value) low = mid else high = mid - 1
                        }
                        start = AgentTextLayout.prefix(shown, (low - 2).coerceAtLeast(0)).length
                        prefixWidth = font.getStringWidth(shown.substring(0, start), size)
                    }
                    val segment = AgentTextLayout.prefix(shown.substring(start), 2000)
                    font.drawString(segment, ax + gutter - horizontal.value + prefixWidth, y, theme.textPrimary, size)
                    if (isFocused && !readOnly && buffer.cursor in offset..(offset + line.length) && (System.currentTimeMillis() / 500) % 2 == 0L) {
                        val cursorX = ax + gutter - horizontal.value + measuredPrefix(line, buffer.cursor - offset)
                        Draw2D.line(cursorX, y - 1f, cursorX, y + 16f, theme.primaryHover, 1.2f)
                    }
                } finally { nvgRestore(context) }
                offset += line.length + 1
            }
            if (buffer.text.isEmpty() && placeholder.isNotEmpty()) {
                val shown = AgentTextLayout.fit(placeholder, textClip.width) { font.getStringWidth(it, size) }
                font.drawString(shown, ax + gutter, if (multiline) ay + 11f else ay + (height - 16f) * 0.5f, theme.textSecondary, size)
            }
        } finally { nvgRestore(context) }
        scrollbar()?.let { Draw2D.roundedRect(it, 1.5f, theme.textSecondary.withAlpha(if (draggingBar) 0.75f else 0.35f)) }
    }
    private fun scrollbar(): Rect2f? {
        if (vertical.maximum <= 0f || height <= 12f || !multiline) return null
        val track = height - 12f
        val barH = (track * textHeight / (vertical.maximum + textHeight)).coerceIn(minOf(16f, track), track)
        return Rect2f(absoluteX + width - 6f, absoluteY + 6f + (track - barH) * vertical.value / vertical.maximum, 3f, barH)
    }
    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isMouseOver(mouseX, mouseY) || button != 0) { blur(); return false }
        isFocused = true
        val bar = scrollbar()
        if (bar != null && mouseX >= absoluteX + width - 12f) {
            draggingBar = true
            barGrab = if (bar.contains(mouseX.coerceIn(bar.x, bar.x2), mouseY)) mouseY - bar.y else bar.height * 0.5f
            moveScrollbar(mouseY)
        } else { dragging = true; setCaret(mouseX, mouseY, false) }
        return true
    }
    private fun moveScrollbar(my: Float) {
        val bar = scrollbar() ?: return
        val travel = height - 12f - bar.height
        if (travel > 0f) vertical.jump((my - absoluteY - 6f - barGrab) / travel * vertical.maximum)
    }
    private fun setCaret(mx: Float, my: Float, select: Boolean) {
        val all = lines()
        val row = if (multiline) floor((my - absoluteY - 10f + vertical.value) / lineHeight).toInt().coerceIn(0, all.lastIndex) else 0
        val line = all[row]
        val target = (mx - absoluteX - gutter + horizontal.value).coerceAtLeast(0f)
        var low = 0; var high = line.codePointCount(0, line.length)
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (measuredPrefix(line, line.offsetByCodePoints(0, mid)) <= target) low = mid else high = mid - 1
        }
        buffer.move(all.take(row).sumOf { it.length + 1 } + line.offsetByCodePoints(0, low), select)
    }
    override fun mouseDragged(mouseX: Float, mouseY: Float, button: Int, dragX: Float, dragY: Float): Boolean {
        if (button != 0) return false
        if (draggingBar) { moveScrollbar(mouseY); return true }
        if (!dragging) return false
        if (mouseY < absoluteY + 8f) vertical.scroll(-lineHeight)
        else if (mouseY > absoluteY + height - 8f) vertical.scroll(lineHeight)
        setCaret(mouseX, mouseY, true); return true
    }
    override fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean {
        val handled = dragging || draggingBar; dragging = false; draggingBar = false; return handled
    }
    override fun mouseScrolled(mouseX: Float, mouseY: Float, deltaX: Float, deltaY: Float): Boolean {
        if (!isMouseOver(mouseX, mouseY)) return false
        updateExtent()
        if (deltaX != 0f) { updateHorizontalExtent(); return horizontal.scroll(-deltaX * 35f) }
        // A one-line form field passes the wheel to its surrounding settings viewport.
        return multiline && vertical.scroll(-deltaY * 3f * lineHeight)
    }
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (!isFocused) return false
        val ctrl = modifiers and GLFW_MOD_CONTROL != 0 || modifiers and GLFW_MOD_SUPER != 0
        val shift = modifiers and GLFW_MOD_SHIFT != 0
        val before = buffer.text
        if (ctrl) when (keyCode) {
            GLFW_KEY_A -> buffer.selectAll()
            GLFW_KEY_C -> if (!password) Minecraft.getInstance().keyboardHandler.clipboard = buffer.selectedText
            GLFW_KEY_X -> if (!readOnly && !password) { Minecraft.getInstance().keyboardHandler.clipboard = buffer.selectedText; buffer.insert("") }
            GLFW_KEY_V -> if (!readOnly) buffer.insert(Minecraft.getInstance().keyboardHandler.clipboard.let { if (multiline) it else it.replace('\r', ' ').replace('\n', ' ') })
            GLFW_KEY_Z -> if (!readOnly) { if (shift) buffer.redo() else buffer.undo() }
            GLFW_KEY_Y -> if (!readOnly) buffer.redo()
            GLFW_KEY_HOME -> buffer.home(shift, true)
            GLFW_KEY_END -> buffer.end(shift, true)
            else -> return false
        } else when (keyCode) {
            GLFW_KEY_LEFT -> buffer.horizontal(-1, shift)
            GLFW_KEY_RIGHT -> buffer.horizontal(1, shift)
            GLFW_KEY_UP -> if (multiline) buffer.vertical(-1, shift)
            GLFW_KEY_DOWN -> if (multiline) buffer.vertical(1, shift)
            GLFW_KEY_PAGE_UP -> if (multiline) repeat(visibleLines()) { buffer.vertical(-1, shift) }
            GLFW_KEY_PAGE_DOWN -> if (multiline) repeat(visibleLines()) { buffer.vertical(1, shift) }
            GLFW_KEY_HOME -> buffer.home(shift)
            GLFW_KEY_END -> buffer.end(shift)
            GLFW_KEY_BACKSPACE -> if (!readOnly) buffer.backspace()
            GLFW_KEY_DELETE -> if (!readOnly) buffer.delete()
            GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER -> if (!readOnly && multiline) buffer.insert("\n") else return false
            GLFW_KEY_TAB -> if (!readOnly && multiline) buffer.insert("    ") else return false
            else -> return false
        }
        if (before != buffer.text) onEdit?.invoke()
        ensureCaret(); return true
    }
    fun codepoint(codepoint: Int): Boolean {
        if (!isFocused || readOnly || !Character.isValidCodePoint(codepoint) || codepoint < 32 || codepoint in 0xD800..0xDFFF) return false
        buffer.insert(String(Character.toChars(codepoint))); ensureCaret(); onEdit?.invoke(); return true
    }
    private fun ensureCaret() {
        updateExtent(); updateHorizontalExtent()
        val row = buffer.line
        if (row * lineHeight < vertical.target) vertical.jump(row * lineHeight)
        if ((row + 1) * lineHeight > vertical.target + textHeight) vertical.jump((row + 1) * lineHeight - textHeight)
        val line = lines().getOrElse(row) { "" }
        val caret = measuredPrefix(line, buffer.column)
        val available = (width - gutter - 20f).coerceAtLeast(20f)
        if (caret - horizontal.target > available) horizontal.jump(caret - available)
        if (caret < horizontal.target) horizontal.jump(caret)
    }
}
