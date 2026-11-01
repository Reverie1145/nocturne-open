package cn.reverie.render.shader

import cn.reverie.render.framebuffer.FBO

interface PostPass : AutoCloseable {
    val name: String
    var isEnabled: Boolean

    fun render(input: FBO, output: FBO)
}
