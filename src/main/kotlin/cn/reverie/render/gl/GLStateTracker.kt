package cn.reverie.render.gl

import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL14C.*
import org.lwjgl.opengl.GL20C.*
import org.lwjgl.opengl.GL21C.*
import org.lwjgl.opengl.GL30C.*
import org.lwjgl.opengl.GL33C.*
import org.lwjgl.system.MemoryStack

class GLStateTracker {

    private var program = 0
    private var vao = 0
    private var vbo = 0
    private var ebo = 0
    private var fbo = 0
    private var pixelUnpackBuffer = 0
    private var pixelPackBuffer = 0
    private var unpackAlignment = 4
    private var unpackRowLength = 0
    private var unpackSkipPixels = 0
    private var unpackSkipRows = 0

    private var activeTexture = GL_TEXTURE0
    private val textureBindings = IntArray(8)
    private val samplerBindings = IntArray(8)

    private var blend = false
    private var blendSrcRgb = 0
    private var blendDstRgb = 0
    private var blendSrcAlpha = 0
    private var blendDstAlpha = 0
    private var blendEqRgb = 0
    private var blendEqAlpha = 0

    private var depthTest = false
    private var depthMask = false
    private var depthFunc = 0

    private var cullFace = false
    private var cullFaceMode = 0

    private var scissorTest = false
    private val scissorBox = IntArray(4)

    private var stencilTest = false
    private var stencilMask = 0
    private var stencilFunc = 0
    private var stencilRef = 0
    private var stencilValueMask = 0
    private var stencilFail = 0
    private var stencilZFail = 0
    private var stencilZPass = 0

    private val viewport = IntArray(4)
    private val colorMask = BooleanArray(4)

    fun save() {
        MemoryStack.stackPush().use { stack ->
            val intBuffer = stack.mallocInt(4)
            val byteBuffer = stack.malloc(4)

            glGetIntegerv(GL_CURRENT_PROGRAM, intBuffer)
            program = intBuffer.get(0)

            glGetIntegerv(GL_VERTEX_ARRAY_BINDING, intBuffer)
            vao = intBuffer.get(0)

            glGetIntegerv(GL_ARRAY_BUFFER_BINDING, intBuffer)
            vbo = intBuffer.get(0)

            glGetIntegerv(GL_ELEMENT_ARRAY_BUFFER_BINDING, intBuffer)
            ebo = intBuffer.get(0)

            glGetIntegerv(GL_FRAMEBUFFER_BINDING, intBuffer)
            fbo = intBuffer.get(0)

            glGetIntegerv(GL_PIXEL_UNPACK_BUFFER_BINDING, intBuffer)
            pixelUnpackBuffer = intBuffer.get(0)

            glGetIntegerv(GL_PIXEL_PACK_BUFFER_BINDING, intBuffer)
            pixelPackBuffer = intBuffer.get(0)

            glGetIntegerv(GL_UNPACK_ALIGNMENT, intBuffer)
            unpackAlignment = intBuffer.get(0)

            glGetIntegerv(GL_UNPACK_ROW_LENGTH, intBuffer)
            unpackRowLength = intBuffer.get(0)

            glGetIntegerv(GL_UNPACK_SKIP_PIXELS, intBuffer)
            unpackSkipPixels = intBuffer.get(0)

            glGetIntegerv(GL_UNPACK_SKIP_ROWS, intBuffer)
            unpackSkipRows = intBuffer.get(0)

            glGetIntegerv(GL_ACTIVE_TEXTURE, intBuffer)
            activeTexture = intBuffer.get(0)

            for (i in 0 until 8) {
                glActiveTexture(GL_TEXTURE0 + i)
                glGetIntegerv(GL_TEXTURE_BINDING_2D, intBuffer)
                textureBindings[i] = intBuffer.get(0)

                glGetIntegerv(GL_SAMPLER_BINDING, intBuffer)
                samplerBindings[i] = intBuffer.get(0)

                glBindSampler(i, 0)
            }
            glActiveTexture(activeTexture)

            blend = glIsEnabled(GL_BLEND)
            glGetIntegerv(GL_BLEND_SRC_RGB, intBuffer)
            blendSrcRgb = intBuffer.get(0)
            glGetIntegerv(GL_BLEND_DST_RGB, intBuffer)
            blendDstRgb = intBuffer.get(0)
            glGetIntegerv(GL_BLEND_SRC_ALPHA, intBuffer)
            blendSrcAlpha = intBuffer.get(0)
            glGetIntegerv(GL_BLEND_DST_ALPHA, intBuffer)
            blendDstAlpha = intBuffer.get(0)
            glGetIntegerv(GL_BLEND_EQUATION_RGB, intBuffer)
            blendEqRgb = intBuffer.get(0)
            glGetIntegerv(GL_BLEND_EQUATION_ALPHA, intBuffer)
            blendEqAlpha = intBuffer.get(0)

            depthTest = glIsEnabled(GL_DEPTH_TEST)
            glGetBooleanv(GL_DEPTH_WRITEMASK, byteBuffer)
            depthMask = byteBuffer.get(0).toInt() != 0
            glGetIntegerv(GL_DEPTH_FUNC, intBuffer)
            depthFunc = intBuffer.get(0)

            cullFace = glIsEnabled(GL_CULL_FACE)
            glGetIntegerv(GL_CULL_FACE_MODE, intBuffer)
            cullFaceMode = intBuffer.get(0)

            scissorTest = glIsEnabled(GL_SCISSOR_TEST)
            glGetIntegerv(GL_SCISSOR_BOX, scissorBox)

            stencilTest = glIsEnabled(GL_STENCIL_TEST)
            glGetIntegerv(GL_STENCIL_WRITEMASK, intBuffer)
            stencilMask = intBuffer.get(0)
            glGetIntegerv(GL_STENCIL_FUNC, intBuffer)
            stencilFunc = intBuffer.get(0)
            glGetIntegerv(GL_STENCIL_REF, intBuffer)
            stencilRef = intBuffer.get(0)
            glGetIntegerv(GL_STENCIL_VALUE_MASK, intBuffer)
            stencilValueMask = intBuffer.get(0)
            glGetIntegerv(GL_STENCIL_FAIL, intBuffer)
            stencilFail = intBuffer.get(0)
            glGetIntegerv(GL_STENCIL_PASS_DEPTH_FAIL, intBuffer)
            stencilZFail = intBuffer.get(0)
            glGetIntegerv(GL_STENCIL_PASS_DEPTH_PASS, intBuffer)
            stencilZPass = intBuffer.get(0)

            glGetIntegerv(GL_VIEWPORT, viewport)
            glGetBooleanv(GL_COLOR_WRITEMASK, byteBuffer)
            colorMask[0] = byteBuffer.get(0).toInt() != 0
            colorMask[1] = byteBuffer.get(1).toInt() != 0
            colorMask[2] = byteBuffer.get(2).toInt() != 0
            colorMask[3] = byteBuffer.get(3).toInt() != 0

            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0)
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0)
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0)
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0)
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0)
        }
    }

    fun restore() {

        glUseProgram(program)
        glBindVertexArray(vao)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo)
        glBindFramebuffer(GL_FRAMEBUFFER, fbo)
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pixelUnpackBuffer)
        glBindBuffer(GL_PIXEL_PACK_BUFFER, pixelPackBuffer)

        glPixelStorei(GL_UNPACK_ALIGNMENT, unpackAlignment)
        glPixelStorei(GL_UNPACK_ROW_LENGTH, unpackRowLength)
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, unpackSkipPixels)
        glPixelStorei(GL_UNPACK_SKIP_ROWS, unpackSkipRows)

        for (i in 0 until 8) {
            glActiveTexture(GL_TEXTURE0 + i)
            glBindTexture(GL_TEXTURE_2D, textureBindings[i])
            glBindSampler(i, samplerBindings[i])
        }
        glActiveTexture(activeTexture)

        if (blend) glEnable(GL_BLEND) else glDisable(GL_BLEND)
        glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha)
        glBlendEquationSeparate(blendEqRgb, blendEqAlpha)

        if (depthTest) glEnable(GL_DEPTH_TEST) else glDisable(GL_DEPTH_TEST)
        glDepthMask(depthMask)
        glDepthFunc(depthFunc)

        if (cullFace) glEnable(GL_CULL_FACE) else glDisable(GL_CULL_FACE)
        glCullFace(cullFaceMode)

        if (scissorTest) glEnable(GL_SCISSOR_TEST) else glDisable(GL_SCISSOR_TEST)
        glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3])

        if (stencilTest) glEnable(GL_STENCIL_TEST) else glDisable(GL_STENCIL_TEST)
        glStencilMask(stencilMask)
        glStencilFunc(stencilFunc, stencilRef, stencilValueMask)
        glStencilOp(stencilFail, stencilZFail, stencilZPass)

        glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
        glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3])
    }
}
