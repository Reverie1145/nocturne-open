package cn.reverie.gui.clickgui.beddefender
import cn.reverie.gui.clickgui.beddefender.BedDefensePreviewScene
import cn.reverie.module.player.BedDefender
import cn.reverie.module.player.beddefender.BedDefenseLayout
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGPath
import cn.reverie.render.nanovg.NanoVGRenderer
import org.lwjgl.glfw.GLFW
import cn.reverie.gui.clickgui.setting.SettingComponent

/** Shared by Dropdown and Window ClickGUI; the miniature edits the actual persisted module settings. */
class BedDefenderPreviewComponent : SettingComponent(BedDefender.height) {
    private data class Box(val x: Float, val y: Float, val width: Float, val height: Float) {
        fun contains(px: Float, py: Float) = px in x..(x + width) && py in y..(y + height)
    }
    private data class SceneKey(val layers: Int, val cover: Boolean, val material: String, val rgb: Int?, val shoulder: BedDefenseLayout.Cell, val yaw: Float, val pitch: Float, val xray: Boolean, val width: Float, val height: Float)

    private var yaw = DEFAULT_YAW
    private var pitch = DEFAULT_PITCH
    private var xray = true
    private var dragging = false
    private var cachedKey: SceneKey? = null
    private var cachedFrame: BedDefensePreviewScene.Frame? = null

    private val innerWidth get() = (width - 12f).coerceAtLeast(1f)
    private val previewHeight get() = (width * 0.5f + 76f).coerceIn(128f, 172f)
    override var height: Float
        get() = 92f + previewHeight
        set(@Suppress("UNUSED_PARAMETER") value) {}

    private fun layerBox(index: Int): Box {
        val cellWidth = (innerWidth - 8f) / 3f
        return Box(6f + index * (cellWidth + 4f), 19f, cellWidth, 24f)
    }
    private fun viewport() = Box(6f, 49f, innerWidth, previewHeight)
    private fun actionBox(index: Int): Box {
        val cellWidth = (innerWidth - 8f) / 3f
        return Box(6f + index * (cellWidth + 4f), 55f + previewHeight, cellWidth, 18f)
    }

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        val layers = BedDefender.height.getInt().coerceIn(1, 3)
        val accent = HUDModule.sampleColor(0f)
        val count = BedDefenseLayout.cells(layers, BedDefender.topCover.value).size
        NanoVGRenderer.drawString("Pyramid", x + 6f, y + 2f, NanoVGColor(0.88f, 0.90f, 0.95f), 8.4f)
        NanoVGRenderer.drawRightString("$count blocks", x + width - 6f, y + 3f, NanoVGColor(0.57f, 0.62f, 0.71f), 6.5f)

        for (index in 0..2) {
            val box = layerBox(index)
            val selected = layers == index + 1
            val hovered = box.contains(mouseX - x, mouseY - y)
            drawButton(box, if (width >= 210f) "${index + 1} ${if (index == 0) "layer" else "layers"}" else "L${index + 1}", selected, hovered, accent, 8.0f)
        }

        val view = viewport()
        Draw2D.roundedRect(x + view.x, y + view.y, view.width, view.height, 5f, NanoVGColor(0.035f, 0.045f, 0.066f, 0.88f))
        Draw2D.roundedOutline(x + view.x, y + view.y, view.width, view.height, 5f,
            if (focused === this) accent.withAlpha(0.64f) else NanoVGColor(1f, 1f, 1f, 0.09f), 0.8f)
        val key = SceneKey(layers, BedDefender.topCover.value, BedDefender.previewMaterial(), BedDefender.previewRgb(), BedDefender.previewShoulder(), yaw, pitch, xray, view.width, view.height)
        if (key != cachedKey) {
            cachedFrame = BedDefensePreviewScene.frame(layers, key.cover, key.material, yaw, pitch, xray, view.width, view.height, key.rgb, key.shoulder)
            cachedKey = key
        }
        val frame = cachedFrame ?: return
        NanoVGRenderer.withScissor(x + view.x, y + view.y, view.width, view.height) {
            frame.floor.forEach { drawFace(it, view) }
            frame.grid.forEach { line ->
                Draw2D.line(x + view.x + line.from.x, y + view.y + line.from.y, x + view.x + line.to.x, y + view.y + line.to.y,
                    NanoVGColor(0.64f, 0.72f, 0.83f, 0.13f), 0.55f)
            }
            frame.faces.forEach { drawFace(it, view) }
        }

        val labels = listOf(if (width >= 210f) "Top cover" else "Top", "X-ray", "Reset")
        for (index in 0..2) {
            val box = actionBox(index)
            val active = when (index) { 0 -> BedDefender.topCover.value; 1 -> xray; else -> false }
            drawButton(box, labels[index], active, box.contains(mouseX - x, mouseY - y), accent, 6.8f)
        }
        val hintY = y + height - 11f
        NanoVGRenderer.drawString(if (dragging) "Rotating..." else "Drag to rotate", x + 6f, hintY, NanoVGColor(0.57f, 0.62f, 0.70f), 6.6f)
        if (width >= 180f) {
            val label = BedDefender.materialName()
            val trimmed = RenderEngine.fontRenderer.trimWithEllipsis(label, width - 80f, 6.6f)
            NanoVGRenderer.drawRightString(trimmed, x + width - 6f, hintY, NanoVGColor(0.72f, 0.76f, 0.83f), 6.6f)
        }
    }

    private fun drawFace(face: BedDefensePreviewScene.Face, view: Box) {
        val path = NanoVGPath(RenderEngine.nanoVG.handle).begin()
        face.points.forEachIndexed { index, point ->
            if (index == 0) path.moveTo(x + view.x + point.x, y + view.y + point.y)
            else path.lineTo(x + view.x + point.x, y + view.y + point.y)
        }
        path.close().fill(face.fill.toNanoVG()).stroke(face.outline.toNanoVG(), 0.65f)
    }

    private fun BedDefensePreviewScene.Color.toNanoVG() = NanoVGColor(r, g, b, a)

    private fun drawButton(box: Box, label: String, selected: Boolean, hovered: Boolean, accent: NanoVGColor, fontSize: Float) {
        val background = when {
            selected -> accent.withAlpha(if (hovered) 0.28f else 0.20f)
            hovered -> NanoVGColor(1f, 1f, 1f, 0.10f)
            else -> NanoVGColor(0f, 0f, 0f, 0.19f)
        }
        Draw2D.roundedRect(x + box.x, y + box.y, box.width, box.height, 3.5f, background)
        Draw2D.roundedOutline(x + box.x, y + box.y, box.width, box.height, 3.5f,
            if (selected) accent.withAlpha(0.68f) else NanoVGColor(1f, 1f, 1f, if (hovered) 0.14f else 0.08f), 0.7f)
        NanoVGRenderer.drawCenteredString(label, x + box.x + box.width * 0.5f, y + box.y + box.height * 0.5f,
            if (selected) NanoVGColor.WHITE else NanoVGColor(0.73f, 0.77f, 0.85f), fontSize)
        if (selected) {
            Draw2D.roundedRect(x + box.x + box.width * 0.5f - 3f, y + box.y + box.height - 3f, 6f, 1f, 0.5f, accent)
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isMouseOver(mouseX, mouseY) || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        focused = this
        val mx = mouseX - x
        val my = mouseY - y
        for (index in 0..2) {
            if (layerBox(index).contains(mx, my)) {
                BedDefender.height.setClamped((index + 1).toDouble())
                return true
            }
            if (actionBox(index).contains(mx, my)) {
                when (index) {
                    0 -> BedDefender.topCover.toggle()
                    1 -> xray = !xray
                    2 -> resetView()
                }
                return true
            }
        }
        if (viewport().contains(mx, my)) {
            dragging = true
            return true
        }
        return true
    }

    override fun mouseDragged(mouseX: Float, mouseY: Float, button: Int, dragX: Float, dragY: Float): Boolean {
        if (!dragging || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        yaw = (yaw + dragX * 0.8f) % 360f
        pitch = (pitch + dragY * 0.55f).coerceIn(18f, 76f)
        return true
    }

    override fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!dragging || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false
        dragging = false
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (focused !== this) return false
        when (keyCode) {
            GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3 -> BedDefender.height.setClamped((keyCode - GLFW.GLFW_KEY_1 + 1).toDouble())
            GLFW.GLFW_KEY_LEFT -> BedDefender.height.setClamped(BedDefender.height.value - 1)
            GLFW.GLFW_KEY_RIGHT -> BedDefender.height.setClamped(BedDefender.height.value + 1)
            GLFW.GLFW_KEY_UP -> pitch = (pitch + 5f).coerceAtMost(76f)
            GLFW.GLFW_KEY_DOWN -> pitch = (pitch - 5f).coerceAtLeast(18f)
            GLFW.GLFW_KEY_C -> BedDefender.topCover.toggle()
            GLFW.GLFW_KEY_X -> xray = !xray
            GLFW.GLFW_KEY_R -> resetView()
            GLFW.GLFW_KEY_ESCAPE -> clearFocus()
            else -> return false
        }
        return true
    }

    private fun resetView() {
        yaw = DEFAULT_YAW
        pitch = DEFAULT_PITCH
        dragging = false
    }

    companion object {
        private const val DEFAULT_YAW = -38f
        private const val DEFAULT_PITCH = 38f
        private var focused: BedDefenderPreviewComponent? = null

        fun hasFocusedPreview() = focused != null

        fun clearFocus() {
            focused?.dragging = false
            focused = null
        }
    }
}
