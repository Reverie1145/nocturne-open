package cn.reverie.module.combat.killaura

import cn.reverie.module.combat.KillAura
import cn.reverie.module.combat.Criticals
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity

internal fun KillAura.doAttack(player: LocalPlayer, entity: LivingEntity, swingBeforeAttack: Boolean = false) {
    if (!Criticals.allowsAttack(player, entity)) return
    val gameMode = Minecraft.getInstance().gameMode ?: return
    val sequenceBeforeAttack = attackSwingSequence
    if (swingBeforeAttack) player.swing(InteractionHand.MAIN_HAND)
    gameMode.attack(player, entity)
    if (attackSwingSequence == sequenceBeforeAttack) return
    if (!swingBeforeAttack) player.swing(InteractionHand.MAIN_HAND)
}

internal fun KillAura.attackForVelocityImpl(player: LocalPlayer, entity: LivingEntity): Boolean {
    if (!enabled || attackedThisTick || target !== entity) return false
    if (KillAuraWatchdogBlock.blocksInputAttack(player)) return false
    if (!entity.isAlive || entity.level() !== player.level() || !canAttack(player, entity)) return false
    if (Minecraft.getInstance().gameMode == null) return false
    doAttack(player, entity)
    if (!attackedThisTick) return false
    attackTimer.reset()
    return true
}

internal fun KillAura.unBlock(player: LocalPlayer) {
    KillAuraWatchdogBlock.reset()
    blocking = false
    blockingState = false
    renderBlock = false
    fakeBlockRenderTarget = null
    fakeBlockOutOfRangeTicks = 0
}
