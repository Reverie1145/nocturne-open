package cn.reverie.render.ui

import cn.reverie.render.layout.Layout

open class UIContainer : UIElement() {

    val children = ArrayList<UIElement>()
    var layout: Layout? = null

    fun add(child: UIElement): UIContainer {
        child.parent = this
        children.add(child)
        relayout()
        return this
    }

    fun remove(child: UIElement): Boolean {
        val removed = children.remove(child)
        if (removed) {
            child.parent = null
            relayout()
        }
        return removed
    }

    fun clear() {
        children.forEach { it.parent = null }
        children.clear()
    }

    fun relayout() {
        layout?.layout(this, children)
    }

    override fun onTick() {
        super.onTick()
        children.forEach { it.onTick() }
    }

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return
        relayout()
        for (child in children) {
            if (child.isVisible) {
                child.isHovered = child.isMouseOver(mouseX, mouseY)
                child.render(mouseX, mouseY, partialTicks)
            }
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false
        for (child in children.reversed()) {
            if (child.mouseClicked(mouseX, mouseY, button)) {
                return true
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean {
        var handled = false
        for (child in children.reversed()) {
            if (child.mouseReleased(mouseX, mouseY, button)) {
                handled = true
            }
        }
        return handled || super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Float, mouseY: Float, button: Int, dragX: Float, dragY: Float): Boolean {
        for (child in children.reversed()) {
            if (child.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true
        }
        return false
    }

    override fun mouseScrolled(mouseX: Float, mouseY: Float, deltaX: Float, deltaY: Float): Boolean {
        for (child in children.reversed()) {
            if (child.mouseScrolled(mouseX, mouseY, deltaX, deltaY)) return true
        }
        return false
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        for (child in children.reversed()) {
            if (child.keyPressed(keyCode, scanCode, modifiers)) return true
        }
        return false
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        for (child in children.reversed()) {
            if (child.charTyped(codePoint, modifiers)) return true
        }
        return false
    }
}
