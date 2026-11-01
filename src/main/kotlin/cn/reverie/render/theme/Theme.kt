package cn.reverie.render.theme

import cn.reverie.render.hud.HUDModule
import cn.reverie.render.nanovg.NanoVGColor

interface Theme {

    companion object {
        val BG_COLOR = NanoVGColor(0, 0, 0, 120)
        val SHADOW_COLOR = NanoVGColor(0, 0, 0, 160)

        val LIST_BG = NanoVGColor(0, 0, 0, 90)

        val SEPARATOR = NanoVGColor(128, 128, 128)

        /**
         * 基于平方根的 sRGB Gamma 矫正插值，彻底消除两极色相过渡时的发黑发暗（Muddy Middle）现象。
         */
        @JvmStatic
        fun interpolateGamma(c1: NanoVGColor, c2: NanoVGColor, t: Float): NanoVGColor {
            val factor = t.coerceIn(0f, 1f)
            val inv = 1f - factor
            val r = kotlin.math.sqrt((inv * (c1.r * c1.r) + factor * (c2.r * c2.r)).coerceIn(0f, 1f))
            val g = kotlin.math.sqrt((inv * (c1.g * c1.g) + factor * (c2.g * c2.g)).coerceIn(0f, 1f))
            val b = kotlin.math.sqrt((inv * (c1.b * c1.b) + factor * (c2.b * c2.b)).coerceIn(0f, 1f))
            val a = (c1.a + (c2.a - c1.a) * factor).coerceIn(0f, 1f)
            return NanoVGColor(r, g, b, a)
        }

        @JvmStatic
        fun getClientColor(index: Int, alpha: Float = 1.0f, timeOffset: Long = 0L): NanoVGColor {
            val speed = try { HUDModule.colorSpeed.getFloat() } catch (e: Throwable) { 1.0f }
            val spread = try { HUDModule.colorSpread.getFloat() } catch (e: Throwable) { 1.0f }
            val cycleMs = (3600L / speed.coerceAtLeast(0.1f)).toLong()
            val progressRaw = ((System.currentTimeMillis() + timeOffset + (index * 110L * spread).toLong()) % cycleMs).toFloat() / (cycleMs * 0.5f)
            val waveProgress = if (progressRaw > 1f) 2f - progressRaw else progressRaw
            val smoothT = ((1.0 - kotlin.math.cos(waveProgress.toDouble() * Math.PI)) / 2.0).toFloat()
            return interpolateGamma(HUDModule.getColor1(), HUDModule.getColor2(), smoothT).multiplyAlpha(alpha)
        }

        @JvmStatic
        fun rainbow(speed: Int, index: Int, offset: Float = 0f): NanoVGColor {
            val hudSpeed = try { HUDModule.colorSpeed.getFloat() } catch (e: Throwable) { 1.0f }
            val hudSpread = try { HUDModule.colorSpread.getFloat() } catch (e: Throwable) { 1.0f }
            val cycleMs = (3500L / hudSpeed.coerceAtLeast(0.1f)).toLong()
            val time = ((System.currentTimeMillis() % cycleMs).toFloat() / cycleMs.toFloat() + offset * hudSpread + index * 0.025f * hudSpread) % 1.0f
            val wrapped = if (time < 0f) time + 1.0f else time
            return NanoVGColor.fromHsv(wrapped * 360f, 0.72f, 0.98f)
        }

        @JvmStatic
        fun astolfo(offset: Float = 0f, speed: Float = 1.0f): NanoVGColor {
            val cycleMs = (3200L / speed.coerceAtLeast(0.1f)).toLong()
            var stage = ((System.currentTimeMillis() + (offset * 1200f).toLong()) % cycleMs).toFloat() / (cycleMs * 0.5f)
            if (stage > 1f) stage = 2f - stage
            val smoothStage = ((1.0 - kotlin.math.cos(stage.toDouble() * Math.PI)) / 2.0).toFloat()
            val hue = (185f + smoothStage * 160f) % 360f
            return NanoVGColor.fromHsv(hue, 0.58f, 1.0f)
        }

        @JvmStatic
        fun pulse(color: NanoVGColor, offset: Float = 0f, speed: Float = 1.0f): NanoVGColor {
            val cycleMs = (2400L / speed.coerceAtLeast(0.1f)).toLong()
            val time = ((System.currentTimeMillis() + (offset * 800f).toLong()) % cycleMs).toFloat() / cycleMs.toFloat()
            val factor = ((1.0 + kotlin.math.sin(time.toDouble() * Math.PI * 2.0)) / 2.0).toFloat()
            val lightened = color.lighten(0.15f * factor)
            return lightened.copy(a = (color.a * (0.80f + 0.20f * factor)).coerceIn(0f, 1f))
        }
    }
}
