package cn.reverie.app.event.player

import cn.reverie.app.event.Cancellable
import net.minecraft.world.entity.Entity

class AttackEvent(val target: Entity) : Cancellable {
    override var isCancelled: Boolean = false
}
