package cn.reverie.module.player

import cn.reverie.mixin.client.FishingHookAccessor
import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.module.combat.ProjectileAura
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items

/** Reels only after the bobber's synced bite flag is set. */
object AutoFish : Module(
    name = "AutoFish",
    description = "Cast and reel a held fishing rod when fish bite",
    category = Category.PLAYER
) {
    val recastDelay = NumberSetting("Recast Delay", "Ticks between reeling and casting again", 20.0, 10.0, 100.0, 5.0)

    private var castCountdown = 10
    private var waitingForHookRemoval = false

    override fun onEnable() = reset()
    override fun onDisable() = reset()
    fun onWorldChange() = reset()

    fun onTick() {
        if (!enabled) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: run { reset(); return }
        val gameMode = mc.gameMode ?: run { reset(); return }
        if (mc.level == null || !player.isAlive || player.isSpectator ||
            mc.gui.screen() != null || player.isUsingItem ||
            (ProjectileAura.enabled && ProjectileAura.target != null)
        ) return

        val hand = when {
            player.mainHandItem.`is`(Items.FISHING_ROD) -> InteractionHand.MAIN_HAND
            player.offhandItem.`is`(Items.FISHING_ROD) -> InteractionHand.OFF_HAND
            else -> { reset(); return }
        }
        val hook = player.fishing
        if (hook != null) {
            if (!waitingForHookRemoval && (hook as FishingHookAccessor).nocturneIsBiting()) {
                gameMode.useItem(player, hand)
                waitingForHookRemoval = true
                castCountdown = recastDelay.getInt()
            }
            return
        }

        if (waitingForHookRemoval) {
            waitingForHookRemoval = false
            castCountdown = recastDelay.getInt()
        }
        if (--castCountdown <= 0) {
            gameMode.useItem(player, hand)
            castCountdown = recastDelay.getInt()
        }
    }

    private fun reset() {
        castCountdown = 10
        waitingForHookRemoval = false
    }
}
