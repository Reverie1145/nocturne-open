package cn.reverie.render.core

import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGContext

class RenderContext2D(val nanoVG: NanoVGContext) {

    val draw: Draw2D = Draw2D
    val font get() = RenderEngine.fontRenderer

    inline fun withMatrix(block: () -> Unit) {
        nanoVG.transform.withTransform(block)
    }

    inline fun withScissor(x: Float, y: Float, w: Float, h: Float, block: () -> Unit) {
        nanoVG.scissor.withScissor(x, y, w, h, block)
    }
}
