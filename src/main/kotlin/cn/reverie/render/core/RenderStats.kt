package cn.reverie.render.core

object RenderStats {

    var frameCount: Long = 0L
    var drawCalls: Int = 0
    var activeFbos: Int = 0
    var frameTimeMs: Float = 0f

    private var lastTime = System.nanoTime()

    fun beginFrame() {
        drawCalls = 0
        val now = System.nanoTime()
        frameTimeMs = (now - lastTime) / 1_000_000f
        lastTime = now
        frameCount++
    }

    fun recordDrawCall() {
        drawCalls++
    }
}
