package cn.reverie.module.movement.noslow

import cn.reverie.mixin.world.ClientLevelAccessor
import cn.reverie.module.combat.KillAura
import cn.reverie.module.movement.NoSlow
import cn.reverie.util.network.PacketUtil
import cn.reverie.util.rotation.RotationManager
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand

object HypixelBNoSlow : NoSlowMode {
    override val modeName: String = "HypixelB"

    private var unblockPending = false
    private var waitingRelease = false
    private var slowByWaiting = false

    override fun onEnable() {
        reset()
    }

    override fun onDisable() {
        reset()
    }

    override fun onWorldChange() {
        reset()
    }

    fun isBusy(): Boolean = waitingRelease || slowByWaiting

    override fun onTick() {
        val player = Minecraft.getInstance().player
        if (player == null) {
            reset()
            return
        }
        if (!wantsBlock(player)) releaseNow()
    }

    override fun onItemUseSlowdown(original: Float): Float {
        val player = Minecraft.getInstance().player ?: return original
        if (!isSword(player) || !NoSlow.sword.value) return original

        if (wantsBlock(player)) {
            keepSprint(player)
            return original
        }
        return 1.0f
    }

    override fun onPreMotion(player: LocalPlayer) {
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.level == null) return
        if (!wantsBlock(player) || unblockPending) {
            if (!wantsBlock(player)) reset()
            return
        }

        unblockPending = true
        waitingRelease = true
        slowByWaiting = true
        PacketUtil.sendPacket(releasePacket())

        val rotation = RotationManager.serverRotation ?: return
        val usePacket = try {
            (mc.level as ClientLevelAccessor)
                .nocturneGetBlockStatePredictionHandler()
                .startPredicting()
                .use { prediction ->
                    ServerboundUseItemPacket(
                        InteractionHand.MAIN_HAND,
                        prediction.currentSequence(),
                        rotation.yaw,
                        rotation.pitch
                    )
                }
        } catch (_: Throwable) {
            ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, rotation.yaw, rotation.pitch)
        }
        PacketUtil.sendPacket(usePacket)
        PacketUtil.sendPacket(ServerboundSwingPacket(InteractionHand.MAIN_HAND))

        unblockPending = false
        waitingRelease = false
        slowByWaiting = false
    }

    private fun wantsBlock(player: LocalPlayer): Boolean =
        isSword(player) && (auraWantsBlock() || Minecraft.getInstance().options.keyUse.isDown)

    private fun isSword(player: LocalPlayer): Boolean = player.mainHandItem.`is`(ItemTags.SWORDS)

    private fun auraWantsBlock(): Boolean = KillAura.enabled && KillAura.isRenderBlock

    private fun keepSprint(player: LocalPlayer) {
        if (NoSlow.keepSprinting.value && Minecraft.getInstance().options.keyUp.isDown) {
            player.setSprinting(true)
        }
    }

    private fun releaseNow() {
        if (!waitingRelease) return
        PacketUtil.sendPacket(releasePacket())
        waitingRelease = false
    }

    private fun releasePacket() = ServerboundPlayerActionPacket(
        ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
        BlockPos.ZERO,
        Direction.DOWN
    )

    private fun reset() {
        releaseNow()
        unblockPending = false
        slowByWaiting = false
    }
}
