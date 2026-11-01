package cn.reverie.module.player

import cn.reverie.mixin.client.MultiPlayerGameModeAccessor
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.module.combat.KillAura
import cn.reverie.module.world.Scaffold
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items

/** Eats ordinary food from the hotbar without taking over a manual item use. */
object AutoEat : Module(
    name = "AutoEat",
    description = "Eat hotbar food automatically when hunger is low",
    category = Category.PLAYER
) {
    val hunger = NumberSetting("Hunger", "Start eating at this food level or below", 14.0, 1.0, 19.0, 1.0)
    val goldenApples = BooleanSetting("Golden Apples", "Allow golden apples as automatic food", false)
    val restoreSlot = BooleanSetting("Restore Slot", "Return to the previously selected slot", true)

    private var previousSlot = -1
    private var foodSlot = -1
    private var useTicks = 0
    private var startedUsing = false
    private val mc: Minecraft get() = Minecraft.getInstance()

    override fun onDisable() = finish(restore = true)

    fun onWorldChange() = clear()

    fun onTick() {
        if (!enabled) return
        val player = mc.player ?: run { clear(); return }
        val gameMode = mc.gameMode ?: run { clear(); return }
        if (mc.level == null || !player.isAlive) { clear(); return }

        if (foodSlot >= 0) {
            useTicks++
            if (player.inventory.selectedSlot != foodSlot || mc.gui.screen() != null) {
                // A manual slot change belongs to the player; never undo it.
                finish(restore = false)
                return
            }
            if (player.isUsingItem) startedUsing = true
            if ((startedUsing && !player.isUsingItem) || useTicks > 50 ||
                player.foodData.foodLevel > hunger.getInt()
            ) {
                finish(restore = true)
            }
            return
        }

        if (mc.gui.screen() != null || player.isSpectator || player.isUsingItem ||
            player.foodData.foodLevel > hunger.getInt() || Scaffold.enabled || KillAura.target != null ||
            AutoTool.toolState != AutoTool.ToolState.IDLE
        ) return

        val slot = (0..8).firstOrNull { index ->
            val stack = player.inventory.getItem(index)
            !stack.isEmpty && stack.has(DataComponents.FOOD) &&
                (goldenApples.value || (!stack.`is`(Items.GOLDEN_APPLE) && !stack.`is`(Items.ENCHANTED_GOLDEN_APPLE)))
        } ?: return

        previousSlot = player.inventory.selectedSlot
        foodSlot = slot
        useTicks = 0
        startedUsing = false
        player.inventory.selectedSlot = slot
        (gameMode as? MultiPlayerGameModeAccessor)?.nocturneEnsureHasSentCarriedItem()
        gameMode.useItem(player, InteractionHand.MAIN_HAND)
        if (!player.isUsingItem) finish(restore = true)
    }

    private fun finish(restore: Boolean) {
        val player = mc.player
        if (restore && restoreSlot.value && player != null && previousSlot in 0..8 &&
            player.inventory.selectedSlot == foodSlot
        ) {
            player.inventory.selectedSlot = previousSlot
            (mc.gameMode as? MultiPlayerGameModeAccessor)?.nocturneEnsureHasSentCarriedItem()
        }
        clear()
    }

    private fun clear() {
        previousSlot = -1
        foodSlot = -1
        useTicks = 0
        startedUsing = false
    }
}
