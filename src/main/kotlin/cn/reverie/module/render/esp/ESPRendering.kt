package cn.reverie.module.render.esp

import cn.reverie.module.render.NameTags
import cn.reverie.module.render.NameProtect

import cn.reverie.module.render.ESP

import cn.reverie.module.player.Teams
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.draw.LineRenderer
import cn.reverie.render.font.TextAlignment
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGTransform
import cn.reverie.util.render.Render3DUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import org.joml.Vector2f
import org.joml.Vector4f
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
    internal fun ESP.isValidForRender(player: LocalPlayer, entity: Entity): Boolean {
        if (entity === player) {
            return self.value && !Minecraft.getInstance().options.cameraType.isFirstPerson
        }
        if (!entity.isAlive || entity.isRemoved) {
            return false
        }
        if (entity is ArmorStand) {
            return false
        }
        if (entity.distanceTo(player) > maxDistance.getDouble()) {
            return false
        }
        if (!invisibles.value && entity.isInvisibleTo(player)) {
            return false
        }
        return when (entity) {
            is Player -> players.value
            is Monster -> monsters.value
            is Animal -> animals.value
            is ItemEntity -> droppedItems.value
            is EndCrystal -> crystals.value
            else -> false
        }
    }

    internal fun ESP.render2D(partialTicks: Float) {
        if (!enabled) return

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return

        val sw = ResolutionHelper.scaledWidth
        val sh = ResolutionHelper.scaledHeight
        if (sw <= 0f || sh <= 0f) return

        val is2D = mode.isMode("2D Box") || mode.isMode("Corners") || mode.isMode("2D + Glow")
        val is3D = mode.isMode("3D Box")
        val isCorners = mode.isMode("Corners")

        for (entity in level.entitiesForRendering()) {
            if (!isValidForRender(player, entity)) continue

            val interpX = Mth.lerp(partialTicks.toDouble(), entity.xo, entity.x)
            val interpY = Mth.lerp(partialTicks.toDouble(), entity.yo, entity.y)
            val interpZ = Mth.lerp(partialTicks.toDouble(), entity.zo, entity.z)

            val bb = entity.boundingBox
            val hw = (bb.maxX - bb.minX) * 0.5
            val h = bb.maxY - bb.minY
            val hd = (bb.maxZ - bb.minZ) * 0.5

            val minX = interpX - hw
            val maxX = interpX + hw
            val minY = interpY
            val maxY = interpY + h
            val minZ = interpZ - hd
            val maxZ = interpZ + hd

            val corners3D = arrayOf(
                doubleArrayOf(minX, minY, minZ),
                doubleArrayOf(minX, minY, maxZ),
                doubleArrayOf(minX, maxY, minZ),
                doubleArrayOf(minX, maxY, maxZ),
                doubleArrayOf(maxX, minY, minZ),
                doubleArrayOf(maxX, minY, maxZ),
                doubleArrayOf(maxX, maxY, minZ),
                doubleArrayOf(maxX, maxY, maxZ)
            )

            val edges = arrayOf(
                intArrayOf(0, 1), intArrayOf(1, 5), intArrayOf(5, 4), intArrayOf(4, 0),
                intArrayOf(2, 3), intArrayOf(3, 7), intArrayOf(7, 6), intArrayOf(6, 2),
                intArrayOf(0, 2), intArrayOf(1, 3), intArrayOf(4, 6), intArrayOf(5, 7)
            )

            val clips = Array(8) { i ->
                Render3DUtil.worldToClip(corners3D[i][0], corners3D[i][1], corners3D[i][2])
            }

            val screenPoints = mutableListOf<Vector2f>()
            val clippedEdges = mutableListOf<Pair<Vector2f, Vector2f>>()

            for (edge in edges) {
                val c1 = clips[edge[0]]
                val c2 = clips[edge[1]]

                val w1 = c1.w
                val w2 = c2.w
                val near = 0.05f

                if (w1 > near && w2 > near) {
                    val p1 = clipToScreen(c1, sw, sh)
                    val p2 = clipToScreen(c2, sw, sh)
                    screenPoints.add(p1)
                    screenPoints.add(p2)
                    clippedEdges.add(Pair(p1, p2))
                } else if (w1 > near || w2 > near) {
                    val t = (near - w1) / (w2 - w1)
                    val cutX = c1.x + t * (c2.x - c1.x)
                    val cutY = c1.y + t * (c2.y - c1.y)
                    val cutClip = Vector4f(cutX, cutY, 0f, near)
                    val cutScreen = clipToScreen(cutClip, sw, sh)

                    if (w1 > near) {
                        val p1 = clipToScreen(c1, sw, sh)
                        screenPoints.add(p1)
                        screenPoints.add(cutScreen)
                        clippedEdges.add(Pair(p1, cutScreen))
                    } else {
                        val p2 = clipToScreen(c2, sw, sh)
                        screenPoints.add(cutScreen)
                        screenPoints.add(p2)
                        clippedEdges.add(Pair(cutScreen, p2))
                    }
                }
            }

            if (screenPoints.isEmpty()) {
                if (offscreen.value) {
                    renderOffscreenIndicator(player, interpX, interpZ, entity, sw, sh)
                }
                continue
            }

            var boxMinX = Float.MAX_VALUE
            var boxMaxX = -Float.MAX_VALUE
            var boxMinY = Float.MAX_VALUE
            var boxMaxY = -Float.MAX_VALUE

            for (p in screenPoints) {
                if (p.x < boxMinX) boxMinX = p.x
                if (p.x > boxMaxX) boxMaxX = p.x
                if (p.y < boxMinY) boxMinY = p.y
                if (p.y > boxMaxY) boxMaxY = p.y
            }

            val isOffscreen = boxMaxX < -50f || boxMinX > sw + 50f || boxMaxY < -50f || boxMinY > sh + 50f
            if (isOffscreen) {
                if (offscreen.value) {
                    renderOffscreenIndicator(player, interpX, interpZ, entity, sw, sh)
                }
                continue
            }

            val boxW = boxMaxX - boxMinX
            val boxH = boxMaxY - boxMinY
            if (boxW < 2f || boxH < 2f) continue

            val mainColor = getEntityColor(entity)
            val shadowColor = NanoVGColor(0f, 0f, 0f, 0.70f * HUDModule.shadowStrength.getFloat())

            if (is2D) {
                if (isCorners) {
                    renderCorners(boxMinX, boxMinY, boxW, boxH, mainColor, shadowColor)
                } else {
                    render2DBox(boxMinX, boxMinY, boxW, boxH, mainColor, shadowColor)
                }
            }

            if (is3D) {
                render3DBox(clippedEdges, mainColor, shadowColor)
            }

            if (healthBar.value && entity is LivingEntity) {
                renderHealthBar(boxMinX, boxMinY, boxH, entity)
            }

            if (nameTags.value && !NameTags.enabled) {
                renderNametag(boxMinX, boxMaxX, boxMinY, entity, player)
            }

            if (items.value && entity is LivingEntity) {
                renderHeldItem(boxMinX, boxMaxX, boxMaxY, entity)
            }

            if (tracers.value) {
                renderTracer(interpX, interpY, interpZ, mainColor, sw, sh)
            }
        }
    }

    private fun ESP.clipToScreen(c: Vector4f, sw: Float, sh: Float): Vector2f {
        val invW = 1.0f / c.w
        val ndcX = c.x * invW
        val ndcY = c.y * invW
        val sx = ((ndcX + 1.0f) * 0.5f) * sw
        val sy = ((1.0f - ndcY) * 0.5f) * sh
        return Vector2f(sx, sy)
    }

    private fun ESP.render2DBox(
        x: Float, y: Float, w: Float, h: Float,
        mainColor: NanoVGColor, shadowColor: NanoVGColor
    ) {

        if (boxFill.value) {
            Draw2D.rect(x + 1f, y + 1f, w - 2f, h - 2f, mainColor.withAlpha(0.18f * HUDModule.visualAlpha.getFloat()))
        }

        if (HUDModule.widgetShadow.value) Draw2D.roundedOutline(x - 1f, y - 1f, w + 2f, h + 2f, 0f, shadowColor, 1.0f)
        Draw2D.roundedOutline(x, y, w, h, 0f, mainColor, 1.0f)
        if (HUDModule.widgetShadow.value) Draw2D.roundedOutline(x + 1f, y + 1f, w - 2f, h - 2f, 0f, shadowColor, 1.0f)
    }

    private fun ESP.renderCorners(
        x: Float, y: Float, w: Float, h: Float,
        mainColor: NanoVGColor, shadowColor: NanoVGColor
    ) {
        if (boxFill.value) {
            Draw2D.rect(x + 1f, y + 1f, w - 2f, h - 2f, mainColor.withAlpha(0.18f * HUDModule.visualAlpha.getFloat()))
        }

        val ratio = cornerLength.getFloat()
        val clw = (w * ratio).coerceIn(4f, 28f)
        val clh = (h * ratio).coerceIn(4f, 28f)
        val ctx = Draw2D.context

        val maxX = x + w
        val maxY = y + h

        fun drawCornerLines(color: NanoVGColor, width: Float) {

            LineRenderer.drawLine(ctx, x, y, x + clw, y, color, width)
            LineRenderer.drawLine(ctx, x, y, x, y + clh, color, width)

            LineRenderer.drawLine(ctx, maxX, y, maxX - clw, y, color, width)
            LineRenderer.drawLine(ctx, maxX, y, maxX, y + clh, color, width)

            LineRenderer.drawLine(ctx, x, maxY, x + clw, maxY, color, width)
            LineRenderer.drawLine(ctx, x, maxY, x, maxY - clh, color, width)

            LineRenderer.drawLine(ctx, maxX, maxY, maxX - clw, maxY, color, width)
            LineRenderer.drawLine(ctx, maxX, maxY, maxX, maxY - clh, color, width)
        }

        if (HUDModule.widgetShadow.value) drawCornerLines(shadowColor, 2.6f)
        drawCornerLines(mainColor, 1.3f)
    }

    private fun ESP.render3DBox(
        clippedEdges: List<Pair<Vector2f, Vector2f>>,
        mainColor: NanoVGColor, shadowColor: NanoVGColor
    ) {
        val ctx = Draw2D.context
        for (edge in clippedEdges) {
            val p1 = edge.first
            val p2 = edge.second
            if (HUDModule.widgetShadow.value) LineRenderer.drawLine(ctx, p1.x, p1.y, p2.x, p2.y, shadowColor, 2.6f)
            LineRenderer.drawLine(ctx, p1.x, p1.y, p2.x, p2.y, mainColor, 1.2f)
        }
    }

    private fun ESP.renderHealthBar(
        boxMinX: Float, boxMinY: Float, boxH: Float,
        entity: LivingEntity
    ) {
        HUDModule.withHudFont {
        val maxHp = entity.maxHealth
        val curHp = (entity.health + entity.absorptionAmount).coerceAtLeast(0f)
        val hpFrac = (curHp / maxHp).coerceIn(0f, 1f)

        val barW = 2.0f
        val barX = boxMinX - 5.5f
        val barY = boxMinY

        Draw2D.rect(barX - 0.75f, barY - 0.75f, barW + 1.5f, boxH + 1.5f, NanoVGColor(0f, 0f, 0f, 0.75f))
        Draw2D.rect(barX, barY, barW, boxH, NanoVGColor(0.04f, 0.05f, 0.07f, 0.88f))

        val fillH = boxH * hpFrac
        val fillY = barY + boxH - fillH
        val hpColor = getHealthColor(hpFrac)
        Draw2D.rect(barX, fillY, barW, fillH, hpColor)

        if (healthText.value) {
            val font = RenderEngine.fontRenderer
            val hpText = String.format("%.1f", curHp)
            val fontW = font.getStringWidth(hpText, 7.5f)
            font.drawString(
                hpText,
                barX - fontW - 2.5f,
                fillY - 2.5f,
                NanoVGColor.WHITE.multiplyAlpha(HUDModule.visualAlpha.getFloat()),
                7.5f,
                shadow = HUDModule.textShadow.value
            )
        }
        }
    }

    private fun ESP.renderNametag(
        boxMinX: Float, boxMaxX: Float, boxMinY: Float,
        entity: Entity, localPlayer: LocalPlayer
    ) {
        HUDModule.withHudFont {
        val font = RenderEngine.fontRenderer
        val protectedName = NameProtect.replacementFor(entity)
        val nameText = protectedName ?: entity.displayName.string
        val distText = "${entity.distanceTo(localPlayer).toInt()}m"
        val pingText = if (entity is Player) getPlayerPing(entity) else null

        val fullText = if (pingText != null) "$nameText  $distText  $pingText" else "$nameText  $distText"
        val textW = font.getStringWidth(fullText, 9.5f)
        val badgeW = textW + 8.0f
        val badgeH = 13.0f

        val badgeX = (boxMinX + boxMaxX) * 0.5f - badgeW * 0.5f
        val badgeY = boxMinY - badgeH - 3.5f

        if (HUDModule.widgetShadow.value) Draw2D.shadow(badgeX, badgeY, badgeW, badgeH, 3.0f, 4.0f, NanoVGColor(0f, 0f, 0f, 0.45f * HUDModule.shadowStrength.getFloat()), 1.0f)
        Draw2D.roundedRect(badgeX, badgeY, badgeW, badgeH, 3.0f, NanoVGColor(0.03f, 0.04f, 0.06f, 0.82f * HUDModule.visualAlpha.getFloat()))
        Draw2D.roundedOutline(badgeX, badgeY, badgeW, badgeH, 3.0f, NanoVGColor(1f, 1f, 1f, 0.12f * HUDModule.visualAlpha.getFloat()), 0.75f)

        var startX = badgeX + 4.0f
        val textY = badgeY + 2.0f

        val nameColor = NanoVGColor.WHITE.multiplyAlpha(HUDModule.visualAlpha.getFloat())
        if (protectedName != null && NameProtect.colorfulName.value) {
            font.drawSpans(
                NameProtect.nameColorSpans(nameText, nameColor),
                startX,
                textY,
                9.5f,
                TextAlignment.TOP_LEFT,
                shadow = HUDModule.textShadow.value
            )
        } else {
            font.drawString(nameText, startX, textY, nameColor, 9.5f, shadow = HUDModule.textShadow.value)
        }
        startX += font.getStringWidth(nameText, 9.5f) + 4.0f

        font.drawString(distText, startX, textY, NanoVGColor(0.65f, 0.68f, 0.76f, 0.95f * HUDModule.visualAlpha.getFloat()), 9.0f, shadow = HUDModule.textShadow.value)

        if (pingText != null) {
            startX += font.getStringWidth(distText, 9.0f) + 4.0f
            font.drawString(pingText, startX, textY, NanoVGColor(0.48f, 0.52f, 0.60f, 0.90f * HUDModule.visualAlpha.getFloat()), 8.5f, shadow = HUDModule.textShadow.value)
        }
        }
    }

    private fun ESP.renderHeldItem(
        boxMinX: Float, boxMaxX: Float, boxMaxY: Float,
        entity: LivingEntity
    ) {
        val stack = entity.mainHandItem
        if (stack.isEmpty) return
        HUDModule.withHudFont {
        val itemName = stack.hoverName.string
        val font = RenderEngine.fontRenderer
        val centerX = (boxMinX + boxMaxX) * 0.5f
        val itemY = boxMaxY + 3.5f

        font.drawCenteredString(
            itemName,
            centerX,
            itemY,
            NanoVGColor(0.85f, 0.88f, 0.95f, 0.92f * HUDModule.visualAlpha.getFloat()),
            8.5f,
            shadow = HUDModule.textShadow.value
        )
        }
    }

    private fun ESP.renderTracer(
        x: Double, y: Double, z: Double,
        color: NanoVGColor, sw: Float, sh: Float
    ) {
        val targetScreen = Render3DUtil.projectToScreen(x, y, z) ?: return
        val startX = sw * 0.5f
        val startY = if (tracerOrigin.isMode("Center")) sh * 0.5f else sh - 10f

        val tracerCol = NanoVGColor(color.r, color.g, color.b, color.a * 0.72f)
        val shadowCol = NanoVGColor(0f, 0f, 0f, 0.45f * HUDModule.shadowStrength.getFloat())

        if (HUDModule.widgetShadow.value) LineRenderer.drawLine(Draw2D.context, startX, startY, targetScreen.x, targetScreen.y, shadowCol, 2.5f)
        LineRenderer.drawLine(Draw2D.context, startX, startY, targetScreen.x, targetScreen.y, tracerCol, 1.2f)
    }

    private fun ESP.renderOffscreenIndicator(
        player: LocalPlayer, targetX: Double, targetZ: Double,
        entity: Entity, sw: Float, sh: Float
    ) {
        val camPos = Render3DUtil.lastCameraPos
        val diffX = targetX - camPos.x
        val diffZ = targetZ - camPos.z

        val yaw = Render3DUtil.lastCameraYaw
        val angleDeg = Math.toDegrees(atan2(diffZ, diffX)) - 90.0
        val deltaYaw = Mth.wrapDegrees(angleDeg - yaw)

        val rad = Math.toRadians(deltaYaw - 90.0)
        val cx = sw * 0.5f
        val cy = sh * 0.5f
        val rx = sw * 0.40f
        val ry = sh * 0.40f

        val arrowX = cx + cos(rad).toFloat() * rx
        val arrowY = cy + sin(rad).toFloat() * ry

        val color = getEntityColor(entity)
        val rotationAngle = Math.toRadians(deltaYaw).toFloat()

        val transform = NanoVGTransform(Draw2D.context)
        transform.withTransform {
            transform.translate(arrowX, arrowY)
            transform.rotate(rotationAngle)

            val arrowPath = cn.reverie.render.nanovg.NanoVGPath(Draw2D.context)
            arrowPath.begin()
            arrowPath.moveTo(0f, -6.5f)
            arrowPath.lineTo(4.5f, 4.5f)
            arrowPath.lineTo(0f, 2.5f)
            arrowPath.lineTo(-4.5f, 4.5f)
            arrowPath.close()

            arrowPath.fill(color)
            arrowPath.stroke(NanoVGColor(0f, 0f, 0f, 0.65f), 1.2f)
        }
    }

    private fun ESP.getEntityColor(entity: Entity): NanoVGColor {
        if (Teams.isTeammate(entity)) {
            return NanoVGColor(0.25f, 0.88f, 0.55f, 0.95f * HUDModule.visualAlpha.getFloat())
        }
        return HUDModule.sampleColor(0f).multiplyAlpha(HUDModule.visualAlpha.getFloat())
    }

    private fun ESP.getHealthColor(hpFrac: Float): NanoVGColor {
        return when {
            hpFrac > 0.65f -> NanoVGColor(0.22f, 0.85f, 0.48f, 0.95f * HUDModule.visualAlpha.getFloat())
            hpFrac > 0.35f -> NanoVGColor(0.96f, 0.68f, 0.18f, 0.95f * HUDModule.visualAlpha.getFloat())
            else -> NanoVGColor(0.94f, 0.28f, 0.28f, 0.95f * HUDModule.visualAlpha.getFloat())
        }
    }

    private fun ESP.getPlayerPing(player: Player): String? {
        val connection = Minecraft.getInstance().connection ?: return null
        val info = connection.getPlayerInfo(player.uuid) ?: return null
        val latency = info.latency
        return if (latency >= 0) "${latency}ms" else null
    }
