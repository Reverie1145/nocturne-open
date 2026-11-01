package cn.reverie.app.agent.ui

import java.net.URI

internal data class MarkdownSpan(val text: String, val style: Int = 0, val href: String? = null)
internal data class MarkdownBlock(val kind: String, val spans: List<MarkdownSpan> = emptyList(),
    val text: String = "", val level: Int = 0, val prefix: String = "", val rows: List<List<String>> = emptyList())
internal data class MarkdownRun(val span: MarkdownSpan, val x: Float, val width: Float)
internal data class MarkdownLine(val runs: List<MarkdownRun>, val width: Float)

/** Bounded native Markdown; HTML stays text and only explicit HTTP(S) links become actions. */
internal object AgentMarkdown {
    const val BOLD = 1; const val ITALIC = 2; const val CODE = 4; const val STRIKE = 8
    const val KEYWORD = 16; const val STRING = 32; const val COMMENT = 64; const val NUMBER = 128
    private val codeTokens = Regex("""//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\b(?:function|var|let|const|if|else|return|for|while|new|true|false|null|undefined|class|import|export|async|await|throw|try|catch)\b|\b\d+(?:\.\d+)?\b""")
    fun code(text: String): List<MarkdownSpan> {
        val spans = mutableListOf<MarkdownSpan>(); var end = 0
        codeTokens.findAll(text).forEach { match ->
            if (match.range.first > end) spans += MarkdownSpan(text.substring(end, match.range.first), CODE)
            val value = match.value
            val color = when { value.startsWith("//") || value.startsWith("/*") -> COMMENT
                value.startsWith('"') || value.startsWith('\'') -> STRING
                value.first().isDigit() -> NUMBER
                else -> KEYWORD }
            spans += MarkdownSpan(value, CODE or color); end = match.range.last + 1
        }
        if (end < text.length) spans += MarkdownSpan(text.substring(end), CODE)
        return spans
    }
    fun safeLink(value: String): String? = runCatching { URI(value).takeIf {
        it.scheme?.lowercase() in setOf("https", "http") && it.host != null && it.userInfo == null
    }?.toASCIIString() }.getOrNull()
    fun parse(source: String): List<MarkdownBlock> {
        val lines = AgentTextLayout.prefix(source, 100000).replace("\r\n", "\n").lines()
        val blocks = mutableListOf<MarkdownBlock>()
        var index = 0
        while (index < lines.size && blocks.size < 2048) {
            val line = lines[index]
            val trimmed = line.trimStart()
            if (line.isBlank()) { index++; continue }
            val fence = Regex("^(\u0060{3,}|~{3,})(.*)$").find(trimmed)
            if (fence != null) {
                val marker = fence.groupValues[1]; val content = mutableListOf<String>(); index++
                while (index < lines.size && !lines[index].trimStart().startsWith(marker)) content += lines[index++]
                if (index < lines.size) index++
                blocks += MarkdownBlock("code", text = content.joinToString("\n"), prefix = fence.groupValues[2].trim().take(32))
                continue
            }
            if (index + 1 < lines.size && line.contains('|') && tableSeparator(lines[index + 1])) {
                val rows = mutableListOf(cells(line)); index += 2
                while (index < lines.size && lines[index].contains('|') && lines[index].isNotBlank()) rows += cells(lines[index++])
                blocks += MarkdownBlock("table", rows = rows.take(100)); continue
            }
            val heading = Regex("^(#{1,6})\\s+(.+)$").find(trimmed)
            val list = Regex("^(\\s*)([-+*]|\\d+[.)])\\s+(.+)$").find(line)
            when {
                heading != null -> blocks += MarkdownBlock("heading", inline(heading.groupValues[2].trimEnd('#', ' ')), level = heading.groupValues[1].length)
                Regex("^([-*_])(?:\\s*\\1){2,}\\s*$").matches(trimmed) -> blocks += MarkdownBlock("rule")
                trimmed.startsWith(">") -> blocks += MarkdownBlock("quote", inline(trimmed.removePrefix(">").trimStart()))
                list != null -> {
                    val text = list.groupValues[3]
                    val task = Regex("^\\[([ xX])]\\s+(.+)$").find(text)
                    blocks += MarkdownBlock("list", inline(task?.groupValues?.get(2) ?: text),
                        level = (list.groupValues[1].length / 2).coerceAtMost(8),
                        prefix = task?.let { if (it.groupValues[1].isBlank()) "□" else "✓" }
                            ?: if (list.groupValues[2].length == 1) "•" else list.groupValues[2])
                }
                else -> blocks += MarkdownBlock("paragraph", inline(line))
            }
            index++
        }
        return blocks
    }
    private fun tableSeparator(line: String): Boolean = cells(line).let { it.isNotEmpty() && it.all { cell -> Regex(":?-{3,}:?").matches(cell.trim()) } }
    private fun cells(line: String) = line.trim().trim('|').split(Regex("(?<!\\\\)\\|")).map { it.trim().replace("\\|", "|") }.take(8)

    fun inline(text: String, style: Int = 0, depth: Int = 0): List<MarkdownSpan> {
        if (depth > 6) return listOf(MarkdownSpan(text, style))
        val result = mutableListOf<MarkdownSpan>()
        val plain = StringBuilder()
        fun flush() { if (plain.isNotEmpty()) { result += MarkdownSpan(plain.toString(), style); plain.setLength(0) } }
        var at = 0
        while (at < text.length) {
            if (text[at] == '\\' && at + 1 < text.length) { plain.append(text[at + 1]); at += 2; continue }
            if (text[at] == '[') {
                val close = text.indexOf("](", at + 1); val end = if (close > at) text.indexOf(')', close + 2) else -1
                if (end >= 0) {
                    flush()
                    val url = safeLink(text.substring(close + 2, end).trim())
                    result += inline(text.substring(at + 1, close), style, depth + 1).map { it.copy(href = url) }
                    at = end + 1; continue
                }
            }
            val marker = when {
                text.startsWith("\u0060", at) -> "\u0060"
                text.startsWith("**", at) -> "**"
                text.startsWith("__", at) -> "__"
                text.startsWith("~~", at) -> "~~"
                text[at] == '*' -> "*"
                text[at] == '_' && (at == 0 || !text[at - 1].isLetterOrDigit()) -> "_"
                else -> ""
            }
            val end = if (marker.isEmpty()) -1 else text.indexOf(marker, at + marker.length)
            if (end > at + marker.length) {
                flush()
                val flag = when (marker) { "\u0060" -> CODE; "**", "__" -> BOLD; "~~" -> STRIKE; else -> ITALIC }
                val value = text.substring(at + marker.length, end)
                if (flag == CODE) result += MarkdownSpan(value, style or CODE)
                else result += inline(value, style or flag, depth + 1)
                at = end + marker.length
            } else { plain.append(text[at]); at++ }
        }
        flush()
        return result
    }

    /** Unicode-safe wrapping. Runs retain code/emphasis/link styles across line breaks. */
    fun wrap(spans: List<MarkdownSpan>, width: Float, measure: (String, Int) -> Float): List<MarkdownLine> {
        if (width <= 0f) return emptyList()
        val lines = mutableListOf<MarkdownLine>(); val runs = mutableListOf<MarkdownRun>(); var x = 0f
        val metrics = mutableMapOf<Pair<Int, Int>, Float>()
        fun newline() { lines += MarkdownLine(runs.toList(), x); runs.clear(); x = 0f }
        for (span in spans) {
            var segment = StringBuilder(); var startX = x; var segmentWidth = 0f
            fun flush() {
                if (segment.isNotEmpty()) {
                    runs += MarkdownRun(span.copy(text = segment.toString()), startX, segmentWidth)
                    segment = StringBuilder(); segmentWidth = 0f
                }
                startX = x
            }
            span.text.codePoints().forEach { code ->
                if (code == 10) { flush(); newline(); startX = 0f }
                else {
                    val glyph = String(Character.toChars(code))
                    val size = metrics.getOrPut(code to span.style) { measure(glyph, span.style).coerceAtLeast(0f) }
                    if (x > 0 && x + size > width) { flush(); newline(); startX = 0f }
                    segment.append(glyph); segmentWidth += size; x += size
                }
            }
            flush()
        }
        if (runs.isNotEmpty() || lines.isEmpty()) newline()
        return lines
    }
}
