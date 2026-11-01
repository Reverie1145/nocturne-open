package cn.reverie.app.agent.ui

import cn.reverie.render.math.Rect2f
import kotlin.test.*

class AgentUiLayoutChecks {
    private fun disjoint(rectangles: List<Rect2f>) {
        rectangles.forEachIndexed { index, rectangle ->
            assertFalse(rectangle.isEmpty, "Empty control: " + rectangle)
            rectangles.drop(index + 1).forEach { next -> assertFalse(rectangle.intersects(next), "Overlapping controls: " + rectangle + " / " + next) }
        }
    }
    @Test fun workspacesFitSmallPortraitUltrawideAndNormalCanvasesWithoutOverlappingControls() {
        val sizes = listOf(320f to 240f, 480f to 800f, 800f to 600f, 960f to 540f, 1280f to 720f, 1920f to 1080f, 3440f to 1440f)
        for ((width, height) in sizes) {
            val layout = AgentWorkspaceLayout(width, height)
            val frame = layout.frame(1f)
            assertTrue(frame.x >= 0f && frame.y >= 0f)
            assertTrue(frame.x + layout.width * frame.scale <= width)
            assertTrue(frame.y + layout.height * frame.scale <= height)
            val header = listOf(layout.brand) + layout.tabs + listOf(layout.status, layout.help, layout.close)
            disjoint(header)
            disjoint(listOf(layout.scriptToolbar, layout.scriptList, layout.scriptTitle, layout.scriptDescription,
                layout.autoStart, layout.editor, layout.logHeader, layout.console) + layout.scriptActions)
            disjoint(listOf(layout.sessionTitle, layout.history, layout.newSession, layout.taskHeading, layout.taskList,
                layout.taskStats, layout.transcript, layout.prompt, layout.send, layout.copy, layout.attachment, layout.composerHint, layout.usage))
            disjoint(listOf(layout.settingsTitle, layout.settingsSave, layout.settingsHint, layout.settingsViewport))
            (header + listOf(layout.content, layout.footer)).forEach { assertTrue(layout.bounds.contains(it)) }
            listOf(layout.scriptToolbar, layout.scriptList, layout.editor, layout.console, layout.transcript,
                layout.prompt, layout.attachment, layout.usage, layout.settingsViewport).forEach { assertTrue(layout.content.contains(it)) }
        }
    }
    @Test fun everyDialogKeepsTextAndButtonsInsideTheSurfaceAtAllSizes() {
        val dialogs = listOf(520f to 264f, 520f to 236f, 620f to 430f, 820f to 540f, 640f to 500f, 580f to 268f, 620f to 280f, 650f to 440f)
        for ((canvasWidth, canvasHeight) in listOf(320f to 240f, 800f to 600f, 1140f to 720f)) {
            val layout = AgentWorkspaceLayout(canvasWidth, canvasHeight)
            for ((width, height) in dialogs) {
                val dialog = layout.dialog(width, height)
                assertTrue(layout.bounds.contains(dialog.bounds))
                val regions = listOf(dialog.title, dialog.body, dialog.actions, dialog.error)
                regions.forEach { assertTrue(dialog.bounds.contains(it)) }
                disjoint(regions)
                disjoint(AgentWorkspaceLayout.row(dialog.actions, 3, 12f))
            }
        }
    }
    @Test fun animatedDrawingAndInputCoordinatesRoundTripAtEveryScale() {
        for ((width, height) in listOf(320f to 240f, 1280f to 720f, 3440f to 1440f)) {
            val layout = AgentWorkspaceLayout(width, height)
            for (step in 0..100) {
                val frame = layout.frame(step / 100f)
                val rect = layout.tabs[1]
                val screen = frame.point(rect.centerX, rect.centerY)
                val logical = frame.pointer(screen.first, screen.second)
                assertEquals(rect.centerX, logical.first, 0.001f); assertEquals(rect.centerY, logical.second, 0.001f)
                assertTrue(rect.contains(logical.first, logical.second))
            }
        }
    }
    @Test fun scrollsRespectContentChangesAndConvergeIndependentlyOfFrameRate() {
        val sixty = AgentScrollState().apply { extent(1000f, 300f); scroll(500f) }
        val thirty = AgentScrollState().apply { extent(1000f, 300f); scroll(500f) }
        repeat(12) { sixty.advance(1f / 60f) }; repeat(6) { thirty.advance(1f / 30f) }
        assertEquals(sixty.value, thirty.value, 0.001f)
        assertTrue(sixty.value in 0f..500f)
        sixty.scroll(100000f); assertEquals(700f, sixty.target)
        sixty.extent(100f, 300f); assertEquals(0f, sixty.value); assertEquals(0f, sixty.target)
        assertFalse(sixty.scroll(-0.25f))
        thirty.scroll(-0.25f); assertEquals(499.75f, thirty.target)
    }
    @Test fun clippingCannotLeaveAnInteractiveRegionBehindTheViewport() {
        val viewport = Rect2f(10f, 100f, 300f, 200f)
        val partial = Rect2f(20f, 80f, 90f, 40f).intersect(viewport)
        assertFalse(partial.contains(30f, 90f)); assertTrue(partial.contains(30f, 110f))
        assertTrue(Rect2f(20f, 20f, 80f, 30f).intersect(viewport).isEmpty)
        val field = AgentTextArea("test", multiline = false).apply { x = 20f; y = 80f; width = 90f; height = 40f; inputClip = partial }
        assertFalse(field.isMouseOver(30f, 90f)); assertTrue(field.isMouseOver(30f, 110f))
        assertFalse(field.mouseScrolled(30f, 110f, 0f, -0.5f))
    }
    @Test fun ellipsisAndBufferLimitsPreserveWholeUnicodeCharacters() {
        val measure: (String) -> Float = { it.codePointCount(0, it.length).toFloat() }
        assertEquals("a😀…", AgentTextLayout.fit("a😀中文xyz", 3f, measure))
        assertEquals("", AgentTextLayout.fit("中文", 0.5f, measure))
        assertEquals("", AgentTextLayout.fit("中文", -20f, measure))
        assertEquals("a", AgentTextLayout.prefix("a😀b", 2))
        val buffer = EditorBuffer("a😀b", 2)
        assertEquals("a", buffer.text)
        buffer.load("b😀c"); assertEquals("b", buffer.text)
    }
    @Test fun growingLogsPreserveAnActiveSelectionAndReadOnlyWheelHandlesFractionalDeltas() {
        val area = AgentTextArea("one\ntwo", readOnly = true).apply { width = 300f; height = 40f; inputClip = bounds; isFocused = true }
        area.buffer.move(0); area.buffer.move(3, true)
        area.setText("one\ntwo\nthree", follow = true)
        assertEquals("one", area.buffer.selectedText)
        assertTrue(area.mouseScrolled(20f, 20f, 0f, -0.25f))
        area.blur(); assertFalse(area.isFocused)
    }
}
