package cn.reverie.module.world.scaffold

import kotlin.math.cos
import kotlin.math.sin

/** Ten turn-rate curves crossed with ten independent face-point trajectories. */
internal object ScaffoldRotationProfiles {
    val turns = listOf("Linear", "Slow", "Fast", "Ramp", "Brake", "Pulse", "Wave", "Step", "Breath", "Flutter")
    val paths = listOf("Center", "Left", "Right", "Top", "Bottom", "Sweep", "Orbit", "Figure Eight", "Diagonal", "Random Walk")
    val modes: List<String> = turns.flatMap { turn -> paths.map { path -> "$turn / $path" } }

    private val defaults = doubleArrayOf(1.0, 0.28, 0.72, 0.72, 0.28, 0.25, 0.22, 0.2, 0.22, 0.5)
    private val descriptions = listOf(
        "Turn speed multiplier", "Left face coordinate", "Right face coordinate", "Upper face coordinate",
        "Lower face coordinate", "Sweep width", "Orbit radius", "Figure-eight height",
        "Diagonal travel", "Random-walk spread"
    )

    fun defaultTuning(index: Int): Double = defaults[index % 10]
    fun tuningDescription(index: Int): String = descriptions[index % 10]
    fun tuningRange(index: Int): ClosedFloatingPointRange<Double> = when (index % 10) {
        0 -> 0.25..2.0
        1, 4 -> 0.1..0.49
        2, 3 -> 0.51..0.9
        9 -> 0.0..0.7
        else -> 0.0..0.4
    }

    fun index(mode: String): Int = modes.indexOf(mode)
    fun index(turn: String, path: String): Int = turns.indexOf(turn).takeIf { it >= 0 }?.let { turnIndex ->
        paths.indexOf(path).takeIf { it >= 0 }?.let { pathIndex -> turnIndex * paths.size + pathIndex }
    } ?: -1

    fun speed(index: Int, tick: Int, tuning: Double = defaultTuning(index)): Int {
        val t = tick.coerceAtLeast(0)
        val base = when (index / 10) {
            0 -> 90
            1 -> 35
            2 -> 145
            3 -> 30 + t % 10 * 9
            4 -> 120 - t % 10 * 9
            5 -> if (t % 5 == 0) 140 else 38
            6 -> (75 + 30 * sin(t * 0.35)).toInt()
            7 -> 30 + t % 4 * 25
            8 -> (65 + 20 * sin(t * 0.12)).toInt()
            else -> 65 + ((t * 17 + 11) % 29) - 14
        }.coerceIn(1, 180)
        return if (index % 10 == 0) (base * tuning).toInt().coerceIn(1, 180) else base
    }

    fun point(index: Int, tick: Int, tuning: Double = defaultTuning(index)): Pair<Double, Double> {
        val t = tick.coerceAtLeast(0).toDouble()
        val phase = t * 0.16
        return when (index % 10) {
            0 -> 0.5 to 0.5
            1 -> tuning to 0.5
            2 -> tuning to 0.5
            3 -> 0.5 to tuning
            4 -> 0.5 to tuning
            5 -> (0.5 + tuning * sin(phase)) to 0.5
            6 -> (0.5 + tuning * cos(phase)) to (0.5 + tuning * sin(phase))
            7 -> (0.5 + 0.25 * sin(phase)) to (0.5 + tuning * sin(phase * 2))
            8 -> (0.5 + tuning * sin(phase)) to (0.5 + tuning * sin(phase))
            else -> {
                val step = (t / 8).toLong()
                val x = ((step * 73 + 19) % 101).toDouble() / 100.0
                val y = ((step * 47 + 53) % 103).toDouble() / 102.0
                (0.5 + tuning * (x - 0.5)) to (0.5 + tuning * (y - 0.5))
            }
        }
    }
}
