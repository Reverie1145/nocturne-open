package cn.reverie.util.misc

import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack

object ItemSpoofUtil {

    private var spoofing = false
    private var spoofedSlot = 0
    private var spoofHotbar = true

    @JvmStatic
    fun isSpoofing(): Boolean = spoofing

    @JvmStatic
    fun shouldSpoofHotbar(): Boolean = spoofing && spoofHotbar

    @JvmStatic
    fun getSpoofedSlot(): Int = spoofedSlot

    @JvmStatic
    @JvmOverloads
    fun startSpoof(slot: Int, spoofHotbar: Boolean = true) {
        if (slot in 0..35) {
            spoofing = true
            spoofedSlot = slot
            this.spoofHotbar = spoofHotbar
        }
    }

    @JvmStatic
    fun stopSpoof() {
        spoofing = false
        spoofedSlot = 0
        spoofHotbar = true
    }

    @JvmStatic
    fun getStack(): ItemStack {
        val player = Minecraft.getInstance().player ?: return ItemStack.EMPTY
        if (spoofing && spoofedSlot in 0..35) {
            return player.inventory.getItem(spoofedSlot)
        }
        return player.mainHandItem
    }
}
