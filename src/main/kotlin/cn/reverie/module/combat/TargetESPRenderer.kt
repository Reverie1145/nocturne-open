package cn.reverie.module.combat

import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.draw.LineRenderer
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.util.render.Render3DUtil
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import org.joml.Vector2f
import org.joml.Vector4f
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin

object TargetESPRenderer {

    private class BoxState(
        var minX: Float,
        var minY: Float,
        var maxX: Float,
        var maxY: Float,
        var alpha: Float,
        var isPrimary: Boolean,
        var lastSeenTime: Long
    )

    private val targetBoxes = HashMap<UUID, BoxState>()
    private var lastFrameTime = 0L
    private var animTime = 0.0

    fun reset() {
        targetBoxes.clear()
        lastFrameTime = 0L
    }

    fun onRender(partialTicks: Float) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (mc.level == null) return

        val sw = ResolutionHelper.scaledWidth
        val sh = ResolutionHelper.scaledHeight
        if (sw <= 0f || sh <= 0f) return

        val now = System.currentTimeMillis()
        val dt = if (lastFrameTime == 0L) 0.016f else ((now - lastFrameTime) / 1000f).coerceIn(0.001f, 0.1f)
        lastFrameTime = now

        val auraActive = KillAura.enabled && KillAura.targetEsp.value
        val bowAimTarget = BowAim.target?.takeIf { BowAim.shouldRotate() && it.isAlive && !it.isRemoved }
        if (!auraActive && bowAimTarget == null) {
            targetBoxes.clear()
            return
        }

        animTime += dt * 1.5

        // Collect all active targets (single or multi-target)
        val activeTargets = LinkedHashSet<LivingEntity>()
        if (bowAimTarget != null) activeTargets.add(bowAimTarget)
        if (auraActive && KillAura.multiTarget.value) {
            val primary = KillAura.target
            if (primary != null && primary.isAlive && !primary.isRemoved) {
                activeTargets.add(primary)
            }
            for (t in KillAura.targets) {
                if (t.isAlive && !t.isRemoved) {
                    activeTargets.add(t)
                }
            }
        } else if (auraActive) {
            val primary = KillAura.target
            if (primary != null && primary.isAlive && !primary.isRemoved) {
                activeTargets.add(primary)
            }
        }

        val primaryTarget = bowAimTarget ?: KillAura.target.takeIf { auraActive }

        // Update active targets
        val seenUuids = HashSet<UUID>()
        for (target in activeTargets) {
            val box = compute2DBoundingBox(target, partialTicks, sw, sh) ?: continue
            val uuid = target.uuid
            seenUuids.add(uuid)

            val isPrimary = (target === primaryTarget)
            val state = targetBoxes[uuid]

            if (state == null) {
                targetBoxes[uuid] = BoxState(
                    minX = box.minX,
                    minY = box.minY,
                    maxX = box.maxX,
                    maxY = box.maxY,
                    alpha = 0.05f,
                    isPrimary = isPrimary,
                    lastSeenTime = now
                )
            } else {
                val lerpFactor = (dt * 18.0f).coerceIn(0f, 1f)
                state.minX += (box.minX - state.minX) * lerpFactor
                state.minY += (box.minY - state.minY) * lerpFactor
                state.maxX += (box.maxX - state.maxX) * lerpFactor
                state.maxY += (box.maxY - state.maxY) * lerpFactor
                state.alpha = (state.alpha + dt * 6.0f).coerceAtMost(1.0f)
                state.isPrimary = isPrimary
                state.lastSeenTime = now
            }
        }

        // Fade out and remove missing targets
        targetBoxes.entries.removeIf { (uuid, state) ->
            if (!seenUuids.contains(uuid)) {
                state.alpha -= dt * 5.0f
            }
            state.alpha <= 0.005f
        }

        if (targetBoxes.isEmpty()) return

        val mode = KillAura.espMode.value

        // Render each target's 2D ESP
        for ((_, state) in targetBoxes) {
            val bw = state.maxX - state.minX
            val bh = state.maxY - state.minY
            if (bw < 2f || bh < 2f) continue

            when (mode) {
                "Corners" -> renderCorners(state, sw, sh)
                "Box" -> renderBox(state, sw, sh)
                else -> renderCorners(state, sw, sh)
            }
        }
    }

    // ==========================================
    // 2D Screen Bounding Box Calculator
    // ==========================================
    private class ScreenBox(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float)

    private fun compute2DBoundingBox(
        entity: LivingEntity,
        partialTicks: Float,
        sw: Float,
        sh: Float
    ): ScreenBox? {
        val interpX = Mth.lerp(partialTicks.toDouble(), entity.xo, entity.x)
        val interpY = Mth.lerp(partialTicks.toDouble(), entity.yo, entity.y)
        val interpZ = Mth.lerp(partialTicks.toDouble(), entity.zo, entity.z)

        val bb = entity.boundingBox
        val hw = (bb.maxX - bb.minX) * 0.5
        val h = bb.maxY - bb.minY
        val hd = (bb.maxZ - bb.minZ) * 0.5

        val corners3D = arrayOf(
            doubleArrayOf(interpX - hw, interpY, interpZ - hd),
            doubleArrayOf(interpX - hw, interpY, interpZ + hd),
            doubleArrayOf(interpX - hw, interpY + h, interpZ - hd),
            doubleArrayOf(interpX - hw, interpY + h, interpZ + hd),
            doubleArrayOf(interpX + hw, interpY, interpZ - hd),
            doubleArrayOf(interpX + hw, interpY, interpZ + hd),
            doubleArrayOf(interpX + hw, interpY + h, interpZ - hd),
            doubleArrayOf(interpX + hw, interpY + h, interpZ + hd)
        )

        val edges = arrayOf(
            intArrayOf(0, 1), intArrayOf(1, 5), intArrayOf(5, 4), intArrayOf(4, 0),
            intArrayOf(2, 3), intArrayOf(3, 7), intArrayOf(7, 6), intArrayOf(6, 2),
            intArrayOf(0, 2), intArrayOf(1, 3), intArrayOf(4, 6), intArrayOf(5, 7)
        )

        val clips = Array(8) { i ->
            Render3DUtil.worldToClip(corners3D[i][0], corners3D[i][1], corners3D[i][2])
        }

        val screenPoints = ArrayList<Vector2f>()
        val near = 0.05f

        for (edge in edges) {
            val c1 = clips[edge[0]]
            val c2 = clips[edge[1]]
            val w1 = c1.w
            val w2 = c2.w

            if (w1 > near && w2 > near) {
                screenPoints.add(Render3DUtil.clipToScreen(c1, sw, sh))
                screenPoints.add(Render3DUtil.clipToScreen(c2, sw, sh))
            } else if (w1 > near || w2 > near) {
                val t = (near - w1) / (w2 - w1)
                val cutX = c1.x + t * (c2.x - c1.x)
                val cutY = c1.y + t * (c2.y - c1.y)
                val cutClip = Vector4f(cutX, cutY, 0f, near)
                val cutScreen = Render3DUtil.clipToScreen(cutClip, sw, sh)
                if (w1 > near) {
                    screenPoints.add(Render3DUtil.clipToScreen(c1, sw, sh))
                    screenPoints.add(cutScreen)
                } else {
                    screenPoints.add(cutScreen)
                    screenPoints.add(Render3DUtil.clipToScreen(c2, sw, sh))
                }
            }
        }

        if (screenPoints.isEmpty()) return null

        var bMinX = Float.MAX_VALUE
        var bMaxX = -Float.MAX_VALUE
        var bMinY = Float.MAX_VALUE
        var bMaxY = -Float.MAX_VALUE

        for (p in screenPoints) {
            if (p.x < bMinX) bMinX = p.x
            if (p.x > bMaxX) bMaxX = p.x
            if (p.y < bMinY) bMinY = p.y
            if (p.y > bMaxY) bMaxY = p.y
        }

        // Offscreen cull
        if (bMaxX < -100f || bMinX > sw + 100f || bMaxY < -100f || bMinY > sh + 100f) {
            return null
        }

        return ScreenBox(bMinX, bMinY, bMaxX, bMaxY)
    }

    // ==========================================
    // Color Resolver
    // ==========================================
    private fun resolveColor(offset: Float, alphaFactor: Float, isPrimary: Boolean): NanoVGColor {
        val t = animTime

        val baseColor = when (KillAura.espColorMode.value) {
            "Theme" -> HUDModule.sampleColor(offset)
            "Custom" -> {
                val c1 = KillAura.espColor.value
                val c2 = KillAura.espColor2.value
                val wave = ((sin(t * 2.6 + offset * PI * 2.0) + 1.0) * 0.5).toFloat()
                c1.lerp(c2, wave)
            }
            "Health" -> {
                val hpFrac = if (isPrimary) {
                    val p = KillAura.target
                    if (p != null) (p.health / p.maxHealth.coerceAtLeast(1f)).coerceIn(0f, 1f) else 1f
                } else 0.8f
                when {
                    hpFrac > 0.65f -> NanoVGColor(0.20f, 0.92f, 0.45f, 1f)
                    hpFrac > 0.35f -> NanoVGColor(1.00f, 0.72f, 0.16f, 1f)
                    else -> NanoVGColor(1.00f, 0.22f, 0.28f, 1f)
                }
            }
            "Astolfo" -> {
                val wave = ((sin(t * 3.2 + offset * PI * 2.0) + 1.0) * 0.5).toFloat()
                val astPink = NanoVGColor(1.00f, 0.38f, 0.75f, 1f)
                val astCyan = NanoVGColor(0.00f, 0.92f, 1.00f, 1f)
                astPink.lerp(astCyan, wave)
            }
            "Rainbow" -> {
                val hue = ((t * 80f + offset * 360f) % 360f + 360f) % 360f
                NanoVGColor.fromHsv(hue.toFloat(), 0.90f, 1.0f)
            }
            else -> HUDModule.sampleColor(offset)
        }

        val primaryMultiplier = if (isPrimary) 1.0f else 0.72f
        val totalAlpha = (baseColor.a * HUDModule.visualAlpha.getFloat() * alphaFactor * primaryMultiplier).coerceIn(0f, 1f)
        return baseColor.withAlpha(totalAlpha)
    }

    // ==========================================
    // Mode 1: Corners (Square corner target brackets)
    // ==========================================
    private fun renderCorners(state: BoxState, sw: Float, sh: Float) {
        val ctx = Draw2D.context
        val isPrimary = state.isPrimary

        // Subtle tactical snap breathing
        val pulse = if (isPrimary) (sin(animTime * 5.0) * 1.8f).toFloat() else 0f
        val pad = 3.0f

        val x = state.minX - pad - pulse
        val y = state.minY - pad - pulse
        val w = (state.maxX - state.minX) + (pad + pulse) * 2f
        val h = (state.maxY - state.minY) + (pad + pulse) * 2f

        val maxX = x + w
        val maxY = y + h

        val cornerRatio = KillAura.espCornerSize.getFloat()
        val clw = (w * cornerRatio).coerceIn(8f, 38f)
        val clh = (h * cornerRatio).coerceIn(8f, 38f)

        val mainColor = resolveColor(0f, state.alpha, isPrimary)
        val shadowColor = NanoVGColor(0f, 0f, 0f, mainColor.a * 0.85f)
        val squareCapSize = if (isPrimary) 4.0f else 3.0f

        // 1. Heavy neon bloom
        if (KillAura.espGlow.value) {
            val bloomAlpha = if (isPrimary) 0.55f else 0.35f
            val glowCol = mainColor.withAlpha(mainColor.a * bloomAlpha)
            val spread = if (isPrimary) 5.0f else 2.5f

            // 4-corner bloom glow
            Draw2D.glow(x - 2f, y - 2f, clw + 4f, clh + 4f, 0f, 12f, glowCol, spread)
            Draw2D.glow(maxX - clw - 2f, y - 2f, clw + 4f, clh + 4f, 0f, 12f, glowCol, spread)
            Draw2D.glow(x - 2f, maxY - clh - 2f, clw + 4f, clh + 4f, 0f, 12f, glowCol, spread)
            Draw2D.glow(maxX - clw - 2f, maxY - clh - 2f, clw + 4f, clh + 4f, 0f, 12f, glowCol, spread)

            // Center faint holographic tint
            Draw2D.rect(x, y, w, h, mainColor.withAlpha(if (isPrimary) 0.08f else 0.03f))
        }

        // 2. Draw 4 corner lines (outer shadow + wide neon glow + core line)
        fun drawCornerLines(col: NanoVGColor, lineWidth: Float) {
            // Top-Left
            LineRenderer.drawLine(ctx, x, y, x + clw, y, col, lineWidth)
            LineRenderer.drawLine(ctx, x, y, x, y + clh, col, lineWidth)

            // Top-Right
            LineRenderer.drawLine(ctx, maxX, y, maxX - clw, y, col, lineWidth)
            LineRenderer.drawLine(ctx, maxX, y, maxX, y + clh, col, lineWidth)

            // Bottom-Left
            LineRenderer.drawLine(ctx, x, maxY, x + clw, maxY, col, lineWidth)
            LineRenderer.drawLine(ctx, x, maxY, x, maxY - clh, col, lineWidth)

            // Bottom-Right
            LineRenderer.drawLine(ctx, maxX, maxY, maxX - clw, maxY, col, lineWidth)
            LineRenderer.drawLine(ctx, maxX, maxY, maxX, maxY - clh, col, lineWidth)
        }

        // Dark outer contour for contrast
        drawCornerLines(shadowColor, 3.2f)

        // Wide neon glow
        if (KillAura.espGlow.value) {
            drawCornerLines(mainColor.withAlpha(mainColor.a * 0.40f), 4.5f)
        }

        // Main line
        drawCornerLines(mainColor, 1.8f)

        // Light Pollution Core line
        val coreCol = mainColor.lighten(0.35f).withAlpha(mainColor.a)
        drawCornerLines(coreCol, 0.8f)

        // 3. Square Corner Tips
        val capHalf = squareCapSize * 0.5f
        val capCol = coreCol

        // Top-Left square
        Draw2D.rect(x - capHalf, y - capHalf, squareCapSize, squareCapSize, capCol)
        Draw2D.roundedOutline(x - capHalf - 0.5f, y - capHalf - 0.5f, squareCapSize + 1f, squareCapSize + 1f, 0f, shadowColor, 0.8f)

        // Top-Right square
        Draw2D.rect(maxX - capHalf, y - capHalf, squareCapSize, squareCapSize, capCol)
        Draw2D.roundedOutline(maxX - capHalf - 0.5f, y - capHalf - 0.5f, squareCapSize + 1f, squareCapSize + 1f, 0f, shadowColor, 0.8f)

        // Bottom-Left square
        Draw2D.rect(x - capHalf, maxY - capHalf, squareCapSize, squareCapSize, capCol)
        Draw2D.roundedOutline(x - capHalf - 0.5f, maxY - capHalf - 0.5f, squareCapSize + 1f, squareCapSize + 1f, 0f, shadowColor, 0.8f)

        // Bottom-Right square
        Draw2D.rect(maxX - capHalf, maxY - capHalf, squareCapSize, squareCapSize, capCol)
        Draw2D.roundedOutline(maxX - capHalf - 0.5f, maxY - capHalf - 0.5f, squareCapSize + 1f, squareCapSize + 1f, 0f, shadowColor, 0.8f)
    }

    // ==========================================
    // Mode 2: Box (2D Neon Box)
    // ==========================================
    private fun renderBox(state: BoxState, sw: Float, sh: Float) {
        val isPrimary = state.isPrimary
        val pad = 3.0f

        val x = state.minX - pad
        val y = state.minY - pad
        val w = (state.maxX - state.minX) + pad * 2f
        val h = (state.maxY - state.minY) + pad * 2f

        val maxX = x + w
        val maxY = y + h

        val mainColor = resolveColor(0f, state.alpha, isPrimary)
        val shadowColor = NanoVGColor(0f, 0f, 0f, mainColor.a * 0.85f)

        // 1. Full-box neon bloom
        if (KillAura.espGlow.value) {
            val bloomAlpha = if (isPrimary) 0.60f else 0.35f
            val glowCol = mainColor.withAlpha(mainColor.a * bloomAlpha)
            val spread = if (isPrimary) 6.0f else 3.0f

            Draw2D.glow(x, y, w, h, 0f, 14f, glowCol, spread)
            // Interior holographic fill
            Draw2D.rect(x + 1f, y + 1f, w - 2f, h - 2f, mainColor.withAlpha(if (isPrimary) 0.14f else 0.06f))
        }

        // 2. Triple border (outer shadow + solid neon + inner shadow)
        Draw2D.roundedOutline(x - 1f, y - 1f, w + 2f, h + 2f, 0f, shadowColor, 1.0f)
        Draw2D.roundedOutline(x, y, w, h, 0f, mainColor, 1.8f)
        Draw2D.roundedOutline(x + 1f, y + 1f, w - 2f, h - 2f, 0f, shadowColor, 1.0f)

        // 3. Square Corner Accents
        val capSize = if (isPrimary) 4.5f else 3.5f
        val capHalf = capSize * 0.5f
        val capCol = mainColor.lighten(0.35f)

        Draw2D.rect(x - capHalf, y - capHalf, capSize, capSize, capCol)
        Draw2D.rect(maxX - capHalf, y - capHalf, capSize, capSize, capCol)
        Draw2D.rect(x - capHalf, maxY - capHalf, capSize, capSize, capCol)
        Draw2D.rect(maxX - capHalf, maxY - capHalf, capSize, capSize, capCol)
    }
}
