package cn.reverie.render.gl

import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer

object GLMemory {

    inline fun <R> stack(block: (MemoryStack) -> R): R {
        return MemoryStack.stackPush().use(block)
    }

    fun allocateDirectByteBuffer(capacity: Int): ByteBuffer {
        return MemoryUtil.memAlloc(capacity)
    }

    fun allocateDirectFloatBuffer(capacity: Int): FloatBuffer {
        return MemoryUtil.memAllocFloat(capacity)
    }

    fun allocateDirectIntBuffer(capacity: Int): IntBuffer {
        return MemoryUtil.memAllocInt(capacity)
    }

    fun free(buffer: ByteBuffer?) {
        if (buffer != null) {
            MemoryUtil.memFree(buffer)
        }
    }

    fun free(buffer: FloatBuffer?) {
        if (buffer != null) {
            MemoryUtil.memFree(buffer)
        }
    }

    fun free(buffer: IntBuffer?) {
        if (buffer != null) {
            MemoryUtil.memFree(buffer)
        }
    }
}
