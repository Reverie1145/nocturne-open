package cn.reverie.gui.clickgui.arcade

import cn.reverie.render.nanovg.NanoVGColor

/**
 * Base interface for Nocturne Arcade mini-games.
 */
interface ArcadeGame {
    val id: String
    val name: String
    val tabLabel: String
    val tabIcon: String

    fun init()
    fun reset()
    fun update()
    fun render(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        mouseX: Float,
        mouseY: Float,
        alpha: Float,
        theme: NanoVGColor
    )
    fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean
    fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean = false
    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean
}
