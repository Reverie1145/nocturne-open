package cn.reverie.module.movement

import cn.reverie.app.event.player.MoveInputEvent
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.module.combat.KillAura
import cn.reverie.module.world.Scaffold
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer

/** Changes the normal movement input rather than holding a key down. */
object AutoWalk : Module(
    name = "AutoWalk",
    description = "Keep walking forward until manually overridden",
    category = Category.MOVEMENT
) {
    val pauseOnCombat = BooleanSetting("Pause On Combat", "Stop when KillAura has a target", true)

    fun onMoveInput(player: LocalPlayer, event: MoveInputEvent) {
        if (!enabled || !player.isAlive || player.isSpectator || player.isPassenger ||
            player.isShiftKeyDown || Scaffold.enabled ||
            Minecraft.getInstance().gui.screen() != null ||
            (pauseOnCombat.value && KillAura.target != null)
        ) return
        // Backward input is an immediate manual override; strafing remains possible.
        if (event.forward >= 0f) event.forward = 1f
    }
}
