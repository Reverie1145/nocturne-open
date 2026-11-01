package cn.reverie.manager

import cn.reverie.app.event.player.MoveInputEvent
import cn.reverie.module.ModuleManager
import cn.reverie.module.combat.Velocity
import cn.reverie.module.combat.Criticals
import cn.reverie.module.combat.KnockbackDisplace
import cn.reverie.module.movement.GrimSpeed
import cn.reverie.module.movement.AutoWalk
import cn.reverie.module.movement.Sprint
import cn.reverie.module.movement.Stuck
import cn.reverie.module.world.Scaffold
import cn.reverie.util.rotation.RotationManager
import net.minecraft.client.player.LocalPlayer

object InputHookManager {
    @JvmStatic fun onKey(keyCode: Int) {
        ModuleManager.onKey(keyCode)
        cn.reverie.app.agent.AgentManager.onKey(keyCode)
    }

    @JvmStatic
    fun onMoveInput(player: LocalPlayer, event: MoveInputEvent) {
        AutoWalk.onMoveInput(player, event)
        Scaffold.onMoveInput(player, event)
        KnockbackDisplace.onMoveInput(player, event)
        RotationManager.onMoveInput(player, event)
        Sprint.onMoveInput(player, event)
        Velocity.onMoveInput(player, event)
        GrimSpeed.onMoveInput(player, event)
        Stuck.onMoveInput(event)
        Criticals.onMoveInput(player, event)
        cn.reverie.app.agent.AgentManager.onMoveInput(event)
    }
}
