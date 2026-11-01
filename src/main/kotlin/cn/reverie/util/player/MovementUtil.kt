package cn.reverie.util.player

import cn.reverie.app.event.player.MoveInputEvent
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sqrt

object MovementUtil {

    fun strafe() {
        strafe(getSpeed())
    }

    fun strafe(speed: Double) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (!isMoving(player)) return

        val yaw = getDirection(player)
        player.deltaMovement = Vec3(
            -Mth.sin(yaw) * speed,
            player.deltaMovement.y,
            Mth.cos(yaw) * speed
        )
    }

    fun stop() {
        val player = Minecraft.getInstance().player ?: return
        player.deltaMovement = Vec3(0.0, 0.0, 0.0)
    }

    @JvmStatic
    fun isMoving(player: LocalPlayer? = Minecraft.getInstance().player): Boolean {
        if (player == null) return false
        return player.input.moveVector.x != 0.0f || player.input.moveVector.y != 0.0f
    }

    @JvmStatic
    fun getDirection(player: LocalPlayer = Minecraft.getInstance().player!!): Double {
        return getDirection(player.yRot, player.input.moveVector.y, player.input.moveVector.x)
    }

    @JvmStatic
    fun getDirection(rotationYaw: Float, moveForward: Float, moveStrafing: Float): Double {
        var yaw = rotationYaw
        if (moveForward < 0f) yaw += 180f

        var forward = 1f
        if (moveForward < 0f) forward = -0.5f
        else if (moveForward > 0f) forward = 0.5f

        if (moveStrafing > 0f) yaw -= 90f * forward
        if (moveStrafing < 0f) yaw += 90f * forward

        return Math.toRadians(yaw.toDouble())
    }

    @JvmStatic
    fun getSpeed(player: LocalPlayer? = Minecraft.getInstance().player): Double {
        if (player == null) return 0.0
        val x = player.deltaMovement.x
        val z = player.deltaMovement.z
        return sqrt(x * x + z * z)
    }

    @JvmStatic
    fun fixMovement(event: MoveInputEvent, yaw: Float) {
        val player = Minecraft.getInstance().player ?: return
        val forward = event.forward
        val strafe = event.strafe

        if (forward == 0f && strafe == 0f) return

        val angle = Mth.wrapDegrees(Math.toDegrees(getDirection(player.yRot, forward, strafe)))

        var closestForward = 0f
        var closestStrafe = 0f
        var closestDifference = Float.MAX_VALUE

        var predictedForward = -1f
        while (predictedForward <= 1f) {
            var predictedStrafe = -1f
            while (predictedStrafe <= 1f) {
                if (predictedStrafe != 0f || predictedForward != 0f) {
                    val predictedAngle = Mth.wrapDegrees(Math.toDegrees(getDirection(yaw, predictedForward, predictedStrafe)))
                    val difference = abs(angle - predictedAngle).toFloat()

                    if (difference < closestDifference) {
                        closestDifference = difference
                        closestForward = predictedForward
                        closestStrafe = predictedStrafe
                    }
                }
                predictedStrafe += 1f
            }
            predictedForward += 1f
        }

        if (closestForward != 0.0f && closestStrafe != 0.0f) {
            val length = Mth.sqrt(closestForward * closestForward + closestStrafe * closestStrafe)
            closestForward /= length
            closestStrafe /= length
        }

        event.forward = closestForward
        event.strafe = closestStrafe
    }

    @JvmStatic
    fun movementInputToVelocity(movementInput: Vec3, speed: Float, yaw: Float): Vec3 {
        val d = movementInput.lengthSqr()
        if (d < 1.0E-7) {
            return Vec3.ZERO
        }
        val vec = (if (d > 1.0) movementInput.normalize() else movementInput).scale(speed.toDouble())
        val f = Mth.sin(yaw.toDouble() * (Math.PI / 180.0))
        val g = Mth.cos(yaw.toDouble() * (Math.PI / 180.0))
        return Vec3(vec.x * g - vec.z * f, vec.y, vec.z * g + vec.x * f)
    }

    @JvmStatic
    fun movementInput(): Boolean {
        val options = Minecraft.getInstance().options
        return options.keyUp.isDown || options.keyDown.isDown || options.keyLeft.isDown || options.keyRight.isDown
    }

    @JvmStatic
    fun getBPS(player: LocalPlayer? = Minecraft.getInstance().player): Double {
        if (player == null) return 0.0
        val bps = hypot(player.x - player.xo, player.z - player.zo) * 20.0
        return round(bps * 100.0) / 100.0
    }
}
