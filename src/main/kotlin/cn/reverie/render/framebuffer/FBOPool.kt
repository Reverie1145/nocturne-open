package cn.reverie.render.framebuffer

import java.util.concurrent.ConcurrentLinkedQueue

object FBOPool : AutoCloseable {

    private val pool = ConcurrentLinkedQueue<FBO>()

    fun acquire(width: Int, height: Int, format: FBOAttachment = FBOAttachment.RGBA8): FBO {
        var fbo = pool.poll()
        if (fbo == null || !fbo.isValid) {
            fbo = FBO(width, height, format, hasDepthStencil = false)
        } else {
            fbo.resize(width, height)
        }
        return fbo
    }

    fun release(fbo: FBO?) {
        if (fbo != null && fbo.isValid) {
            pool.offer(fbo)
        }
    }

    inline fun <R> withFBO(width: Int, height: Int, format: FBOAttachment = FBOAttachment.RGBA8, block: (FBO) -> R): R {
        val fbo = acquire(width, height, format)
        return try {
            block(fbo)
        } finally {
            release(fbo)
        }
    }

    override fun close() {
        while (pool.isNotEmpty()) {
            pool.poll()?.close()
        }
    }
}
