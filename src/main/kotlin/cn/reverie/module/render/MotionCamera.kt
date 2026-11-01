package cn.reverie.module.render

import cn.reverie.mixin.client.CameraAccessor
import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import kotlin.math.exp

object MotionCamera : Module(
    name = "MotionCamera",
    description = "Smoothly follow player movement in third person",
    category = Category.RENDER,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {

    val followSpeed = NumberSetting(
        "Follow Speed",
        "How quickly the third-person camera follows player movement",
        12.0,
        1.0,
        30.0,
        0.5
    )

    private var initialized = false
    private var followedPlayer: LocalPlayer? = null
    private var followedLevel: ClientLevel? = null
    private var followedPivot = Vec3.ZERO
    private var previousTargetPivot: Vec3? = null
    private var followedDistance = 0.0
    private var previousYaw = 0.0f
    private var previousPitch = 0.0f
    private var previousMirrored = false
    private var lastFrameNanos = 0L

    /** Called after vanilla has positioned and collision-adjusted the camera. */
    fun apply(camera: Camera, deltaTracker: net.minecraft.client.DeltaTracker) {
        val client = Minecraft.getInstance()
        val player = client.player
        val level = client.level
        if (!enabled || player == null || level == null || !camera.isDetached || camera.entity() !== player || player.isPassenger) {
            resetFollow()
            return
        }

        val partialTick = camera.getCameraEntityPartialTicks(deltaTracker)
        val targetPivot = player.getEyePosition(partialTick)
        val vanillaPosition = camera.position()
        val targetDistance = targetPivot.distanceTo(vanillaPosition)
        val targetYaw = camera.yRot()
        val targetPitch = camera.xRot()
        val mirrored = client.options.cameraType.isMirrored
        val now = System.nanoTime()

        val teleported = previousTargetPivot?.distanceToSqr(targetPivot)?.let { it > 64.0 } ?: false
        if (!initialized || followedPlayer !== player || followedLevel !== level || previousMirrored != mirrored || teleported) {
            remember(player, level, targetPivot, targetDistance, targetYaw, targetPitch, mirrored, now)
            return
        }

        val deltaSeconds = ((now - lastFrameNanos) / 1_000_000_000.0).coerceIn(1.0 / 240.0, 0.1)
        val blend = 1.0 - exp(-followSpeed.value * deltaSeconds)
        val smoothedPivot = followedPivot.add(targetPivot.subtract(followedPivot).scale(blend))
        val smoothedYaw = previousYaw + Mth.wrapDegrees(targetYaw - previousYaw) * blend.toFloat()
        val smoothedPitch = previousPitch + (targetPitch - previousPitch) * blend.toFloat()
        val smoothedDistance = if (targetDistance < followedDistance) {
            targetDistance
        } else {
            followedDistance + (targetDistance - followedDistance) * blend
        }
        val collisionSafeDistance = keepCameraOutsideBlocks(
            level,
            player,
            smoothedPivot,
            smoothedYaw,
            smoothedPitch,
            smoothedDistance
        )
        val cameraPosition = cameraPosition(smoothedPivot, smoothedYaw, smoothedPitch, collisionSafeDistance)

        val cameraAccess = camera as CameraAccessor
        cameraAccess.nocturneSetRotation(smoothedYaw, smoothedPitch)
        cameraAccess.nocturneSetPosition(cameraPosition)

        remember(player, level, smoothedPivot, collisionSafeDistance, smoothedYaw, smoothedPitch, mirrored, now, targetPivot)
    }

    private fun keepCameraOutsideBlocks(
        level: ClientLevel,
        player: LocalPlayer,
        pivot: Vec3,
        yaw: Float,
        pitch: Float,
        requestedDistance: Double
    ): Double {
        if (requestedDistance <= 0.0) return 0.0

        val direction = cameraOffsetDirection(yaw, pitch)
        var safeDistance = requestedDistance
        for (index in 0 until 8) {
            val offsetX = ((index and 1) * 2 - 1) * 0.1
            val offsetY = (((index shr 1) and 1) * 2 - 1) * 0.1
            val offsetZ = (((index shr 2) and 1) * 2 - 1) * 0.1
            val from = pivot.add(offsetX, offsetY, offsetZ)
            val to = from.add(direction.scale(requestedDistance))
            val hit = level.clip(
                ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)
            )
            if (hit.type != HitResult.Type.MISS) {
                safeDistance = minOf(safeDistance, (from.distanceTo(hit.location) - 0.05).coerceAtLeast(0.0))
            }
        }
        return safeDistance
    }

    private fun cameraPosition(pivot: Vec3, yaw: Float, pitch: Float, distance: Double): Vec3 =
        pivot.add(cameraOffsetDirection(yaw, pitch).scale(distance))

    private fun cameraOffsetDirection(yaw: Float, pitch: Float): Vec3 {
        val yawRadians = Math.toRadians(yaw.toDouble())
        val pitchRadians = Math.toRadians(pitch.toDouble())
        val horizontalScale = kotlin.math.cos(pitchRadians)
        return Vec3(
            kotlin.math.sin(yawRadians) * horizontalScale,
            kotlin.math.sin(pitchRadians),
            -kotlin.math.cos(yawRadians) * horizontalScale
        )
    }

    private fun remember(
        player: LocalPlayer,
        level: ClientLevel,
        pivot: Vec3,
        distance: Double,
        yaw: Float,
        pitch: Float,
        mirrored: Boolean,
        now: Long,
        targetPivot: Vec3 = pivot
    ) {
        initialized = true
        followedPlayer = player
        followedLevel = level
        followedPivot = pivot
        previousTargetPivot = targetPivot
        followedDistance = distance
        previousYaw = yaw
        previousPitch = pitch
        previousMirrored = mirrored
        lastFrameNanos = now
    }

    private fun resetFollow() {
        initialized = false
        followedPlayer = null
        followedLevel = null
        followedPivot = Vec3.ZERO
        previousTargetPivot = null
        followedDistance = 0.0
        previousYaw = 0.0f
        previousPitch = 0.0f
        lastFrameNanos = 0L
    }
}
