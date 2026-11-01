package cn.reverie.render.ui

import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent

interface NvgScreen {

    fun renderNvg(mouseX: Float, mouseY: Float, partialTicks: Float)

    fun onMouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean = false

    fun onMouseReleased(event: MouseButtonEvent): Boolean = false

    fun onMouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean = false

    fun onMouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean = false

    fun onKeyPressed(event: KeyEvent): Boolean = false

    fun onCharTyped(event: CharacterEvent): Boolean = false
}
