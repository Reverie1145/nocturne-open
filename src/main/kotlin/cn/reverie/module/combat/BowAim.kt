package cn.reverie.module.combat

import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.combat.AntiBot
import cn.reverie.module.Module
import cn.reverie.module.ModeSetting
import cn.reverie.module.NumberSetting
import cn.reverie.module.player.Teams
import cn.reverie.module.world.Scaffold
import cn.reverie.util.rotation.MoveFix
import cn.reverie.util.rotation.Rotation
import cn.reverie.util.rotation.RotationManager
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import kotlin.math.atan2
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object BowAim : Module("BowAim", "Silent bow aim with target prediction", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN) {
    val range = NumberSetting("Range", "Maximum target distance", 40.0, 5.0, 80.0, 1.0)
    val predict = BooleanSetting("Predict", "Lead moving targets", true)
    val predictFactor = NumberSetting("Predict Size", "Target movement multiplier", 1.0, 0.0, 2.0, 0.1, { predict.value })
    val minCharge = NumberSetting("Min Charge", "Charge ticks before automatic firing", 20.0, 1.0, 20.0, 1.0)
    val autoFire = BooleanSetting("Auto Fire", "Release the bow when charge is reached", true)
    val rotationSpeed = NumberSetting("Rotation Speed", "Aim speed in degrees per tick", 180.0, 10.0, 360.0, 5.0)
    val movementFixMode = ModeSetting("MoveFix", "Align movement with silent aim", "Silent", listOf("None", "Silent", "Strict"))
    val players = BooleanSetting("Players", "Target players", true)
    val mobs = BooleanSetting("Mobs", "Target other living entities", true)
    val invisibles = BooleanSetting("Invisibles", "Target invisible entities", false)

    var target: LivingEntity? = null
        private set
    var rotations: Rotation? = null
        private set

    fun getMoveFix(): MoveFix = when {
        movementFixMode.isMode("Silent") -> MoveFix.Silent
        movementFixMode.isMode("Strict") -> MoveFix.Strict
        else -> MoveFix.None
    }

    fun getRotationSpeed(): Double = rotationSpeed.value

    fun shouldRotate(): Boolean = enabled && !Scaffold.enabled && target?.isAlive == true && rotations != null

    @JvmStatic
    fun onUpdate(player: LocalPlayer) {
        if (!enabled || Scaffold.enabled || !player.isAlive || player.isSpectator || !player.isUsingItem || player.useItem.item != Items.BOW) {
            target = null
            rotations = null
            return
        }

        val currentTarget = findTarget(player) ?: run {
            target = null
            rotations = null
            return
        }
        target = currentTarget
        val charge = player.ticksUsingItem
        val draw = (charge / 20.0).let { (it * it + it * 2.0) / 3.0 }.coerceAtMost(1.0)
        rotations = solve(player.eyePosition, currentTarget, (draw * 3.0).coerceAtLeast(0.15))?.let { Rotation(it.first, it.second) }

        // Release only after the normal movement packet has carried the aim. Sending
        // extra Rot packets around the release creates an impossible packet burst.
        val sent = RotationManager.lastSentRotation()
        val aim = rotations
        val aligned = sent != null && aim != null &&
            abs(Mth.wrapDegrees(sent.yaw - aim.yaw)) < 2.0f && abs(sent.pitch - aim.pitch) < 2.0f
        if (autoFire.value && aligned && charge >= minCharge.getInt()) {
            Minecraft.getInstance().gameMode?.releaseUsingItem(player)
            target = null
            rotations = null
        }
    }

    @JvmStatic
    fun onVisualTick(player: LocalPlayer) {
        if (shouldRotate()) RotationManager.updateRenderAngles(player)
    }

    private fun findTarget(player: LocalPlayer): LivingEntity? {
        val level = player.level()
        return level.getEntities(player, player.boundingBox.inflate(range.value))
            .filterIsInstance<LivingEntity>()
            .filter { entity ->
                entity.isAlive && !entity.isSpectator && entity !is net.minecraft.world.entity.decoration.ArmorStand &&
                    !AntiBot.isBot(entity) && !Teams.isTeammate(entity) && (invisibles.value || !entity.isInvisible) &&
                    (if (entity is Player) players.value else mobs.value) &&
                    player.distanceTo(entity) <= range.value
            }
            // Prefer the entity whose center is nearest to the current crosshair,
            // using angular distance so this works consistently at any resolution.
            .minWithOrNull(compareBy<LivingEntity> { crosshairDistance(player, it) }
                .thenBy { player.distanceToSqr(it) })
    }

    private fun crosshairDistance(player: LocalPlayer, entity: LivingEntity): Double {
        val box = entity.boundingBox
        val center = Vec3((box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5)
        val delta = center.subtract(player.eyePosition)
        val horizontal = sqrt(delta.x * delta.x + delta.z * delta.z)
        if (horizontal < 1.0E-6) return 0.0

        val targetYaw = Math.toRadians(Math.toDegrees(atan2(delta.z, delta.x)) - 90.0)
        val targetPitch = Math.toRadians(-Math.toDegrees(atan2(delta.y, horizontal)))
        val viewYaw = Math.toRadians(player.yRot.toDouble())
        val viewPitch = Math.toRadians(player.xRot.toDouble())
        val dot = cos(viewPitch) * cos(targetPitch) * cos(viewYaw - targetYaw) +
            sin(viewPitch) * sin(targetPitch)
        return kotlin.math.acos(dot.coerceIn(-1.0, 1.0))
    }

    private fun solve(origin: Vec3, target: LivingEntity, speed: Double): Pair<Float, Float>? {
        val velocity = target.deltaMovement
        val factor = if (predict.value) predictFactor.value else 0.0
        val ticks = (origin.distanceTo(target.position()) / speed).coerceIn(1.0, 60.0)
        var flight = ticks
        var result: Pair<Float, Float>? = null

        repeat(5) {
            val predicted = target.position().add(
                velocity.x * flight * factor,
                if (target.onGround() && velocity.y <= 0.0) target.bbHeight * 0.55 else
                    target.bbHeight * 0.55 + (velocity.y * flight - 0.04 * flight * flight) * factor,
                velocity.z * flight * factor
            )
            val dx = predicted.x - origin.x
            val dz = predicted.z - origin.z
            val horizontal = sqrt(dx * dx + dz * dz)
            if (horizontal < 0.01) return null
            val yaw = (Math.toDegrees(atan2(dz, dx)) - 90.0).toFloat()

            var bestPitch: Float? = null
            var bestError = Double.MAX_VALUE
            for (step in 0..800) {
                val pitch = -80.0 + step * 0.2
                val radians = Math.toRadians(pitch)
                var x = 0.0
                var y = 0.0
                var vx = speed * cos(radians)
                var vy = -speed * sin(radians)
                for (tick in 1..60) {
                    val nextX = x + vx
                    val nextY = y + vy
                    if (nextX >= horizontal) {
                        val fraction = ((horizontal - x) / (nextX - x)).coerceIn(0.0, 1.0)
                        val error = kotlin.math.abs(y + (nextY - y) * fraction - (predicted.y - origin.y))
                        if (error < bestError) {
                            bestError = error
                            bestPitch = pitch.toFloat()
                            flight = tick - 1 + fraction
                        }
                        break
                    }
                    x = nextX
                    y = nextY
                    vx *= 0.99
                    vy = vy * 0.99 - 0.05
                }
            }
            result = bestPitch?.let { yaw to it }
        }
        return result
    }
}
