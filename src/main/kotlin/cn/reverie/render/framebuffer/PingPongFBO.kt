package cn.reverie.render.framebuffer

class PingPongFBO(
    var width: Int,
    var height: Int,
    format: FBOAttachment = FBOAttachment.RGBA8
) : AutoCloseable {

    var primary: FBO = FBO(width, height, format, hasDepthStencil = false)
        private set
    var secondary: FBO = FBO(width, height, format, hasDepthStencil = false)
        private set

    private var swapped = false

    val readTarget: FBO get() = if (swapped) secondary else primary
    val writeTarget: FBO get() = if (swapped) primary else secondary

    fun swap() {
        swapped = !swapped
    }

    fun reset() {
        swapped = false
    }

    fun resize(newWidth: Int, newHeight: Int) {
        if (newWidth <= 0 || newHeight <= 0) return
        if (newWidth == width && newHeight == height) return

        width = newWidth
        height = newHeight
        primary.resize(newWidth, newHeight)
        secondary.resize(newWidth, newHeight)
    }

    override fun close() {
        primary.close()
        secondary.close()
    }
}
