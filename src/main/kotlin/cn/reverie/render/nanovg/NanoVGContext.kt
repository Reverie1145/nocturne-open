package cn.reverie.render.nanovg

import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.nanovg.NanoVGGL3.*

class NanoVGContext(val flags: Int = NanoVGConstants.ANTIALIAS or NanoVGConstants.STENCIL_STROKES) : AutoCloseable {

    var handle: Long = 0L
        private set

    val isValid: Boolean get() = handle != 0L

    val path: NanoVGPath by lazy { NanoVGPath(handle) }
    val transform: NanoVGTransform by lazy { NanoVGTransform(handle) }
    val scissor: NanoVGScissor by lazy { NanoVGScissor(handle) }

    private var inFrame = false
    val isFrameActive: Boolean get() = isValid && inFrame

    fun init(): Boolean {
        if (handle == 0L) {
            handle = nvgCreate(flags)
            if (handle == 0L) {
                System.err.println("[Nocturne-NanoVG] Failed to initialize NanoVG GL3 context!")
                return false
            }
        }
        return true
    }

    fun beginFrame(windowWidth: Float, windowHeight: Float, devicePixelRatio: Float) {
        if (!isValid) init()
        if (isValid && !inFrame) {
            nvgBeginFrame(handle, windowWidth, windowHeight, devicePixelRatio)
            inFrame = true
        }
    }

    fun endFrame() {
        if (isValid && inFrame) {
            nvgEndFrame(handle)
            inFrame = false
        }
    }

    fun cancelFrame() {
        if (isValid && inFrame) {
            nvgCancelFrame(handle)
            inFrame = false
        }
    }

    inline fun draw(block: NanoVGContext.() -> Unit) {
        block()
    }

    override fun close() {
        if (isValid) {
            if (inFrame) cancelFrame()
            nvgDelete(handle)
            handle = 0L
        }
    }
}
