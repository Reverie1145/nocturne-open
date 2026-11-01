package cn.reverie.render.clipping

import cn.reverie.render.math.Rect2f
import org.lwjgl.opengl.GL11C.*
import java.util.ArrayDeque

class ScissorStack {

    private val stack = ArrayDeque<Rect2f>()

    fun push(rect: Rect2f) {
        val newRect = if (stack.isEmpty()) {
            rect
        } else {
            stack.peek().intersect(rect)
        }
        stack.push(newRect)
        apply(newRect)
    }

    fun pop() {
        if (stack.isNotEmpty()) {
            stack.pop()
            if (stack.isEmpty()) {
                glDisable(GL_SCISSOR_TEST)
            } else {
                apply(stack.peek())
            }
        }
    }

    private fun apply(rect: Rect2f) {
        val box = ScissorBox.fromGuiRect(rect)
        glEnable(GL_SCISSOR_TEST)
        glScissor(box.x, box.y, box.width, box.height)
    }

    fun clear() {
        stack.clear()
        glDisable(GL_SCISSOR_TEST)
    }
}
