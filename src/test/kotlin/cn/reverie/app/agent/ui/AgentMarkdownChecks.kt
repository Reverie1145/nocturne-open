package cn.reverie.app.agent.ui

import kotlin.test.*

class AgentMarkdownChecks {
    @Test fun headingsListsQuotesFencedCodeAndTablesRetainStructure() {
        val text = "# 标题\n\n1. **完成** \u0060module\u0060\n- [x] 已检查\n> 引用\n\n\u0060\u0060\u0060javascript\nvar health = 8;\n\u0060\u0060\u0060\n\n| 类型 | 值 |\n| --- | --- |\n| 数值 | 8 |\n"
        val blocks = AgentMarkdown.parse(text)
        assertEquals(listOf("heading", "list", "list", "quote", "code", "table"), blocks.map { it.kind })
        assertEquals("✓", blocks[2].prefix); assertEquals("javascript", blocks[4].prefix)
        assertEquals("var health = 8;", blocks[4].text); assertEquals(2, blocks[5].rows.size)
        assertTrue(blocks[1].spans.any { it.style and AgentMarkdown.BOLD != 0 })
        assertTrue(blocks[1].spans.any { it.style and AgentMarkdown.CODE != 0 })
    }
    @Test fun incompleteStreamingCodeFencesAndEscapesStayReadable() {
        val blocks = AgentMarkdown.parse("## 准备\n\u0060\u0060\u0060js\nconsole.log('还在输出🙂');")
        assertEquals(2, blocks.size); assertEquals("console.log('还在输出🙂');", blocks.last().text)
        assertEquals("*literal*", AgentMarkdown.inline("\\*literal\\*").joinToString("") { it.text })
        assertEquals("some_value", AgentMarkdown.inline("some_value").joinToString("") { it.text })
    }
    @Test fun htmlAndUnsafeLinksNeverBecomeExecutableActions() {
        assertNull(AgentMarkdown.safeLink("javascript:alert(1)")); assertNull(AgentMarkdown.safeLink("file:///C:/secret"))
        assertNull(AgentMarkdown.safeLink("https://user:password@example.test"))
        assertEquals("https://example.test/docs", AgentMarkdown.inline("[文档](https://example.test/docs)").single().href)
        assertNull(AgentMarkdown.inline("[文字](javascript:example)").first().href)
        assertEquals("<script>text</script>", AgentMarkdown.inline("<script>text</script>").single().text)
    }
    @Test fun unicodeWrappingPreservesEveryCodePointAndStylesWithoutOverflow() {
        val spans = listOf(MarkdownSpan("中文🙂", AgentMarkdown.BOLD), MarkdownSpan("abc \u0060", AgentMarkdown.CODE))
        val lines = AgentMarkdown.wrap(spans, 3f) { value, _ -> value.codePointCount(0, value.length).toFloat() }
        assertEquals("中文🙂abc \u0060", lines.flatMap { it.runs }.joinToString("") { it.span.text })
        assertTrue(lines.all { it.width <= 3f })
        assertTrue(lines.flatMap { it.runs }.none { it.span.text.lastOrNull()?.isHighSurrogate() == true || it.span.text.firstOrNull()?.isLowSurrogate() == true })
        assertEquals(AgentMarkdown.BOLD, lines.first().runs.first().span.style)
    }
    @Test fun codeHighlightingNeverAltersSourceOrInterpretsMarkdownMarkers() {
        val text = "var n = 8; // **comment**\nconsole.log(\"中文🙂\");"
        val spans = AgentMarkdown.code(text)
        assertEquals(text, spans.joinToString("") { it.text })
        assertTrue(spans.any { it.style and AgentMarkdown.COMMENT != 0 })
        assertTrue(spans.any { it.style and AgentMarkdown.STRING != 0 })
        assertTrue(spans.any { it.style and AgentMarkdown.NUMBER != 0 })
    }
}
