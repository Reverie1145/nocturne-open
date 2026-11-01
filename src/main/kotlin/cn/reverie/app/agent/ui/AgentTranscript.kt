package cn.reverie.app.agent.ui

import cn.reverie.app.agent.*
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.math.Rect2f
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UITheme
import org.lwjgl.nanovg.NanoVG.*
import java.text.SimpleDateFormat
import java.util.Date

/** A native, clipped message timeline with independent tool cards and live Markdown output. */
internal class AgentTranscript {
    private data class Item(val key: String, val kind: String, val text: String, val time: Long,
        val tool: String? = null, val result: AgentEntry? = null, val live: Boolean = false)
    private data class Block(val source: MarkdownBlock, val lines: List<MarkdownLine>, val size: Float,
        val height: Float, val indent: Float, val table: List<List<List<MarkdownLine>>> = emptyList())
    private data class MessageLayout(val blocks: List<Block>, val height: Float)
    val scroll = AgentScrollState()
    private val expanded = mutableSetOf<String>()
    private val cached = linkedMapOf<String, MessageLayout>()
    private var sessionId = ""
    private var dataSignature = ""
    private var contentHeight = 0f
    private var dragScroll = false
    private var dragY = 0f
    private var dragStart = 0f
    private var viewport = Rect2f.EMPTY
    private var theme = UITheme.DARK
    private var registerLink: (Rect2f, String, () -> Unit) -> Unit = { _, _, _ -> }
    private val timeFormat = SimpleDateFormat("HH:mm")
    fun reset() { dataSignature = ""; sessionId = ""; scroll.jump(0f); cached.clear(); expanded.clear() }
    fun wheel(x: Float, y: Float, amount: Float): Boolean {
        if (!viewport.contains(x, y)) return false
        scroll.scroll(-amount * 58f); return true
    }
    fun mouseDown(x: Float, y: Float): Boolean {
        if (contentHeight <= viewport.height || !Rect2f(viewport.x2 - 10f, viewport.y, 10f, viewport.height).contains(x, y)) return false
        dragScroll = true; dragY = y; dragStart = scroll.value; return true
    }
    fun mouseDrag(y: Float) {
        if (!dragScroll) return
        val thumb = (viewport.height * viewport.height / contentHeight).coerceAtLeast(24f)
        scroll.jump(dragStart + (y - dragY) * (contentHeight - viewport.height) / (viewport.height - thumb).coerceAtLeast(1f))
    }
    fun mouseUp() { dragScroll = false }
    fun render(id: String, entries: List<AgentEntry>, live: AgentLiveResponse?, approvalId: String?, bounds: Rect2f,
               mx: Float, my: Float, seconds: Float, colors: UITheme,
               button: (String, Rect2f, String, () -> Unit) -> Unit,
               clip: (Rect2f, () -> Unit) -> Unit, copy: (String) -> Unit, link: (String) -> Unit,
               linkArea: (Rect2f, String, () -> Unit) -> Unit,
               details: (String, String) -> Unit) {
        viewport = bounds; theme = colors
        registerLink = linkArea
        val changedSession = sessionId != id
        if (changedSession) { reset(); sessionId = id }
        val items = mutableListOf<Item>()
        val tail = entries.takeLast(100)
        val results = tail.filter { it.callId != null && it.kind in setOf("result", "tool_error") }.associateBy { it.callId }
        val toolIds = tail.filter { it.kind == "tool" }.mapNotNull { it.callId }.toSet()
        tail.forEachIndexed { index, entry ->
            if (entry.kind in setOf("result", "tool_error") && entry.callId in toolIds) return@forEachIndexed
            items += Item(entry.callId ?: (entry.time.toString() + "-" + index), entry.kind, entry.text, entry.time, entry.tool,
                if (entry.kind == "tool") results[entry.callId] else null)
        }
        if (live != null) {
            items += Item("live-text", "assistant", live.text, live.startedAt, live = true)
            live.calls.forEachIndexed { index, call ->
                items += Item("live-tool-" + index, "tool", call.arguments.string("preview"), live.startedAt, call.name, live = true)
            }
        }
        val width = bounds.width - 34f
        val layouts = items.map { item -> messageLayout(item, width - 32f) }
        val nextHeight = layouts.sumOf { it.height.toDouble() + 12.0 }.toFloat() + 24f
        val nextData = id + ":" + entries.size + ":" + entries.lastOrNull()?.time + ":" + entries.lastOrNull()?.text?.hashCode() + ":" + live?.text?.hashCode() + ":" + live?.calls?.hashCode()
        val following = changedSession || scroll.target >= scroll.maximum - 28f
        scroll.extent(nextHeight, bounds.height)
        if (nextData != dataSignature && following) {
            if (changedSession) scroll.jump(scroll.maximum) else scroll.scroll(scroll.maximum - scroll.target)
        }
        dataSignature = nextData; contentHeight = nextHeight; scroll.advance(seconds)
        Draw2D.roundedRect(bounds, AgentStyle.radius, theme.surface.withAlpha(0.018f))
        clip(bounds) {
            if (items.isEmpty()) {
                text("开始一个新任务", bounds.x + 28f, bounds.y + 40f, theme.textPrimary, 20f)
                text("让 Agent 编写脚本、创建模块或检查客户端状态。", bounds.x + 28f, bounds.y + 78f, theme.textSecondary, 12f)
                listOf("编写一个低血量提醒脚本", "查看已启用的模块并说明用途", "为脚本添加 ClickGUI 模块和可调设置").forEachIndexed { index, value ->
                    button(value, Rect2f(bounds.x + 28f, bounds.y + 119f + index * 44f, bounds.width - 64f, 34f), "example-" + index) { details("任务示例", value) }
                }
                return@clip
            }
            var y = bounds.y + 12f - scroll.value
            items.forEachIndexed { index, item ->
                val measured = layouts[index]
                val box = Rect2f(bounds.x + 12f, y, width, measured.height)
                y += measured.height + 12f
                if (!box.intersects(bounds)) return@forEachIndexed
                val tool = item.kind in setOf("tool", "result", "tool_error")
                val error = item.kind in setOf("error", "tool_error") || item.result?.kind == "tool_error"
                val accent = when { error -> theme.error; tool -> theme.primaryHover; item.kind == "user" -> theme.textSecondary; else -> theme.textPrimary }
                if (tool || item.kind == "user" || error) {
                    Draw2D.roundedRect(box, AgentStyle.radius, if (tool) theme.primary.withAlpha(0.055f) else theme.surface.withAlpha(0.045f))
                    Draw2D.roundedOutline(box, AgentStyle.radius, accent.withAlpha(if (tool || error) 0.18f else 0.06f), 0.8f)
                }
                val heading = when (item.kind) {
                    "user" -> "你"; "assistant" -> if (item.live) "Nocturne · 正在输出" else "Nocturne"
                    "partial" -> "Nocturne · 已中断的部分输出"; "tool" -> item.tool ?: "工具调用"
                    "result" -> item.tool ?: "工具结果"; "tool_error" -> item.tool ?: "工具错误"
                    "error" -> "请求失败"; else -> "执行状态"
                }
                if (tool) {
                    val state = when { item.live -> "生成参数"; error -> "失败"; item.result != null || item.kind == "result" -> "已完成"; item.key == approvalId -> "等待授权"; else -> "执行中" }
                    val label = (if (item.key in expanded) "− " else "+ ") + heading + " · " + state
                    button(label, Rect2f(box.x + 10f, box.y + 8f, box.width - 144f, 26f), "expand-" + item.key) {
                        if (!expanded.add(item.key)) expanded.remove(item.key)
                    }
                } else text(heading, box.x + 16f, box.y + 13f, accent, 11f)
                if (!tool) text(timeFormat.format(Date(item.time)), box.x2 - 176f, box.y + 14f, theme.textDisabled, 10f)
                button("原文", Rect2f(box.x2 - 128f, box.y + 8f, 48f, 25f), "raw-message-" + item.key) {
                    details(heading, item.text + (item.result?.let { "\n\n" + it.text } ?: ""))
                }
                button("复制", Rect2f(box.x2 - 72f, box.y + 8f, 58f, 25f), "copy-message-" + item.key) {
                    copy(item.text + (item.result?.let { "\n\n" + it.text } ?: ""))
                }
                if (item.live) {
                    val pulse = (0.45 + 0.45 * kotlin.math.sin(System.nanoTime() / 300_000_000.0)).toFloat()
                    Draw2D.roundedRect(box.x + 16f, box.y + 35f, 24f, 2f, 1f, theme.primaryHover.withAlpha(pulse))
                }
                clip(Rect2f(box.x + 16f, box.y + 44f, box.width - 32f, box.height - 54f)) {
                    var by = box.y + 44f
                    measured.blocks.forEachIndexed { blockIndex, block ->
                        val region = Rect2f(box.x + 16f, by, box.width - 32f, block.height)
                        by += block.height + 8f
                        if (!region.intersects(bounds)) return@forEachIndexed
                        drawBlock(block, region, item.key + "-" + blockIndex, button, clip, copy, link)
                    }
                }
            }
            if (scroll.maximum > 0f) {
                val h = (bounds.height * bounds.height / contentHeight).coerceIn(24f, bounds.height)
                val sy = bounds.y + (bounds.height - h) * scroll.value / scroll.maximum
                Draw2D.roundedRect(bounds.x2 - 5f, sy, 3f, h, 1.5f, theme.textSecondary.withAlpha(if (dragScroll) 0.65f else 0.32f))
            }
        }
        if (cached.size > 200) { val iterator = cached.keys.iterator(); repeat(cached.size - 160) { iterator.next(); iterator.remove() } }
    }
    private fun messageLayout(item: Item, width: Float): MessageLayout {
        val tool = item.kind in setOf("tool", "result", "tool_error")
        val expandedTool = item.key in expanded
        val source = if (tool && !expandedTool) {
            val preview = item.result?.text ?: item.text
            AgentTextLayout.prefix(preview.replace('\n', ' '), 160).ifEmpty { "等待模型生成参数…" }
        } else if (tool) "**参数**\n\u0060\u0060\u0060json\n" + item.text + "\n\u0060\u0060\u0060\n" +
            (item.result?.let { "**执行结果**\n\u0060\u0060\u0060json\n" + it.text + "\n\u0060\u0060\u0060" } ?: "")
        else item.text.ifEmpty { if (item.live) "正在等待模型的第一段输出…" else "" }
        val limited = AgentTextLayout.prefix(source, 48000)
        val key = item.kind + ":" + width.toInt() + ":" + limited.hashCode() + ":" + expandedTool
        return cached.getOrPut(key) {
            val blocks = if (item.kind in setOf("assistant", "partial") || (tool && expandedTool)) AgentMarkdown.parse(limited)
                else listOf(MarkdownBlock("paragraph", listOf(MarkdownSpan(limited))))
            val measured = blocks.map { block ->
                val size = if (block.kind == "heading") when (block.level) { 1 -> 21f; 2 -> 18f; else -> 15f } else if (block.kind == "code") 12f else 13f
                val indent = when (block.kind) { "quote" -> 14f; "list" -> 22f + block.level * 12f; else -> 0f }
                val spans = if (block.kind == "code") AgentMarkdown.code(block.text.replace("\t", "    ")) else block.spans
                val lines = if (block.kind == "rule" || block.kind == "table") emptyList() else wrap(spans, (width - indent - if (block.kind == "code") 22f else 0f).coerceAtLeast(20f), size)
                val table = if (block.kind != "table") emptyList() else {
                    val count = block.rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
                    block.rows.mapIndexed { rowIndex, cells -> (0 until count).map { column ->
                        wrap(AgentMarkdown.inline(cells.getOrElse(column) { "" }, if (rowIndex == 0) AgentMarkdown.BOLD else 0), width / count - 16f, 12f)
                    } }
                }
                val height = when (block.kind) {
                    "rule" -> 8f
                    "code" -> 38f + lines.size * 19f + 10f
                    "table" -> table.sumOf { row -> (row.maxOfOrNull { it.size } ?: 1) * 18.0 + 16.0 }.toFloat()
                    else -> (lines.size * (size + 8f)).coerceAtLeast(size + 8f)
                }
                Block(block, lines, size, height, indent, table)
            }
            MessageLayout(measured, (measured.sumOf { it.height.toDouble() + 8.0 }.toFloat() + 54f).coerceAtLeast(82f))
        }
    }
    private fun wrap(spans: List<MarkdownSpan>, width: Float, size: Float) =
        AgentMarkdown.wrap(spans, width) { value, style -> measure(value, size, style) }
    private fun measure(value: String, size: Float, style: Int): Float = if (style and AgentMarkdown.CODE != 0)
        RenderEngine.withNamedFont("Consolas") { RenderEngine.fontRenderer.getStringWidth(value, size) }
        else RenderEngine.fontRenderer.getStringWidth(value, size)
    private fun text(value: String, x: Float, y: Float, color: NanoVGColor, size: Float) =
        RenderEngine.fontRenderer.drawString(value, x, y, color, size)
    private fun drawBlock(block: Block, bounds: Rect2f, key: String,
                          button: (String, Rect2f, String, () -> Unit) -> Unit,
                          clip: (Rect2f, () -> Unit) -> Unit, copy: (String) -> Unit, link: (String) -> Unit) {
        if (block.source.kind == "rule") { Draw2D.rect(bounds.x, bounds.y + 3f, bounds.width, 1f, theme.border.withAlpha(0.18f)); return }
        if (block.source.kind == "table") {
            var rowY = bounds.y
            block.table.forEachIndexed { rowIndex, row ->
                val height = (row.maxOfOrNull { it.size } ?: 1) * 18f + 16f
                Draw2D.rect(bounds.x, rowY, bounds.width, height, theme.surface.withAlpha(if (rowIndex == 0) 0.065f else 0.022f))
                val colWidth = bounds.width / row.size
                row.forEachIndexed { column, lines ->
                    val cell = Rect2f(bounds.x + colWidth * column, rowY, colWidth, height)
                    clip(cell.shrink(3f)) { lines.forEachIndexed { lineIndex, line -> drawLine(line, cell.x + 8f, cell.y + 8f + lineIndex * 18f, 12f, key + "-" + rowIndex + "-" + column + "-" + lineIndex, button, link) } }
                    Draw2D.rect(cell.x2 - 0.5f, cell.y, 0.5f, cell.height, theme.border.withAlpha(0.13f))
                }
                Draw2D.rect(bounds.x, rowY + height - 0.5f, bounds.width, 0.5f, theme.border.withAlpha(0.16f)); rowY += height
            }
            return
        }
        val code = block.source.kind == "code"
        if (code) {
            Draw2D.roundedRect(bounds, 6f, NanoVGColor(0.025f, 0.029f, 0.042f, 0.78f))
            Draw2D.roundedOutline(bounds, 6f, theme.border.withAlpha(0.1f), 0.8f)
            text(block.source.prefix.ifEmpty { "code" }, bounds.x + 11f, bounds.y + 10f, theme.textSecondary, 10f)
            button("复制代码", Rect2f(bounds.x2 - 84f, bounds.y + 5f, 74f, 24f), "code-" + key) { copy(block.source.text) }
        }
        if (block.source.kind == "quote") Draw2D.roundedRect(bounds.x, bounds.y + 1f, 2f, bounds.height - 3f, 1f, theme.primaryHover.withAlpha(0.55f))
        if (block.source.kind == "list") text(block.source.prefix, bounds.x + block.source.level * 12f, bounds.y, theme.primaryHover, block.size)
        block.lines.forEachIndexed { index, line ->
            drawLine(line, bounds.x + block.indent + if (code) 11f else 0f, bounds.y + index * (block.size + 8f) + if (code) 38f else 0f,
                block.size, key + "-" + index, button, link, block.source.kind == "heading", code)
        }
    }
    private fun drawLine(line: MarkdownLine, x: Float, y: Float, size: Float, key: String,
                         button: (String, Rect2f, String, () -> Unit) -> Unit, link: (String) -> Unit, heading: Boolean = false, codeBlock: Boolean = false) {
        line.runs.forEachIndexed { index, run ->
            val style = run.span.style
            val color = when {
                run.span.href != null -> theme.primaryHover
                style and AgentMarkdown.KEYWORD != 0 -> NanoVGColor(0.73f, 0.64f, 0.95f, 1f)
                style and AgentMarkdown.STRING != 0 -> NanoVGColor(0.60f, 0.81f, 0.67f, 1f)
                style and AgentMarkdown.COMMENT != 0 -> theme.textDisabled
                style and AgentMarkdown.NUMBER != 0 -> NanoVGColor(0.89f, 0.74f, 0.53f, 1f)
                style and AgentMarkdown.CODE != 0 -> NanoVGColor(0.77f, 0.84f, 0.93f, 1f)
                style and AgentMarkdown.ITALIC != 0 -> theme.textSecondary
                else -> theme.textPrimary
            }
            if (style and AgentMarkdown.CODE != 0 && !heading && !codeBlock) Draw2D.roundedRect(x + run.x - 1f, y - 1f, run.width + 2f, size + 4f, 3f, theme.surfaceHover.withAlpha(0.08f))
            fun draw() {
                text(run.span.text, x + run.x, y, color, size)
                if (heading || style and AgentMarkdown.BOLD != 0) text(run.span.text, x + run.x + 0.35f, y, color.withAlpha(0.55f), size)
            }
            if (style and AgentMarkdown.CODE != 0) RenderEngine.withNamedFont("Consolas") { draw() } else draw()
            if (style and AgentMarkdown.STRIKE != 0) Draw2D.rect(x + run.x, y + size * 0.55f, run.width, 0.8f, color.withAlpha(0.7f))
            run.span.href?.let { href ->
                Draw2D.rect(x + run.x, y + size + 2f, run.width, 0.7f, color.withAlpha(0.55f))
                // Use the normal control/hit path; this action requires an explicit click.
                registerLink(Rect2f(x + run.x, y - 1f, run.width, size + 5f), "link-" + key + "-" + index) { link(href) }
            }
        }
    }
}
