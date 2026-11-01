package cn.reverie.module.combat.backtrack

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

data class BacktrackPosition(
    val entityId: Int,
    val position: Vec3,
    val hitbox: AABB,
    val timestamp: Long,
    val queueIndex: Int
)
