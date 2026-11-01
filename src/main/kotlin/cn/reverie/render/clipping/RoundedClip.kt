package cn.reverie.render.clipping

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.CornerRadii
import cn.reverie.render.draw.RoundedRect
import cn.reverie.render.math.Rect2f

object RoundedClip {

    inline fun withRoundedClip(
        rect: Rect2f,
        radii: CornerRadii,
        block: () -> Unit
    ) {
        StencilClip.pushMask {
            RoundedRect.draw(RenderEngine.nanoVG.handle, rect.x, rect.y, rect.width, rect.height, radii, cn.reverie.render.nanovg.NanoVGColor.WHITE)
        }
        try {
            block()
        } finally {
            StencilClip.popMask()
        }
    }
}
