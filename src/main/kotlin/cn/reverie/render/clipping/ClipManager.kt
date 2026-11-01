package cn.reverie.render.clipping

import cn.reverie.render.draw.CornerRadii
import cn.reverie.render.math.Rect2f

object ClipManager {

    val glScissorStack = ScissorStack()
    val scope = ClipScope()

    inline fun clip(block: ClipScope.() -> Unit) {
        scope.block()
    }
}
