package cn.reverie.render.nanovg

import cn.reverie.render.math.Rect2f
import org.lwjgl.nanovg.NanoVG.*
import java.util.ArrayDeque

class NanoVGScissor(val context: Long) {

    private val stack = ArrayDeque<Rect2f>()

    fun push(x: Float, y: Float, w: Float, h: Float) {
        val newRect = Rect2f(x, y, w, h)
        if (stack.isEmpty()) {
            stack.push(newRect)
            nvgScissor(context, x, y, w, h)
        } else {
            val current = stack.peek()
            val intersected = current.intersect(newRect)
            stack.push(intersected)
            nvgIntersectScissor(context, x, y, w, h)
        }
    }

    fun push(rect: Rect2f) = push(rect.x, rect.y, rect.width, rect.height)

    fun pop() {
        if (stack.isNotEmpty()) {
            stack.pop()
            if (stack.isEmpty()) {
                nvgResetScissor(context)
            } else {
                val current = stack.peek()
                nvgResetScissor(context)
                nvgScissor(context, current.x, current.y, current.width, current.height)
            }
        }
    }

    fun reset() {
        stack.clear()
        nvgResetScissor(context)
    }

    inline fun <R> withScissor(x: Float, y: Float, w: Float, h: Float, block: () -> R): R {
        push(x, y, w, h)
        return try {
            block()
        } finally {
            pop()
        }
    }

    inline fun <R> withScissor(rect: Rect2f, block: () -> R): R = withScissor(rect.x, rect.y, rect.width, rect.height, block)
}
