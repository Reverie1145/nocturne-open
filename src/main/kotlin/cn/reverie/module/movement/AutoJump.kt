package cn.reverie.module.movement

import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.module.world.Scaffold
import cn.reverie.util.player.MovementUtil
import net.minecraft.client.Minecraft

object AutoJump : Module(
    name = "AutoJump",
    description = "Jump automatically while walking on the ground",
    category = Category.MOVEMENT
) {
    val forwardOnly = BooleanSetting("Forward Only", "Jump only when moving forward", false)

    fun onTick() {
        if (!enabled) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (mc.gui.screen() != null || !player.isAlive || player.isSpectator ||
            !player.onGround() || player.isInWater || player.isInLava ||
            player.onClimbable() || player.isPassenger || player.isShiftKeyDown ||
            mc.options.keyJump.isDown || Scaffold.enabled
        ) return
        if (forwardOnly.value) {
            if (player.input.moveVector.y <= 0f) return
        } else if (!MovementUtil.isMoving(player)) return
        player.jumpFromGround()
    }
}
