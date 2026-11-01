package cn.reverie.module.movement

import cn.reverie.module.*
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3
import kotlin.math.*

object Fly : Module("Fly", "Creative-style client flight", Category.MOVEMENT) {
    val speed = NumberSetting("Speed", "Horizontal blocks per tick", 0.35, 0.1, 2.0, 0.05)
    val verticalSpeed = NumberSetting("Vertical Speed", "Vertical blocks per tick", 0.35, 0.1, 2.0, 0.05)
    private var originalFlying: Boolean? = null
    private var originalMayFly: Boolean? = null
    fun onTick(player: LocalPlayer) {
        if (!enabled) return
        if (originalFlying == null) {
            originalFlying = player.abilities.flying
            originalMayFly = player.abilities.mayfly
        }
        val mc = Minecraft.getInstance()
        player.abilities.mayfly = true
        player.abilities.flying = true
        val forward = (if (mc.options.keyUp.isDown) 1 else 0) - (if (mc.options.keyDown.isDown) 1 else 0)
        val strafe = (if (mc.options.keyLeft.isDown) 1 else 0) - (if (mc.options.keyRight.isDown) 1 else 0)
        val yaw = Math.toRadians(player.yRot.toDouble())
        val length = hypot(forward.toDouble(), strafe.toDouble()).coerceAtLeast(1.0)
        val x = (-sin(yaw) * forward - cos(yaw) * strafe) * speed.value / length
        val z = (cos(yaw) * forward - sin(yaw) * strafe) * speed.value / length
        val y = ((if (mc.options.keyJump.isDown) 1 else 0) - (if (mc.options.keyShift.isDown) 1 else 0)) * verticalSpeed.value
        player.deltaMovement = Vec3(x, y, z)
        player.fallDistance = 0.0
    }
    override fun onDisable() {
        Minecraft.getInstance().player?.let {
            originalFlying?.let { wasFlying -> it.abilities.flying = wasFlying }
            originalMayFly?.let { mayFly -> it.abilities.mayfly = mayFly }
        }
        originalFlying = null
        originalMayFly = null
    }
}

