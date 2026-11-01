package cn.reverie.render.animation

import kotlin.math.*

object Easing {

    val LINEAR: (Float) -> Float = { it }

    val EASE_IN_QUAD: (Float) -> Float = { it * it }
    val EASE_OUT_QUAD: (Float) -> Float = { it * (2f - it) }
    val EASE_IN_OUT_QUAD: (Float) -> Float = { if (it < 0.5f) 2f * it * it else -1f + (4f - 2f * it) * it }

    val EASE_IN_CUBIC: (Float) -> Float = { it * it * it }
    val EASE_OUT_CUBIC: (Float) -> Float = { val t = it - 1f; t * t * t + 1f }
    val EASE_IN_OUT_CUBIC: (Float) -> Float = { if (it < 0.5f) 4f * it * it * it else (it - 1f) * (2f * it - 2f) * (2f * it - 2f) + 1f }

    val EASE_IN_QUART: (Float) -> Float = { it * it * it * it }
    val EASE_OUT_QUART: (Float) -> Float = { val t = it - 1f; 1f - t * t * t * t }
    val EASE_IN_OUT_QUART: (Float) -> Float = { if (it < 0.5f) 8f * it * it * it * it else { val t = it - 1f; 1f - 8f * t * t * t * t } }

    val EASE_IN_QUINT: (Float) -> Float = { it * it * it * it * it }
    val EASE_OUT_QUINT: (Float) -> Float = { val t = it - 1f; 1f + t * t * t * t * t }

    val EASE_IN_SINE: (Float) -> Float = { 1f - cos((it * Math.PI / 2.0).toFloat()) }
    val EASE_OUT_SINE: (Float) -> Float = { sin((it * Math.PI / 2.0).toFloat()) }
    val EASE_IN_OUT_SINE: (Float) -> Float = { -(cos(Math.PI.toFloat() * it) - 1f) / 2f }

    val EASE_IN_EXPO: (Float) -> Float = { if (it == 0f) 0f else 2.0.pow(10.0 * (it - 1.0)).toFloat() }
    val EASE_OUT_EXPO: (Float) -> Float = { if (it == 1f) 1f else 1f - 2.0.pow(-10.0 * it).toFloat() }
    val EASE_IN_OUT_EXPO: (Float) -> Float = {
        when {
            it == 0f -> 0f
            it == 1f -> 1f
            it < 0.5f -> (2.0.pow(20.0 * it - 10.0) / 2.0).toFloat()
            else -> ((2.0 - 2.0.pow(-20.0 * it + 10.0)) / 2.0).toFloat()
        }
    }

    val EASE_IN_BACK: (Float) -> Float = { val c1 = 1.70158f; val c3 = c1 + 1f; c3 * it * it * it - c1 * it * it }
    val EASE_OUT_BACK: (Float) -> Float = { val c1 = 1.70158f; val c3 = c1 + 1f; 1f + c3 * (it - 1f).pow(3) + c1 * (it - 1f).pow(2) }

    val EASE_OUT_BOUNCE: (Float) -> Float = {
        var t = it
        val n1 = 7.5625f
        val d1 = 2.75f
        when {
            t < 1f / d1 -> n1 * t * t
            t < 2f / d1 -> { t -= 1.5f / d1; n1 * t * t + 0.75f }
            t < 2.5f / d1 -> { t -= 2.25f / d1; n1 * t * t + 0.9375f }
            else -> { t -= 2.625f / d1; n1 * t * t + 0.984375f }
        }
    }
}
