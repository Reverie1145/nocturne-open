package cn.reverie.render.animation

import kotlin.math.abs

class SpringAnimation(
    var targetValue: Float = 0f,
    var mass: Float = 1.0f,
    var stiffness: Float = 180.0f,
    var damping: Float = 18.0f
) {
    var value: Float = targetValue
    var velocity: Float = 0f

    var isSettled: Boolean = true
        private set

    fun update(deltaSeconds: Float = 1f / 60f) {
        if (isSettled) return

        val displacement = value - targetValue
        val springForce = -stiffness * displacement
        val dampingForce = -damping * velocity
        val acceleration = (springForce + dampingForce) / mass

        velocity += acceleration * deltaSeconds
        value += velocity * deltaSeconds

        if (abs(velocity) < 0.001f && abs(value - targetValue) < 0.001f) {
            value = targetValue
            velocity = 0f
            isSettled = true
        }
    }

    fun snapTo(newValue: Float) {
        value = newValue
        targetValue = newValue
        velocity = 0f
        isSettled = true
    }

    fun animateTo(newTarget: Float) {
        if (targetValue != newTarget) {
            targetValue = newTarget
            isSettled = false
        }
    }
}
