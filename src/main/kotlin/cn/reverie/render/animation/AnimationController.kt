package cn.reverie.render.animation

import java.util.concurrent.CopyOnWriteArrayList

object AnimationController {

    private val activeAnimations = CopyOnWriteArrayList<Animation>()
    private val activeSprings = CopyOnWriteArrayList<SpringAnimation>()

    fun register(animation: Animation) {
        if (!activeAnimations.contains(animation)) {
            activeAnimations.add(animation)
        }
    }

    fun register(spring: SpringAnimation) {
        if (!activeSprings.contains(spring)) {
            activeSprings.add(spring)
        }
    }

    fun unregister(animation: Animation) {
        activeAnimations.remove(animation)
    }

    fun unregister(spring: SpringAnimation) {
        activeSprings.remove(spring)
    }

    fun update(deltaSeconds: Float = 1f / 60f) {
        for (anim in activeAnimations) {
            anim.update()
            if (!anim.isRunning) {
                activeAnimations.remove(anim)
            }
        }

        for (spring in activeSprings) {
            spring.update(deltaSeconds)
            if (spring.isSettled) {
                activeSprings.remove(spring)
            }
        }
    }
}
