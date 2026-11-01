package cn.reverie.render.shader

import cn.reverie.render.framebuffer.FBO
import cn.reverie.render.framebuffer.PingPongFBO

class PostProcessingPipeline : AutoCloseable {

    private val passes = ArrayList<PostPass>()

    fun addPass(pass: PostPass): PostProcessingPipeline {
        passes.add(pass)
        return this
    }

    fun removePass(pass: PostPass): Boolean = passes.remove(pass)

    fun clearPasses() {
        passes.forEach { it.close() }
        passes.clear()
    }

    fun process(input: FBO, output: FBO, pingPong: PingPongFBO) {
        val activePasses = passes.filter { it.isEnabled }
        if (activePasses.isEmpty()) {
            input.blitTo(output)
            return
        }

        var currentInput = input
        for (i in activePasses.indices) {
            val pass = activePasses[i]
            val isLast = (i == activePasses.lastIndex)
            val currentOutput = if (isLast) output else pingPong.writeTarget

            pass.render(currentInput, currentOutput)

            if (!isLast) {
                pingPong.swap()
                currentInput = pingPong.readTarget
            }
        }
    }

    override fun close() {
        clearPasses()
    }
}
