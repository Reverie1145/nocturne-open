package cn.reverie.render.ui

import cn.reverie.render.layout.Padding
import cn.reverie.render.math.Rect2f
import cn.reverie.render.math.Vec2f

abstract class UIElement {

    var x: Float = 0f
    var y: Float = 0f
    var width: Float = 100f
    var height: Float = 30f

    var padding: Padding = Padding.ZERO
    var margin: Padding = Padding.ZERO

    var isVisible: Boolean = true
    var isEnabled: Boolean = true
    var isHovered: Boolean = false
    var isFocused: Boolean = false
    var isPressed: Boolean = false

    var parent: UIContainer? = null

    val bounds: Rect2f get() = Rect2f(x, y, width, height)
    val absoluteX: Float get() = (parent?.absoluteX ?: 0f) + x
    val absoluteY: Float get() = (parent?.absoluteY ?: 0f) + y
    val absoluteBounds: Rect2f get() = Rect2f(absoluteX, absoluteY, width, height)

    var onClick: (() -> Unit)? = null

    open fun onTick() {}

    abstract fun render(mouseX: Float, mouseY: Float, partialTicks: Float)

    open fun isMouseOver(mouseX: Float, mouseY: Float): Boolean {
        return absoluteBounds.contains(mouseX, mouseY)
    }

    open fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false
        if (isMouseOver(mouseX, mouseY)) {
            isPressed = true
            isFocused = true
            onClick?.invoke()
            return true
        }
        isFocused = false
        return false
    }

    open fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean {
        val wasPressed = isPressed
        isPressed = false
        return wasPressed
    }

    open fun mouseDragged(mouseX: Float, mouseY: Float, button: Int, dragX: Float, dragY: Float): Boolean = false
    open fun mouseScrolled(mouseX: Float, mouseY: Float, deltaX: Float, deltaY: Float): Boolean = false

    open fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean = false
    open fun charTyped(codePoint: Char, modifiers: Int): Boolean = false
}
