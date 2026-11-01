package cn.reverie.module.movement

import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.Module
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.InventoryScreen

/** Krs Inventory Move: poll movement keys while an allowed screen is open. */
object InventoryMove : Module(
    name = "Inventory Move",
    description = "Move while inventory or other screens are open",
    category = Category.MOVEMENT
) {
    val inventoryOnly = BooleanSetting("Inventory Only", "Only move in the player inventory", false)

    @JvmStatic
    fun canMoveFreely(screen: Screen? = Minecraft.getInstance().gui.screen()): Boolean =
        screen == null || (enabled && (!inventoryOnly.value || screen is InventoryScreen) && screen !is ChatScreen)

    @JvmStatic
    fun moveFreely() {
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.gui.screen() == null || !canMoveFreely()) return
        KeyMapping.setAll()
        mc.options.keyShift.isDown = false
    }
}
