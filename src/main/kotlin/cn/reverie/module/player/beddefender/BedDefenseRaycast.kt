package cn.reverie.module.player.beddefender

import cn.reverie.util.rotation.GrimRayCastUtil
import cn.reverie.util.rotation.RayCastUtil
import cn.reverie.util.rotation.Rotation
import cn.reverie.util.rotation.RotationManager
import cn.reverie.util.rotation.RotationUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/** Placement checks matching the RayCast choices available in Scaffold. */
internal object BedDefenseRaycast {
    private enum class Mode(val label: String) {
        SIMPLE("Simple"), NORMAL("Normal"), MEDIUM("Medium"), NEW("New"), OFF("Off"),
        HANABI("Hanabi"), GRIM("Grim"), STRICT("Strict"), FACE("Face"), REACH("Reach"),
        SHORT_REACH("Short Reach"), PREVIOUS("Previous"), DUAL("Dual"), STABLE("Stable"),
        PERMISSIVE("Permissive"), VISIBLE("Visible"), EYE("Eye"), PRECISE("Precise"),
        TIGHT("Tight"), WIDE("Wide"), AIM_POINT("Aim Point"), HYBRID("Hybrid")
    }

    val modes: List<String> = Mode.entries.map { it.label }

    /** Preserve the preceding motion separately; RotationManager.serverRotation already contains this tick's aim. */
    data class Motion(val rotation: Rotation, val position: Vec3, val eye: Vec3)

    fun captureMotion(player: LocalPlayer): Motion = Motion(
        RotationManager.lastSentRotation() ?: Rotation(player.yRot, player.xRot),
        player.position(), player.eyePosition
    )

    fun reach(mode: String, player: LocalPlayer): Double {
        val limit = when (mode) {
            "Short Reach" -> 3.0
            "Tight" -> 3.5
            "Normal", "Medium", "New", "Previous", "Dual", "Stable", "Permissive", "Visible", "Precise", "Hybrid" -> 4.5
            "Wide" -> 5.0
            else -> player.blockInteractionRange()
        }
        return limit.coerceAtMost(player.blockInteractionRange())
    }

    /** All modes retain a reachable, unobstructed support face, including Off and the angle-only checks. */
    fun visibleHit(
        mode: String, player: LocalPlayer, level: ClientLevel,
        support: BlockPos, face: Direction, point: Vec3
    ): BlockHitResult? {
        if (!BedDefenseBlocks.canSupport(level, support)) return null
        val eye = player.eyePosition
        val toward = point.subtract(eye)
        val distance = toward.length()
        val maxReach = reach(mode, player)
        if (distance < 1.0e-6 || distance > maxReach) return null
        val traceRange = (distance + 0.05).coerceAtMost(maxReach)
        val hit = level.clip(ClipContext(
            eye, eye.add(toward.scale(traceRange / distance)),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
        ))
        if (!matches(hit, support, face)) return null
        if (mode == "Strict" || mode == "Visible") {
            val outline = RayCastUtil.rayCast(RotationUtil.toRotation(point), traceRange, entity = player) as? BlockHitResult
            if (!matches(outline, support, face)) return null
        }
        return hit
    }

    fun placementHit(
        modeName: String, player: LocalPlayer, level: ClientLevel,
        support: BlockPos, face: Direction, point: Vec3,
        motion: Motion, previous: Motion?
    ): BlockHitResult? {
        val mode = Mode.entries.firstOrNull { it.label == modeName } ?: return null
        val visible = visibleHit(modeName, player, level, support, face, point) ?: return null
        val maxReach = reach(modeName, player)
        val aimed = RotationUtil.toRotation(point)
        val sent = motion.rotation
        val priorPosition = previous?.position ?: Vec3(player.xOld, player.yOld, player.zOld)
        val priorEye = previous?.eye ?: priorPosition.add(0.0, player.eyeHeight.toDouble(), 0.0)

        fun trace(rotation: Rotation, eye: Vec3 = motion.eye, collider: Boolean = false): BlockHitResult = level.clip(ClipContext(
            eye, eye.add(RotationUtil.getVectorForRotation(rotation.yaw, rotation.pitch).scale(maxReach)),
            if (collider) ClipContext.Block.COLLIDER else ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE, player
        ))

        val packet by lazy { trace(sent) }
        val aimHit by lazy { trace(aimed) }

        // A loose policy can approve a rotation, but its interaction still uses the verified destination face.
        fun approved(hit: BlockHitResult? = null): BlockHitResult = if (matches(hit, support, face)) hit!! else visible
        fun yawWithin(degrees: Float): Boolean = abs(Mth.wrapDegrees(sent.yaw - aimed.yaw)) <= degrees

        return when (mode) {
            Mode.OFF -> visible
            Mode.SIMPLE -> if (yawWithin(35f)) visible else null
            Mode.NORMAL -> {
                val rotation = if (RotationManager.canRotation()) RotationManager.targetRotation ?: sent else sent
                val hit = trace(rotation)
                if (matches(hit, support, face, exactFace = false)) approved(hit) else null
            }
            Mode.MEDIUM, Mode.REACH -> if (matches(packet, support, face, exactFace = false)) approved(packet) else null
            Mode.NEW, Mode.FACE, Mode.SHORT_REACH, Mode.TIGHT, Mode.WIDE -> if (matches(packet, support, face)) packet else null
            Mode.HANABI -> {
                val hit = Minecraft.getInstance().hitResult as? BlockHitResult
                if (matches(hit, support, face, exactFace = false) && hit!!.location.distanceToSqr(motion.eye) <= maxReach * maxReach) approved(hit) else null
            }
            Mode.GRIM -> if (GrimRayCastUtil.canPlaceGrim(
                player, support, face, priorPosition.x, priorPosition.y, priorPosition.z, sent.yaw, sent.pitch
            )) approved(packet) else null
            Mode.STRICT -> {
                val box = AABB(support)
                if (!GrimRayCastUtil.checkPositionPlace(player, box, face, priorPosition.x, priorPosition.y, priorPosition.z) ||
                    !GrimRayCastUtil.checkPositionPlace(player, box, face, motion.position.x, motion.position.y, motion.position.z)) null
                else {
                    val hit = RayCastUtil.rayCast(sent, maxReach, entity = player) as? BlockHitResult
                    if (matches(hit, support, face) && matches(trace(sent, collider = true), support, face)) hit else null
                }
            }
            Mode.PREVIOUS -> if (matches(packet, support, face, exactFace = false) &&
                priorEye.distanceToSqr(packet.location) <= maxReach * maxReach) approved(packet) else null
            Mode.DUAL, Mode.STABLE -> {
                if (!matches(packet, support, face)) null
                else {
                    val prior = previous?.let { trace(it.rotation, it.eye) }
                    if (previous == null || matches(prior, support, face, exactFace = mode == Mode.STABLE)) packet else null
                }
            }
            Mode.PERMISSIVE -> if (matches(packet, support, face, exactFace = false) || yawWithin(12f)) approved(packet) else null
            Mode.VISIBLE -> {
                val hit = RayCastUtil.rayCast(sent, maxReach, entity = player) as? BlockHitResult
                if (matches(hit, support, face)) hit else null
            }
            Mode.EYE -> {
                val hit = trace(sent, collider = true)
                if (matches(hit, support, face)) hit else null
            }
            Mode.PRECISE -> if (matches(packet, support, face) && packet.location.distanceToSqr(motion.eye) <= 20.25) packet else null
            Mode.AIM_POINT -> if (matches(aimHit, support, face)) aimHit else null
            Mode.HYBRID -> when {
                matches(packet, support, face) -> packet
                yawWithin(8f) && matches(aimHit, support, face) -> aimHit
                else -> null
            }
        }
    }

    private fun matches(hit: BlockHitResult?, support: BlockPos, face: Direction, exactFace: Boolean = true): Boolean =
        hit != null && hit.type == HitResult.Type.BLOCK && !hit.isInside && hit.blockPos == support &&
            (!exactFace || hit.direction == face)
}
