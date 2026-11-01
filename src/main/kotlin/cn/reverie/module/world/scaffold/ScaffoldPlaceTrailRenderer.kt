package cn.reverie.module.world.scaffold

import cn.reverie.module.world.*

import cn.reverie.render.hud.HUDModule
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.LineRenderer
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.util.render.Render3DUtil
import net.minecraft.client.Minecraft
import org.joml.Vector2f
import org.joml.Vector4f
import kotlin.math.min
import kotlin.math.sin
    internal fun Scaffold.renderTrail() {
        if (!enabled || !renderPlace.value) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.level == null) return
        if (placeTrail.isEmpty()) return

        val now = System.currentTimeMillis()
        val delta = if (lastRenderTime == 0L) 16L else min(64L, now - lastRenderTime)
        lastRenderTime = now
        val dt = delta / 1000.0

        val appearMs = 150.0
        val holdMs = 120.0
        val fadeMs = trailLength.value * 1000.0

        if (breath.value) breathPhase += dt * 2.4

        val baseColor = if (useThemeColor.value) HUDModule.sampleColor(0f) else placeColor.sampleColor(0f)
        val baseScale = placeScale.getFloat()
        val sw = ResolutionHelper.scaledWidth
        val sh = ResolutionHelper.scaledHeight

        val edges = arrayOf(
            intArrayOf(0, 1), intArrayOf(1, 3), intArrayOf(3, 2), intArrayOf(2, 0),
            intArrayOf(4, 5), intArrayOf(5, 7), intArrayOf(7, 6), intArrayOf(6, 4),
            intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7)
        )

        placeTrail.entries.removeIf { e ->
            val since = now - e.value[1]
            since > holdMs + fadeMs + 50
        }

        for (entry in placeTrail.entries) {
            val pos = entry.key
            val born = entry.value[0]
            val last = entry.value[1]

            val ageBorn = (now - born).toDouble()
            val sinceLast = (now - last).toDouble()

            var growP = min(1.0, ageBorn / appearMs)
            growP = 1.0 - Math.pow(1.0 - growP, 4.0)

            val fadeP: Double = if (sinceLast <= holdMs) {
                1.0
            } else {
                val f = min(1.0, (sinceLast - holdMs) / fadeMs)
                1.0 - f
            }
            if (fadeP <= 0.005) continue

            var bScale = 1.0f
            var bAlpha = 1.0f
            if (breath.value) {
                val phaseOff = (pos.x xor pos.y xor pos.z) and 7
                val s = sin(breathPhase + phaseOff * 0.6).toFloat()
                bScale = 1.0f + 0.025f * s
                bAlpha = 0.85f + 0.15f * (0.5f + 0.5f * s)
            }

            val half = (growP * baseScale * bScale * 0.5).toFloat()
            if (half < 0.001f) continue

            val cx = pos.x + 0.5
            val cy = pos.y + 0.5
            val cz = pos.z + 0.5

            val finalAlpha = (baseColor.a * fadeP.toFloat() * bAlpha).coerceIn(0.0f, 1.0f)
            val lineCol = NanoVGColor(baseColor.r, baseColor.g, baseColor.b, finalAlpha)
            val shadowCol = NanoVGColor(0.0f, 0.0f, 0.0f, finalAlpha * 0.6f)

            drawWireframeBox(cx, cy, cz, half, edges, sw, sh, lineCol, shadowCol, 1.6f, 3.0f)

            val ih = half * 0.5f
            val innerCol = NanoVGColor(
                min(1.0f, baseColor.r + 0.15f),
                min(1.0f, baseColor.g + 0.15f),
                min(1.0f, baseColor.b + 0.15f),
                finalAlpha * 0.55f
            )
            drawWireframeBox(cx, cy, cz, ih, edges, sw, sh, innerCol, null, 1.0f, 0f)
        }
    }

    internal fun Scaffold.drawWireframeBox(
        cx: Double, cy: Double, cz: Double,
        half: Float,
        edges: Array<IntArray>,
        sw: Float, sh: Float,
        mainColor: NanoVGColor,
        shadowColor: NanoVGColor?,
        mainWidth: Float,
        shadowWidth: Float = 0f
    ) {
        val corners3D = arrayOf(
            doubleArrayOf(cx - half, cy - half, cz - half),
            doubleArrayOf(cx - half, cy - half, cz + half),
            doubleArrayOf(cx - half, cy + half, cz - half),
            doubleArrayOf(cx - half, cy + half, cz + half),
            doubleArrayOf(cx + half, cy - half, cz - half),
            doubleArrayOf(cx + half, cy - half, cz + half),
            doubleArrayOf(cx + half, cy + half, cz - half),
            doubleArrayOf(cx + half, cy + half, cz + half)
        )

        val clips = Array(8) { i ->
            Render3DUtil.worldToClip(corners3D[i][0], corners3D[i][1], corners3D[i][2])
        }

        val ctx = RenderEngine.nanoVG.handle
        val near = 0.05f

        for (edge in edges) {
            val c1 = clips[edge[0]]
            val c2 = clips[edge[1]]
            val w1 = c1.w
            val w2 = c2.w

            if (w1 <= near && w2 <= near) continue

            val p1: Vector2f
            val p2: Vector2f

            if (w1 > near && w2 > near) {
                p1 = Render3DUtil.clipToScreen(c1, sw, sh)
                p2 = Render3DUtil.clipToScreen(c2, sw, sh)
            } else {
                val t = (near - w1) / (w2 - w1)
                val cutX = c1.x + t * (c2.x - c1.x)
                val cutY = c1.y + t * (c2.y - c1.y)
                val cutClip = Vector4f(cutX, cutY, 0f, near)
                val cutScreen = Render3DUtil.clipToScreen(cutClip, sw, sh)
                if (w1 > near) {
                    p1 = Render3DUtil.clipToScreen(c1, sw, sh)
                    p2 = cutScreen
                } else {
                    p1 = cutScreen
                    p2 = Render3DUtil.clipToScreen(c2, sw, sh)
                }
            }

            if (shadowColor != null && shadowWidth > 0f) {
                LineRenderer.drawLine(ctx, p1.x, p1.y, p2.x, p2.y, shadowColor, shadowWidth)
            }
            LineRenderer.drawLine(ctx, p1.x, p1.y, p2.x, p2.y, mainColor, mainWidth)
        }
    }
