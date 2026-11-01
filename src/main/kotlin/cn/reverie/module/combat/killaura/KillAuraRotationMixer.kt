package cn.reverie.module.combat.killaura

import kotlin.math.cos
import kotlin.math.sin

internal object KillAuraRotationMixer {
    val turns = listOf("Constant", "Slow", "Fast", "Ramp", "Brake", "Pulse", "Wave", "Step", "Breath", "Flutter")
    val points = listOf("Nearest", "Center", "Head", "Chest", "Feet", "Left", "Right", "Orbit", "Sweep", "Random")

    fun speed(mode: String, tick: Int, base: Double, minimum: Double, maximum: Double, cycle: Double, variation: Double): Double {
        val phase = tick.coerceAtLeast(0) / cycle.coerceAtLeast(2.0)
        val unit = when (mode) {
            "Slow" -> -1.0
            "Fast" -> 1.0
            "Ramp" -> 2.0 * (phase % 1.0) - 1.0
            "Brake" -> 1.0 - 2.0 * (phase % 1.0)
            "Pulse" -> if (phase % 1.0 < 0.2) 1.0 else -0.5
            "Wave" -> sin(phase * Math.PI * 2.0)
            "Step" -> if ((phase.toInt() and 1) == 0) -0.7 else 0.7
            "Breath" -> sin(phase * Math.PI)
            "Flutter" -> sin(phase * 11.0) * 0.55 + sin(phase * 4.0) * 0.45
            else -> 0.0
        }
        return (base + unit * variation).coerceIn(minimum.coerceAtMost(maximum), maximum.coerceAtLeast(minimum))
    }

    /** Local coordinates inside a target's bounding box. */
    fun point(mode: String, tick: Int, cycle: Double, spread: Double): Triple<Double, Double, Double> {
        val phase = tick / cycle.coerceAtLeast(2.0) * Math.PI * 2.0
        val radius = spread.coerceIn(0.0, 0.4)
        return when (mode) {
            "Head" -> Triple(0.5, 0.86, 0.5)
            "Chest" -> Triple(0.5, 0.65, 0.5)
            "Feet" -> Triple(0.5, 0.2, 0.5)
            "Left" -> Triple(0.28, 0.55, 0.5)
            "Right" -> Triple(0.72, 0.55, 0.5)
            "Orbit" -> Triple(0.5 + cos(phase) * radius, 0.55, 0.5 + sin(phase) * radius)
            "Sweep" -> Triple(0.5 + sin(phase) * radius, 0.55, 0.5)
            "Random" -> {
                val step = (tick / cycle.coerceAtLeast(2.0)).toLong()
                Triple(0.5 + ((((step * 37 + 11) % 101) / 100.0) - 0.5) * 2 * radius,
                    0.55 + ((((step * 53 + 7) % 97) / 96.0) - 0.5) * radius,
                    0.5 + ((((step * 71 + 19) % 103) / 102.0) - 0.5) * 2 * radius)
            }
            else -> Triple(0.5, 0.5, 0.5)
        }
    }
}
