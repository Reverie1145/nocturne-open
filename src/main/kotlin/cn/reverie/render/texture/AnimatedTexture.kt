package cn.reverie.render.texture

class AnimatedTexture(
    val frameTextureIds: List<Int>,
    val frameDurationMs: Long = 100L
) {
    private var currentFrameIndex = 0
    private var lastFrameTime = System.currentTimeMillis()

    val currentTextureId: Int
        get() {
            update()
            return if (frameTextureIds.isNotEmpty()) frameTextureIds[currentFrameIndex] else 0
        }

    fun update() {
        if (frameTextureIds.size <= 1) return
        val now = System.currentTimeMillis()
        if (now - lastFrameTime >= frameDurationMs) {
            currentFrameIndex = (currentFrameIndex + 1) % frameTextureIds.size
            lastFrameTime = now
        }
    }

    fun reset() {
        currentFrameIndex = 0
        lastFrameTime = System.currentTimeMillis()
    }
}
