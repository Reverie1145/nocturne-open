package cn.reverie.render.core

import java.util.concurrent.CopyOnWriteArrayList

class RenderingPipeline {

    interface StageListener {
        fun onPreRender() {}
        fun onPostRender() {}
    }

    private val listeners = CopyOnWriteArrayList<StageListener>()

    fun addListener(listener: StageListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: StageListener) {
        listeners.remove(listener)
    }

    fun triggerPre() {
        listeners.forEach { it.onPreRender() }
    }

    fun triggerPost() {
        listeners.forEach { it.onPostRender() }
    }
}
