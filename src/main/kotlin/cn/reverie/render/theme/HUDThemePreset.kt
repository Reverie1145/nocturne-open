package cn.reverie.render.theme

import cn.reverie.render.nanovg.NanoVGColor
import kotlin.math.cos

enum class HUDThemePreset(
    val displayName: String,
    val colors: List<NanoVGColor>
) {
    NOCTURNE(
        "Nocturne",
        listOf(
            NanoVGColor.fromHex("#7B61FF"),
            NanoVGColor.fromHex("#5E6AD2"),
            NanoVGColor.fromHex("#9480FF")
        )
    ),
    SAKURA(
        "Sakura",
        listOf(
            NanoVGColor.fromHex("#FFAAA6"),
            NanoVGColor.fromHex("#FF85A2"),
            NanoVGColor.fromHex("#FF6584")
        )
    ),
    CYBERPUNK(
        "Cyberpunk",
        listOf(
            NanoVGColor.fromHex("#00F0FF"),
            NanoVGColor.fromHex("#9D00FF"),
            NanoVGColor.fromHex("#FF007F")
        )
    ),
    OCEAN(
        "Ocean",
        listOf(
            NanoVGColor.fromHex("#00E5FF"),
            NanoVGColor.fromHex("#0072FF"),
            NanoVGColor.fromHex("#0047AB")
        )
    ),
    SUNSET(
        "Sunset",
        listOf(
            NanoVGColor.fromHex("#FFA000"),
            NanoVGColor.fromHex("#FF5722"),
            NanoVGColor.fromHex("#DD2476")
        )
    ),
    MINT(
        "Mint",
        listOf(
            NanoVGColor.fromHex("#00F260"),
            NanoVGColor.fromHex("#0575E6"),
            NanoVGColor.fromHex("#00D2D3")
        )
    ),
    CRIMSON(
        "Crimson",
        listOf(
            NanoVGColor.fromHex("#FF416C"),
            NanoVGColor.fromHex("#8A2387"),
            NanoVGColor.fromHex("#FF4B2B")
        )
    ),
    LAVENDER(
        "Lavender",
        listOf(
            NanoVGColor.fromHex("#B06AB3"),
            NanoVGColor.fromHex("#4568DC"),
            NanoVGColor.fromHex("#E0C3FC")
        )
    ),
    ZINC(
        "Zinc",
        listOf(
            NanoVGColor.fromHex("#FFFFFF"),
            NanoVGColor.fromHex("#94A3B8"),
            NanoVGColor.fromHex("#64748B")
        )
    );

    fun sample(offset: Float, speed: Float = 1.0f): NanoVGColor {
        val stops = colors
        val n = stops.size
        if (n == 0) return NanoVGColor.WHITE
        if (n == 1) return stops[0]

        val cycleMs = (3600L / speed.coerceAtLeast(0.1f)).toLong()
        val time = ((System.currentTimeMillis() % cycleMs).toFloat() / cycleMs.toFloat() + offset) % 1.0f
        val wrappedTime = if (time < 0f) time + 1.0f else time

        val pos = wrappedTime * n
        val i1 = pos.toInt() % n
        val i2 = (i1 + 1) % n
        val rawFraction = pos - pos.toInt()
        val smoothFraction = ((1.0 - cos(rawFraction.toDouble() * Math.PI)) / 2.0).toFloat()

        return Theme.interpolateGamma(stops[i1], stops[i2], smoothFraction)
    }

    companion object {
        val names: List<String> = values().map { it.displayName }

        fun fromName(name: String): HUDThemePreset {
            return values().firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: NOCTURNE
        }
    }
}
