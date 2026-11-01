package cn.reverie.render.clipping

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.CornerRadii
import cn.reverie.render.math.Rect2f

class ClipScope {

    inline fun scissor(rect: Rect2f, block: () -> Unit) {
        RenderEngine.nanoVG.scissor.withScissor(rect, block)
    }

    inline fun rounded(rect: Rect2f, radius: Float, block: () -> Unit) {
        RoundedClip.withRoundedClip(rect, CornerRadii(radius), block)
    }

    inline fun rounded(rect: Rect2f, radii: CornerRadii, block: () -> Unit) {
        RoundedClip.withRoundedClip(rect, radii, block)
    }

    inline fun polygon(centerX: Float, centerY: Float, radius: Float, sides: Int, block: () -> Unit) {
        PolygonClip.withPolygonClip(centerX, centerY, radius, sides, 0f, block)
    }
}
