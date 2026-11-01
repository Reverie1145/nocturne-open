package cn.reverie.render.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object LayerManager {

    private val layers = ConcurrentHashMap<RenderLayer, CopyOnWriteArrayList<() -> Unit>>()

    fun register(layer: RenderLayer, block: () -> Unit) {
        val list = layers.getOrPut(layer) { CopyOnWriteArrayList() }
        list.add(block)
    }

    fun unregister(layer: RenderLayer, block: () -> Unit) {
        layers[layer]?.remove(block)
    }

    fun renderAll() {
        val sortedLayers = RenderLayer.values().sortedBy { it.zIndex }
        for (layer in sortedLayers) {
            val callbacks = layers[layer] ?: continue
            for (callback in callbacks) {
                callback()
            }
        }
    }
}
