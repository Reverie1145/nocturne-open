package cn.reverie.module.player

import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.util.misc.ChatUtil
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

object DurabilityAlert : Module(
    name = "DurabilityAlert",
    description = "Warn once when equipped gear is close to breaking",
    category = Category.PLAYER
) {
    val remaining = NumberSetting("Remaining", "Warn at this many durability points", 20.0, 1.0, 100.0, 1.0)
    val armor = BooleanSetting("Armor", "Check equipped armor", true)
    val hands = BooleanSetting("Hands", "Check held items", true)

    private val warned = mutableMapOf<String, Item>()
    private var ticks = 0

    override fun onEnable() = onWorldChange()
    override fun onDisable() = onWorldChange()

    fun onWorldChange() {
        warned.clear()
        ticks = 0
    }

    fun onTick() {
        if (!enabled) return
        val player = Minecraft.getInstance().player ?: run { onWorldChange(); return }
        if (++ticks < 20) return
        ticks = 0

        if (hands.value) {
            check("Main hand", player.mainHandItem)
            check("Off hand", player.offhandItem)
        } else {
            warned.remove("Main hand")
            warned.remove("Off hand")
        }
        if (armor.value) {
            for (slot in listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                check(slot.name, player.getItemBySlot(slot))
            }
        } else {
            listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
                .forEach { warned.remove(it.name) }
        }
    }

    private fun check(slot: String, stack: ItemStack) {
        if (stack.isEmpty || !stack.isDamageableItem) {
            warned.remove(slot)
            return
        }
        val left = stack.maxDamage - stack.damageValue
        if (left > remaining.getInt()) {
            warned.remove(slot)
            return
        }
        if (warned[slot] != stack.item) {
            warned[slot] = stack.item
            ChatUtil.sendWarning("${stack.hoverName.string} ($slot): $left durability remaining")
        }
    }
}
