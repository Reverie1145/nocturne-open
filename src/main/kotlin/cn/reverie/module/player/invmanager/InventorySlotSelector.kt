package cn.reverie.module.player.invmanager

import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.item.*
/** Shared deterministic slot selection for the inventory manager's item categories. */
internal object InventorySlotSelector {
    fun findMost(
        player: LocalPlayer,
        selectedSlot: Int,
        configuredSlots: List<String>,
        category: String,
        matches: (ItemStack) -> Boolean
    ): Int {
        var bestSlot = -1
        var bestCount = 0
        for (slot in 0..35) {
            if (slot in 0..8 && slot != selectedSlot && configuredSlots.getOrNull(slot) == category) continue
            val stack = player.inventory.getItem(slot)
            if (stack.isEmpty || !matches(stack)) continue
            if (stack.count > bestCount) {
                bestCount = stack.count
                bestSlot = slot
            }
        }
        return bestSlot
    }
}
