package cn.reverie.app.agent.ui

import cn.reverie.render.math.Rect2f
import kotlin.math.exp
import kotlin.math.abs

/** Logical canvas remains usable in a small window; one invertible frame serves drawing and input. */
internal data class AgentViewFrame(val x: Float, val y: Float, val scale: Float) {
    fun pointer(x: Float, y: Float) = Pair((x - this.x) / scale, (y - this.y) / scale)
    fun point(x: Float, y: Float) = Pair(this.x + x * scale, this.y + y * scale)
}

internal class AgentWorkspaceLayout(canvasWidth: Float, canvasHeight: Float) {
    private val availableWidth = (canvasWidth - 32f).coerceAtLeast(1f)
    private val availableHeight = (canvasHeight - 32f).coerceAtLeast(1f)
    val width = availableWidth.coerceIn(920f, 1140f)
    val height = availableHeight.coerceIn(620f, 720f)
    private val fitScale = minOf(1f, availableWidth / width, availableHeight / height)
    private val centerX = canvasWidth * 0.5f
    private val centerY = canvasHeight * 0.5f
    val bounds = Rect2f(0f, 0f, width, height)
    val content = Rect2f(20f, 86f, width - 40f, height - 122f)
    val footer = Rect2f(22f, height - 27f, width - 44f, 18f)
    val brand = Rect2f(20f, 20f, 158f, 42f)
    val tabs = listOf(Rect2f(204f, 27f, 78f, 30f), Rect2f(290f, 27f, 78f, 30f), Rect2f(376f, 27f, 100f, 30f))
    val status = Rect2f(width - 280f, 30f, 98f, 24f)
    val help = Rect2f(width - 166f, 27f, 100f, 30f)
    val close = Rect2f(width - 54f, 27f, 30f, 30f)
    val scriptToolbar = Rect2f(content.x, content.y, 194f, 30f)
    val scriptList = Rect2f(content.x, content.y + 44f, 194f, content.height - 44f)
    val scriptTitle = Rect2f(content.x + 212f, content.y, content.width - 212f, 24f)
    val scriptActions = row(Rect2f(scriptTitle.x, content.y + 34f, scriptTitle.width, 30f), 7, 8f)
    val scriptDescription = Rect2f(scriptTitle.x, content.y + 77f, scriptTitle.width - 148f, 20f)
    val autoStart = Rect2f(scriptTitle.x2 - 136f, content.y + 71f, 136f, 30f)
    private val logHeight = (content.height * 0.22f).coerceIn(104f, 138f)
    val console = Rect2f(scriptTitle.x, content.y2 - logHeight, scriptTitle.width, logHeight)
    val logHeader = Rect2f(scriptTitle.x, console.y - 30f, scriptTitle.width, 22f)
    val editor = Rect2f(scriptTitle.x, content.y + 110f, scriptTitle.width, logHeader.y - content.y - 120f)
    val sessionTitle = Rect2f(content.x, content.y, content.width - 208f, 26f)
    val history = Rect2f(content.x2 - 186f, content.y, 74f, 30f)
    val newSession = Rect2f(content.x2 - 104f, content.y, 104f, 30f)
    val taskHeading = Rect2f(content.x + 4f, content.y + 46f, 180f, 20f)
    val taskStats = Rect2f(content.x + 4f, content.y2 - 54f, 180f, 48f)
    val taskList = Rect2f(content.x, content.y + 76f, 188f, taskStats.y - content.y - 88f)
    private val mainX = content.x + 204f
    private val mainWidth = content.width - 204f
    val transcript = Rect2f(mainX, content.y + 42f, mainWidth, content.height - 184f)
    val prompt = Rect2f(mainX, transcript.y2 + 12f, mainWidth - 106f, 80f)
    val send = Rect2f(content.x2 - 96f, prompt.y, 96f, 34f)
    val copy = Rect2f(send.x, prompt.y + 46f, 96f, 34f)
    val attachment = Rect2f(mainX, prompt.y2 + 10f, 144f, 30f)
    val composerHint = Rect2f(mainX + 156f, prompt.y2 + 17f, mainWidth - 282f, 18f)
    val usage = Rect2f(content.x2 - 116f, prompt.y2 + 10f, 116f, 30f)
    val settingsTitle = Rect2f(content.x + 8f, content.y, content.width - 160f, 26f)
    val settingsSave = Rect2f(content.x2 - 128f, content.y, 120f, 30f)
    val settingsHint = Rect2f(content.x + 8f, content.y + 36f, content.width - 16f, 18f)
    val settingsViewport = Rect2f(content.x + 8f, content.y + 66f, content.width - 16f, content.height - 66f)

    fun frame(progress: Float): AgentViewFrame {
        val p = progress.coerceIn(0f, 1f)
        val scale = fitScale * (0.97f + 0.03f * p)
        return AgentViewFrame(centerX - width * scale * 0.5f,
            centerY - height * scale * 0.5f + (1f - p) * 10f * fitScale, scale)
    }

    fun dialog(preferredWidth: Float, preferredHeight: Float) = AgentDialogLayout(
        Rect2f((width - minOf(preferredWidth, width - 56f)) * 0.5f,
            (height - minOf(preferredHeight, height - 64f)) * 0.5f,
            minOf(preferredWidth, width - 56f), minOf(preferredHeight, height - 64f)))

    companion object {
        fun row(bounds: Rect2f, count: Int, gap: Float = 8f): List<Rect2f> {
            require(count > 0)
            val width = ((bounds.width - gap * (count - 1)) / count).coerceAtLeast(0f)
            return List(count) { Rect2f(bounds.x + it * (width + gap), bounds.y, width, bounds.height) }
        }
    }
}

internal class AgentDialogLayout(val bounds: Rect2f) {
    val title = Rect2f(bounds.x + 24f, bounds.y + 20f, bounds.width - 48f, 26f)
    val body = Rect2f(bounds.x + 24f, bounds.y + 62f, bounds.width - 48f, bounds.height - 144f)
    val actions = Rect2f(bounds.x + 24f, bounds.y2 - 68f, bounds.width - 48f, 32f)
    val error = Rect2f(bounds.x + 24f, bounds.y2 - 25f, bounds.width - 48f, 17f)
}

/** Time-based easing keeps wheel, trackpad and animation behavior independent of frame rate. */
internal class AgentScrollState {
    var value = 0f; private set
    var target = 0f; private set
    var maximum = 0f; private set
    fun extent(content: Float, viewport: Float) {
        maximum = (content - viewport).coerceAtLeast(0f)
        value = value.coerceIn(0f, maximum); target = target.coerceIn(0f, maximum)
    }
    fun jump(position: Float) { target = position.coerceIn(0f, maximum); value = target }
    fun scroll(delta: Float): Boolean {
        val next = (target + delta).coerceIn(0f, maximum)
        val changed = next != target; target = next; return changed
    }
    fun advance(seconds: Float) {
        value += (target - value) * (1f - exp(-20f * seconds.coerceIn(0f, 0.1f)))
        if (abs(target - value) < 0.05f) value = target
    }
}

internal object AgentTextLayout {
    fun prefix(text: String, length: Int): String {
        var end = length.coerceIn(0, text.length)
        if (end in 1 until text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
        return text.substring(0, end)
    }
    fun fit(text: String, width: Float, measure: (String) -> Float): String {
        if (width <= 0f) return ""
        if (measure(text) <= width) return text
        if (measure("…") > width) return ""
        var low = 0; var high = text.codePointCount(0, text.length)
        while (low < high) {
            val mid = (low + high + 1) / 2
            val end = text.offsetByCodePoints(0, mid)
            if (measure(text.substring(0, end) + "…") <= width) low = mid else high = mid - 1
        }
        return text.substring(0, text.offsetByCodePoints(0, low)) + "…"
    }
}
