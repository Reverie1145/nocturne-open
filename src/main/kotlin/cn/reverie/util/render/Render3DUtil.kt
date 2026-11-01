package cn.reverie.util.render

import cn.reverie.render.core.ResolutionHelper
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

object Render3DUtil {

    @JvmStatic
    var lastCameraPos: Vec3 = Vec3.ZERO
        private set

    @JvmStatic
    var lastViewMatrix = Matrix4f()
        private set

    @JvmStatic
    var lastProjectionMatrix = Matrix4f()
        private set

    @JvmStatic
    var lastCameraYaw: Float = 0f
        private set

    @JvmStatic
    var lastCameraPitch: Float = 0f
        private set

    @JvmStatic
    fun capture(state: CameraRenderState) {
        lastCameraPos = state.pos
        lastViewMatrix = Matrix4f(state.viewRotationMatrix)
        lastProjectionMatrix = Matrix4f(state.projectionMatrix)
        lastCameraYaw = state.yRot
        lastCameraPitch = state.xRot
    }

    @JvmStatic
    fun worldToClip(x: Double, y: Double, z: Double): Vector4f {
        val clip = Vector4f(
            (x - lastCameraPos.x).toFloat(),
            (y - lastCameraPos.y).toFloat(),
            (z - lastCameraPos.z).toFloat(),
            1.0f
        )
        lastViewMatrix.transform(clip)
        lastProjectionMatrix.transform(clip)
        return clip
    }

    @JvmStatic
    fun clipToScreen(c: Vector4f, sw: Float = ResolutionHelper.scaledWidth, sh: Float = ResolutionHelper.scaledHeight): Vector2f {
        val invW = 1.0f / c.w
        val ndcX = c.x * invW
        val ndcY = c.y * invW
        val sx = ((ndcX + 1.0f) * 0.5f) * sw
        val sy = ((1.0f - ndcY) * 0.5f) * sh
        return Vector2f(sx, sy)
    }

    @JvmStatic
    fun worldToNdc(x: Double, y: Double, z: Double): Vector3f? {
        val clip = worldToClip(x, y, z)
        if (clip.w <= 0.05f) {
            return null
        }
        val ndcX = clip.x / clip.w
        val ndcY = clip.y / clip.w
        val ndcZ = clip.z / clip.w
        return Vector3f(ndcX, ndcY, ndcZ)
    }

    @JvmStatic
    fun projectToScreen(x: Double, y: Double, z: Double): Vector3f? {
        val clip = worldToClip(x, y, z)
        if (clip.w <= 0.05f) {
            return null
        }
        val p = clipToScreen(clip)
        return Vector3f(p.x, p.y, clip.w)
    }

    @JvmStatic
    fun worldToScreen(x: Double, y: Double, z: Double): Vector3f? {
        return projectToScreen(x, y, z)
    }
}
