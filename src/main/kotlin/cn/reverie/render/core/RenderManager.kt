package cn.reverie.render.core

object RenderManager {

    val engine: RenderEngine get() = RenderEngine
    val stats: RenderStats get() = RenderStats
    val resolution: ResolutionHelper get() = ResolutionHelper
    val layers: LayerManager get() = LayerManager

    inline fun render(block: RenderEngine.() -> Unit) {
        RenderEngine.block()
    }
}
