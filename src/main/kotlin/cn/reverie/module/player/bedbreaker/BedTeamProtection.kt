package cn.reverie.module.player.bedbreaker

import kotlin.math.sqrt

/** Holds one team identity for a tick; missing or changing helmet colors stop automatic mining. */
internal class BedTeamProtection<C : Any>(colorSamples: Map<C, List<Int>>) {
    private val samples = colorSamples.mapValues { (_, values) -> values.map { it and 0xFFFFFF }.distinct() }
    private val labs = samples.mapValues { (_, values) -> values.map(::toLab) }

    var protectedColor: C? = null
        private set
    var helmetRgb: Int? = null
        private set
    var identification: Identification<C>? = null
        private set
    private var pendingColor: C? = null
    private var pendingTicks = 0
    private var readable = false

    val readyToMine: Boolean
        get() = readable && protectedColor != null && pendingColor == null

    fun observeHelmetColor(rgb: Int?) {
        helmetRgb = rgb?.and(0xFFFFFF)
        identification = helmetRgb?.let(::identifyColor)
        val detected = identification?.color
        readable = detected != null
        if (detected == null || detected == protectedColor) {
            pendingColor = null
            pendingTicks = 0
            return
        }
        if (detected != pendingColor) {
            pendingColor = detected
            pendingTicks = 1
        } else {
            pendingTicks++
        }
        if (pendingTicks >= STABLE_TICKS) {
            protectedColor = detected
            pendingColor = null
            pendingTicks = 0
        }
    }

    fun isProtectedBed(color: C): Boolean = !readyToMine || color == protectedColor

    /** Check both the source bed and the block actually sent to the mining controller. */
    fun canMine(sourceBedColor: C?, targetBedColor: C?): Boolean =
        readyToMine && sourceBedColor != null && sourceBedColor != protectedColor && targetBedColor != protectedColor

    fun reset() {
        protectedColor = null
        helmetRgb = null
        identification = null
        pendingColor = null
        pendingTicks = 0
        readable = false
    }

    private fun identifyColor(rgb: Int): Identification<C>? {
        val normalized = rgb and 0xFFFFFF
        val helmetLab = toLab(normalized)
        val ranked = labs.map { (color, references) ->
            Candidate(color, sqrt(references.minOf { distanceSquared(helmetLab, it) }))
        }.sortedBy { it.distance }
        val best = ranked.firstOrNull() ?: return null
        // A readable tint always has one result. Expose approximate matches and runners-up in chat.
        return Identification(best.color, normalized in samples.getValue(best.color), ranked.take(3))
    }

    data class Candidate<C>(val color: C, val distance: Double)
    data class Identification<C>(val color: C, val exact: Boolean, val candidates: List<Candidate<C>>)

    private fun distanceSquared(first: LabColor, second: LabColor): Double {
        val lightness = first.lightness - second.lightness
        val greenRed = first.greenRed - second.greenRed
        val blueYellow = first.blueYellow - second.blueYellow
        return lightness * lightness + greenRed * greenRed + blueYellow * blueYellow
    }

    private fun toLab(rgb: Int): LabColor {
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        val red = linear((rgb shr 16) and 0xFF)
        val green = linear((rgb shr 8) and 0xFF)
        val blue = linear(rgb and 0xFF)
        val x = (0.4124 * red + 0.3576 * green + 0.1805 * blue) / 0.95047
        val y = 0.2126 * red + 0.7152 * green + 0.0722 * blue
        val z = (0.0193 * red + 0.1192 * green + 0.9505 * blue) / 1.08883
        fun pivot(value: Double): Double =
            if (value > 0.008856) Math.cbrt(value) else 7.787 * value + 16.0 / 116.0
        val fx = pivot(x)
        val fy = pivot(y)
        val fz = pivot(z)
        return LabColor(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
    }

    private data class LabColor(val lightness: Double, val greenRed: Double, val blueYellow: Double)

    private companion object {
        const val STABLE_TICKS = 3
    }
}
