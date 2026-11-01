package cn.reverie.module.combat.killaura

import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

private const val PREDICT_REACH = 3.0

internal class IncomingAttackPredictor {
    private class Track {
        var position: Vec3 = Vec3.ZERO
        var velocity: Vec3 = Vec3.ZERO
        var swinging = false
        var lastSwingTick = -1
        var swingInterval = 0.0
        var swingSamples = 0
        var seenTick = -1
    }

    private val tracks = HashMap<Int, Track>()
    private var tick = 0
    private var holdUntil = -1
    private var hurtTick = -1
    private var hurtAttacker = -1

    var leadTicks: Int = Int.MAX_VALUE
        private set

    fun reset() {
        tracks.clear()
        tick = 0
        holdUntil = -1
        hurtTick = -1
        hurtAttacker = -1
        leadTicks = Int.MAX_VALUE
    }

    fun update(player: LocalPlayer, candidates: List<LivingEntity>, range: Double, react: Int, hold: Int): Boolean {
        tick++
        sampleTracks(player, candidates, range)
        var lead = Int.MAX_VALUE
        for (enemy in candidates) {
            if (!enemy.isAlive || enemy.level() !== player.level()) continue
            val track = tracks[enemy.id] ?: continue
            val eta = estimate(player, enemy, track, range)
            if (eta < lead) lead = eta
        }
        tracks.entries.removeIf { tick - it.value.seenTick > 40 }
        leadTicks = lead
        if (lead <= react) holdUntil = tick + hold
        return lead <= react || tick <= holdUntil
    }

    private fun sampleTracks(player: LocalPlayer, candidates: List<LivingEntity>, range: Double) {
        if (player.hurtDuration > 0 && player.hurtTime == player.hurtDuration) {
            var closest = range + 1.5
            var attacker = -1
            for (enemy in candidates) {
                if (!enemy.isAlive || enemy.level() !== player.level()) continue
                val distance = player.position().distanceTo(enemy.position())
                if (distance <= closest) {
                    closest = distance
                    attacker = enemy.id
                }
            }
            hurtTick = tick
            hurtAttacker = attacker
        }

        for (enemy in candidates) {
            if (!enemy.isAlive || enemy.level() !== player.level()) continue
            val track = tracks[enemy.id] ?: Track().also { tracks[enemy.id] = it }
            val position = enemy.position()
            if (track.seenTick >= 0) {
                val measured = position.subtract(track.position)
                track.velocity = if (track.seenTick == tick - 1) {
                    track.velocity.scale(0.6).add(measured.scale(0.4))
                } else {
                    measured
                }
            } else {
                track.velocity = enemy.deltaMovement
            }
            track.position = position
            track.seenTick = tick

            if (enemy.swinging && !track.swinging) {
                if (track.lastSwingTick >= 0) {
                    val gap = (tick - track.lastSwingTick).toDouble()
                    if (gap in 2.0..40.0) {
                        track.swingInterval = if (track.swingSamples == 0) gap else track.swingInterval * 0.7 + gap * 0.3
                        track.swingSamples++
                    }
                }
                track.lastSwingTick = tick
            }
            track.swinging = enemy.swinging
        }
    }

    private fun estimate(player: LocalPlayer, enemy: LivingEntity, track: Track, range: Double): Int {
        val relative = player.position().subtract(track.position)
        val flat = Vec3(relative.x, 0.0, relative.z)
        val distance = flat.length()
        if (distance > range + 2.0 || abs(relative.y) > 2.5) return Int.MAX_VALUE

        val direction = if (distance < 1.0e-4) Vec3.ZERO else flat.scale(1.0 / distance)
        val closing = track.velocity.subtract(player.deltaMovement).dot(direction)
        val look = Vec3(
            -sin(Math.toRadians(enemy.yRot.toDouble())),
            0.0,
            cos(Math.toRadians(enemy.yRot.toDouble()))
        )
        val facing = if (distance < 1.0e-4) 1.0 else look.dot(direction)
        val close = distance <= PREDICT_REACH + 0.25 && facing > -0.35
        val drawn = distance <= range && facing > 0.45

        var eta = Int.MAX_VALUE
        when {
            close || drawn -> eta = 0
            closing > 0.02 -> eta = ceil((distance - PREDICT_REACH) / closing).toInt().coerceIn(0, 40)
        }

        if (track.swingSamples > 0 && track.lastSwingTick >= 0 && facing > 0.30 && distance <= range + 0.75) {
            val until = track.swingInterval - (tick - track.lastSwingTick)
            if (until in 0.0..30.0) eta = minOf(eta, ceil(until).toInt())
        }

        if (enemy.id == hurtAttacker && tick - hurtTick <= 1 && distance <= range + 0.75) {
            val cadence = if (track.swingInterval > 0.0) track.swingInterval else 10.0
            val until = cadence - (tick - hurtTick)
            if (until in 0.0..30.0) eta = minOf(eta, ceil(until).toInt())
        }

        return eta
    }
}
