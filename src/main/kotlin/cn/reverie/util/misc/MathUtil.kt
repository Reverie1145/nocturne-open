package cn.reverie.util.misc

import java.security.SecureRandom

object MathUtil {
    private val random = SecureRandom()

    fun getRandomInRange(min: Float, max: Float): Float {
        return random.nextFloat() * (max - min) + min
    }

    fun getRandomInRange(min: Long, max: Long): Long {
        return (random.nextFloat() * (max - min) + min).toLong()
    }

    fun clamp(value: Float, min: Float, max: Float): Float {
        return Math.max(min, Math.min(max, value))
    }

    fun randomDouble(min: Double, max: Double): Double {
        return random.nextDouble() * (max - min) + min
    }

    fun randomFloat(min: Float, max: Float): Float {
        return random.nextFloat() * (max - min) + min
    }
}
