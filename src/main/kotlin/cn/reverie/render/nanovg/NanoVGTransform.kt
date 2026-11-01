package cn.reverie.render.nanovg

import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.system.MemoryStack
import java.nio.FloatBuffer

class NanoVGTransform(val context: Long) {

    fun save(): NanoVGTransform {
        nvgSave(context)
        return this
    }

    fun restore(): NanoVGTransform {
        nvgRestore(context)
        return this
    }

    fun reset(): NanoVGTransform {
        nvgResetTransform(context)
        return this
    }

    fun translate(x: Float, y: Float): NanoVGTransform {
        nvgTranslate(context, x, y)
        return this
    }

    fun scale(sx: Float, sy: Float): NanoVGTransform {
        nvgScale(context, sx, sy)
        return this
    }

    fun scale(s: Float): NanoVGTransform = scale(s, s)

    fun rotate(radians: Float): NanoVGTransform {
        nvgRotate(context, radians)
        return this
    }

    fun rotateDegrees(degrees: Float): NanoVGTransform {
        nvgRotate(context, Math.toRadians(degrees.toDouble()).toFloat())
        return this
    }

    fun skewX(radians: Float): NanoVGTransform {
        nvgSkewX(context, radians)
        return this
    }

    fun skewY(radians: Float): NanoVGTransform {
        nvgSkewY(context, radians)
        return this
    }

    fun currentTransform(dest: FloatBuffer) {
        nvgCurrentTransform(context, dest)
    }

    inline fun <R> withTransform(block: () -> R): R {
        save()
        return try {
            block()
        } finally {
            restore()
        }
    }
}
