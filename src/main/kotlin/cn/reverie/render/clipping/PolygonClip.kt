package cn.reverie.render.clipping

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.PolygonRenderer
import cn.reverie.render.math.Vec2f
import cn.reverie.render.nanovg.NanoVGColor

object PolygonClip {

    inline fun withPolygonClip(
        centerX: Float, centerY: Float,
        radius: Float, sides: Int,
        rotationRad: Float = 0f,
        block: () -> Unit
    ) {
        StencilClip.pushMask {
            PolygonRenderer.drawRegularPolygon(
                RenderEngine.nanoVG.handle,
                centerX, centerY,
                radius, sides,
                NanoVGColor.WHITE,
                rotationRad
            )
        }
        try {
            block()
        } finally {
            StencilClip.popMask()
        }
    }
}
