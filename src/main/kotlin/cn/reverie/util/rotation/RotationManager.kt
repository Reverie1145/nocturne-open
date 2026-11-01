package cn.reverie.util.rotation

import cn.reverie.mixin.client.MinecraftAccessor
import cn.reverie.app.event.player.MoveInputEvent
import cn.reverie.util.player.MovementUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import kotlin.math.abs
object RotationManager {

    @JvmField
    var currentRotations: FloatArray? = null

    @JvmField
    var targetRotations: FloatArray? = null

    @JvmField
    var lastRotations: FloatArray? = null

    @JvmField
    var correctMovement: MoveFix = MoveFix.None

    @JvmField
    var rotationSpeed: Double = 180.0

    @JvmField
    var enabled: Boolean = false

    @JvmField
    var serverRotation: Rotation? = null

    @JvmField
    var targetRotation: Rotation? = null

    @JvmField
    var currentRotation: Rotation? = null

    private var lastSentYaw = 0f
    private var lastSentPitch = 0f
    private var hasLastSent = false
    private var duplicateNudgeSign = 1

    @JvmField
    var renderBodyYaw: Float = 0f

    @JvmField
    var renderHeadYaw: Float = 0f
    private var renderReady = false

    private var visualYaw: Float = 0f
    private var lastVisualYaw: Float = 0f
    private var visualPitch: Float = 0f
    private var lastVisualPitch: Float = 0f
    private var visualDecayTicks: Int = 0
    private var isVisualActive: Boolean = false

    @JvmStatic
    fun isVisualRotating(): Boolean = isVisualActive || canRotation()

    @JvmStatic
    fun setRotations(rotations: FloatArray?, speed: Double, fix: MoveFix) {
        targetRotations = rotations
        rotationSpeed = speed
        correctMovement = fix
        if (rotations != null && rotations.size >= 2) {
            targetRotation = Rotation(rotations[0], rotations[1])
        }
        enabled = true
    }

    @JvmStatic
    fun setRotations(
        rotation: Rotation?,
        fix: MoveFix,
        smooth: Boolean,
        smoothMode: SmoothMode,
        speed: Float,
        ticks: Int,
        priority: Priority
    ) {
        if (rotation != null) {
            setRotations(floatArrayOf(rotation.yaw, rotation.pitch), speed.toDouble(), fix)
        }
    }

    @JvmStatic
    fun getRotation(): Rotation {
        val current = currentRotations
        if (current != null && current.size >= 2) {
            return Rotation(current[0], current[1])
        }
        val player = Minecraft.getInstance().player
        if (player != null) {
            return Rotation(player.yRot, player.xRot)
        }
        return Rotation.ZERO
    }

    @JvmStatic
    fun setView(rotations: FloatArray?, fix: MoveFix) {
        if (rotations == null) {
            enabled = false
            return
        }
        enabled = true
        correctMovement = fix
        targetRotations = rotations
        lastRotations = rotations
        currentRotations = rotations
        if (rotations.size >= 2) {
            val rot = Rotation(rotations[0], rotations[1])
            targetRotation = rot
            currentRotation = rot
            serverRotation = rot
        }
        refreshCrosshair()
    }

    @JvmStatic
    fun canRotation(): Boolean =
        enabled && currentRotations != null && lastRotations != null && targetRotations != null

    @JvmStatic
    fun clearRotationTarget() {
        enabled = false
        hasLastSent = false
    }

    @JvmStatic
    fun onLivingUpdate(player: LocalPlayer) = advanceLivingUpdate(player, RotationProfile.isInstantRotation())

    @JvmStatic
    fun advanceLivingUpdate(player: LocalPlayer, preserveFeatureRotation: Boolean) {
        var snap = false
        val fallback = floatArrayOf(player.yRot, player.xRot)
        lastRotations = currentRotations?.clone() ?: fallback
        if (enabled) {
            val target = targetRotations ?: fallback
            if (snap && targetRotations != null) {
                currentRotations = targetRotations!!.clone()
                lastRotations = currentRotations!!.clone()
            } else if (preserveFeatureRotation) {

            } else {
                currentRotations = RotationUtil.getSmoothRotation(lastRotations!!, target, rotationSpeed)
            }
        } else {
            currentRotations = fallback
        }

        if (currentRotations != null && currentRotations!!.size >= 2) {
            serverRotation = Rotation(currentRotations!![0], currentRotations!![1])
            currentRotation = serverRotation
        }

        refreshCrosshair()

        lastVisualYaw = visualYaw
        lastVisualPitch = visualPitch

        if (canRotation()) {
            val cur = currentRotations!!
            if (!isVisualActive) {
                lastVisualYaw = player.yRot
                lastVisualPitch = player.xRot
            }
            visualYaw = cur[0]
            visualPitch = cur[1]
            visualDecayTicks = 4
            isVisualActive = true
        } else if (visualDecayTicks > 0) {
            visualDecayTicks--
            val pYaw = player.yRot
            val pPitch = player.xRot
            visualYaw += Mth.wrapDegrees(pYaw - visualYaw) * 0.45f
            visualPitch += (pPitch - visualPitch) * 0.45f
            if (visualDecayTicks == 0 || (abs(Mth.wrapDegrees(pYaw - visualYaw)) < 0.5f && abs(pPitch - visualPitch) < 0.5f)) {
                visualDecayTicks = 0
                isVisualActive = false
                visualYaw = pYaw
                visualPitch = pPitch
                lastVisualYaw = pYaw
                lastVisualPitch = pPitch
            }
        } else {
            isVisualActive = false
            visualYaw = player.yRot
            visualPitch = player.xRot
            lastVisualYaw = player.yRot
            lastVisualPitch = player.xRot
        }
        renderBodyYaw = visualYaw
        renderHeadYaw = visualYaw
    }

    @JvmStatic
    fun update(player: LocalPlayer) = onLivingUpdate(player)

    @JvmStatic
    fun onMotionPre(player: LocalPlayer): FloatArray {
        if (!enabled || currentRotations == null || lastRotations == null || targetRotations == null) {
            val real = floatArrayOf(player.yRot, player.xRot)
            currentRotations = real
            targetRotations = real
            lastRotations = real
            hasLastSent = false
        }
        if (!canRotation()) {
            return floatArrayOf(player.yRot, player.xRot)
        }
        var yaw = currentRotations!![0]
        var pitch = currentRotations!![1]
        if (correctMovement == MoveFix.None && hasLastSent && yaw == lastSentYaw && pitch == lastSentPitch) {
            val nudged = RotationUtil.nudgeRotation(yaw, pitch, duplicateNudgeSign)
            yaw = nudged[0]
            pitch = nudged[1]
            currentRotations!![0] = yaw
            currentRotations!![1] = pitch
            duplicateNudgeSign = -duplicateNudgeSign
        }
        lastSentYaw = yaw
        lastSentPitch = pitch
        hasLastSent = true
        return floatArrayOf(yaw, pitch)
    }

    @JvmStatic
    fun packetRotation(player: LocalPlayer): FloatArray = onMotionPre(player)

    @JvmStatic
    fun peekPacketRotation(player: LocalPlayer): FloatArray {
        val cur = currentRotations
        if (canRotation() && cur != null && cur.size >= 2) {
            return floatArrayOf(cur[0], cur[1])
        }
        return floatArrayOf(player.yRot, player.xRot)
    }

    /** Rotation carried by the last flying packet that was actually sent to the server. */
    @JvmStatic
    fun lastSentRotation(): Rotation? =
        if (hasLastSent) Rotation(lastSentYaw, lastSentPitch) else null

    @JvmStatic
    fun onMoveInput(player: LocalPlayer, e: MoveInputEvent) {
        if (!canRotation() || correctMovement == MoveFix.None) {
            return
        }
        val current = currentRotations ?: return
        if (correctMovement == MoveFix.Silent) {
            MovementUtil.fixMovement(e, current[0])
        }
    }

    @JvmStatic
    fun movementYaw(): Float? {
        if (!canRotation() || correctMovement == MoveFix.None) {
            return null
        }
        return currentRotations!![0]
    }

    @JvmStatic
    fun viewRotation(partialTick: Float): FloatArray? {
        if (!isVisualRotating()) {
            return null
        }
        return floatArrayOf(
            getLerpedYaw(partialTick),
            getLerpedPitch(partialTick)
        )
    }

    @JvmStatic
    fun computeSilentInput(player: LocalPlayer): Pair<Float, Float>? {
        if (!canRotation() || correctMovement != MoveFix.Silent) {
            return null
        }
        val current = currentRotations ?: return null
        val analog = player.input.moveVector
        val forward = analog.y
        val strafe = analog.x
        if (forward == 0f && strafe == 0f) {
            return null
        }
        val event = MoveInputEvent(forward, strafe, false, false, false)
        MovementUtil.fixMovement(event, current[0])
        return event.forward to event.strafe
    }

    @JvmStatic
    fun updateRenderAngles(player: LocalPlayer) {
        renderBodyYaw = visualYaw
        renderHeadYaw = visualYaw
    }

    @JvmStatic
    fun getLerpedYaw(tickDelta: Float): Float {
        if (!isVisualRotating()) {
            val player = Minecraft.getInstance().player
            return player?.getViewYRot(tickDelta) ?: 0.0f
        }
        return Mth.rotLerp(tickDelta, lastVisualYaw, visualYaw)
    }

    @JvmStatic
    fun getLerpedPitch(tickDelta: Float): Float {
        if (!isVisualRotating()) {
            val player = Minecraft.getInstance().player
            return player?.getViewXRot(tickDelta) ?: 0.0f
        }
        return Mth.lerp(tickDelta, lastVisualPitch, visualPitch)
    }

    @JvmStatic
    fun overrideBodyRot(): Float? = if (isVisualRotating()) visualYaw else null

    @JvmStatic
    fun overrideHeadRot(): Float? = if (isVisualRotating()) visualYaw else null

    @JvmStatic
    fun overridePitch(): Float? = if (isVisualRotating()) visualPitch else null

    fun captureVisualLook(): VisualLook? {
        val player = Minecraft.getInstance().player ?: return null
        return VisualLook(player.yRot, player.xRot, player.yRotO, player.xRotO)
    }

    fun restoreVisualLookIfServerAppliedSilent(visual: VisualLook?) {
        if (visual == null) return
        val player = Minecraft.getInstance().player ?: return
        if (!matchesSentLook(player.yRot, player.xRot)) return
        player.yRot = visual.yaw
        player.xRot = visual.pitch
        player.yRotO = visual.lastYaw
        player.xRotO = visual.lastPitch
    }

    private fun matchesSentLook(yaw: Float, pitch: Float): Boolean {
        if (hasLastSent && isCloseLook(yaw, pitch, lastSentYaw, lastSentPitch)) {
            return true
        }
        val current = currentRotations
        return canRotation() && current != null && isCloseLook(yaw, pitch, current[0], current[1])
    }

    private fun isCloseLook(yaw: Float, pitch: Float, otherYaw: Float, otherPitch: Float): Boolean {
        return abs(Mth.wrapDegrees(yaw - otherYaw)) < 2.0f && abs(pitch - otherPitch) < 2.0f
    }

    @JvmStatic
    internal fun refreshCrosshair() {
        val mc = Minecraft.getInstance()
        try {
            (mc as MinecraftAccessor).nocturnePick(1.0f)
        } catch (_: Throwable) {
        }
    }

    data class VisualLook(val yaw: Float, val pitch: Float, val lastYaw: Float, val lastPitch: Float)
}
