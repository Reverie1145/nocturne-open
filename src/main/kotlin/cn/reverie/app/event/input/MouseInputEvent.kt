package cn.reverie.app.event.input

import cn.reverie.app.event.Cancellable
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent

class MouseInputEvent(
    val event: MouseButtonEvent,
    val action: Int,
    val screen: Screen?
) : Cancellable {
    override var isCancelled: Boolean = false
}
