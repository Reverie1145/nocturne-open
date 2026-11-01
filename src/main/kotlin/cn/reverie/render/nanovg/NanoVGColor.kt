package cn.reverie.render.nanovg

import org.lwjgl.nanovg.NVGColor
import kotlin.math.roundToInt

data class NanoVGColor(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float = 1.0f
) {
    constructor(r: Int, g: Int, b: Int, a: Int = 255) : this(
        r.coerceIn(0, 255) / 255f,
        g.coerceIn(0, 255) / 255f,
        b.coerceIn(0, 255) / 255f,
        a.coerceIn(0, 255) / 255f
    )

    fun toArgb(): Int {
        val ai = (a * 255f).roundToInt().coerceIn(0, 255)
        val ri = (r * 255f).roundToInt().coerceIn(0, 255)
        val gi = (g * 255f).roundToInt().coerceIn(0, 255)
        val bi = (b * 255f).roundToInt().coerceIn(0, 255)
        return (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    fun toRgba(): Int {
        val ri = (r * 255f).roundToInt().coerceIn(0, 255)
        val gi = (g * 255f).roundToInt().coerceIn(0, 255)
        val bi = (b * 255f).roundToInt().coerceIn(0, 255)
        val ai = (a * 255f).roundToInt().coerceIn(0, 255)
        return (ri shl 24) or (gi shl 16) or (bi shl 8) or ai
    }

    fun toHex(includeAlpha: Boolean = false): String {
        val ri = (r * 255f).roundToInt().coerceIn(0, 255)
        val gi = (g * 255f).roundToInt().coerceIn(0, 255)
        val bi = (b * 255f).roundToInt().coerceIn(0, 255)
        val ai = (a * 255f).roundToInt().coerceIn(0, 255)
        return if (includeAlpha) {
            String.format("#%02X%02X%02X%02X", ai, ri, gi, bi)
        } else {
            String.format("#%02X%02X%02X", ri, gi, bi)
        }
    }

    fun toHsv(): FloatArray {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        var h = 0f
        if (delta > 0.0001f) {
            h = when (max) {
                r -> 60f * (((g - b) / delta) % 6f)
                g -> 60f * (((b - r) / delta) + 2f)
                else -> 60f * (((r - g) / delta) + 4f)
            }
            if (h < 0f) h += 360f
        }

        val s = if (max <= 0.0001f) 0f else delta / max
        val v = max
        return floatArrayOf(h, s, v, a)
    }

    fun toNVG(dest: NVGColor): NVGColor {
        dest.r(r)
        dest.g(g)
        dest.b(b)
        dest.a(a)
        return dest
    }

    fun withAlpha(alpha: Float): NanoVGColor = copy(a = alpha.coerceIn(0f, 1f))
    fun multiplyAlpha(factor: Float): NanoVGColor = copy(a = (a * factor).coerceIn(0f, 1f))

    fun lerp(target: NanoVGColor, t: Float): NanoVGColor {
        val factor = t.coerceIn(0f, 1f)
        return NanoVGColor(
            r + (target.r - r) * factor,
            g + (target.g - g) * factor,
            b + (target.b - b) * factor,
            a + (target.a - a) * factor
        )
    }

    fun lighten(amount: Float): NanoVGColor = NanoVGColor(
        (r + amount).coerceIn(0f, 1f),
        (g + amount).coerceIn(0f, 1f),
        (b + amount).coerceIn(0f, 1f),
        a
    )

    fun darken(amount: Float): NanoVGColor = lighten(-amount)

    companion object {
        val TRANSPARENT = NanoVGColor(0f, 0f, 0f, 0f)
        val WHITE = NanoVGColor(1f, 1f, 1f, 1f)
        val BLACK = NanoVGColor(0f, 0f, 0f, 1f)
        val RED = NanoVGColor(1f, 0f, 0f, 1f)
        val GREEN = NanoVGColor(0f, 1f, 0f, 1f)
        val BLUE = NanoVGColor(0f, 0f, 1f, 1f)
        val CYAN = NanoVGColor(0f, 1f, 1f, 1f)
        val MAGENTA = NanoVGColor(1f, 0f, 1f, 1f)
        val YELLOW = NanoVGColor(1f, 1f, 0f, 1f)

        fun fromArgb(argb: Int): NanoVGColor {
            val a = ((argb ushr 24) and 0xFF) / 255f
            val r = ((argb ushr 16) and 0xFF) / 255f
            val g = ((argb ushr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            return NanoVGColor(r, g, b, a)
        }

        fun fromHex(hex: String, defaultAlpha: Float = 1.0f): NanoVGColor {
            val cleanHex = hex.trim().removePrefix("#")
            return when (cleanHex.length) {
                6 -> {
                    val rgb = cleanHex.toInt(16)
                    val r = ((rgb ushr 16) and 0xFF) / 255f
                    val g = ((rgb ushr 8) and 0xFF) / 255f
                    val b = (rgb and 0xFF) / 255f
                    NanoVGColor(r, g, b, defaultAlpha)
                }
                8 -> {
                    val argb = cleanHex.toLong(16).toInt()
                    fromArgb(argb)
                }
                else -> WHITE
            }
        }

        fun fromHsv(h: Float, s: Float, v: Float, a: Float = 1.0f): NanoVGColor {
            val hue = (h % 360f + 360f) % 360f
            val sat = s.coerceIn(0f, 1f)
            val value = v.coerceIn(0f, 1f)

            val c = value * sat
            val x = c * (1f - Math.abs((hue / 60f) % 2f - 1f))
            val m = value - c

            var r1 = 0f
            var g1 = 0f
            var b1 = 0f

            when {
                hue < 60f -> { r1 = c; g1 = x; b1 = 0f }
                hue < 120f -> { r1 = x; g1 = c; b1 = 0f }
                hue < 180f -> { r1 = 0f; g1 = c; b1 = x }
                hue < 240f -> { r1 = 0f; g1 = x; b1 = c }
                hue < 300f -> { r1 = x; g1 = 0f; b1 = c }
                else -> { r1 = c; g1 = 0f; b1 = x }
            }

            return NanoVGColor(r1 + m, g1 + m, b1 + m, a)
        }

        fun fromHsl(h: Float, s: Float, l: Float, a: Float = 1.0f): NanoVGColor {
            val hue = (h % 360f + 360f) % 360f / 360f
            val sat = s.coerceIn(0f, 1f)
            val light = l.coerceIn(0f, 1f)

            if (sat == 0f) {
                return NanoVGColor(light, light, light, a)
            }

            val q = if (light < 0.5f) light * (1f + sat) else light + sat - light * sat
            val p = 2f * light - q

            val r = hueToRgb(p, q, hue + 1f / 3f)
            val g = hueToRgb(p, q, hue)
            val b = hueToRgb(p, q, hue - 1f / 3f)

            return NanoVGColor(r, g, b, a)
        }

        private fun hueToRgb(p: Float, q: Float, tInput: Float): Float {
            var t = tInput
            if (t < 0f) t += 1f
            if (t > 1f) t -= 1f
            if (t < 1f / 6f) return p + (q - p) * 6f * t
            if (t < 1f / 2f) return q
            if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6f
            return p
        }
    }
}
