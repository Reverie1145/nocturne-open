package cn.reverie.module.render

import cn.reverie.app.event.render.Render2DEvent
import cn.reverie.app.event.render.RenderHudEvent
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.module.player.Teams
import cn.reverie.nocturne.PresenceManager
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGPath
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.render.font.TextAlignment
import cn.reverie.util.render.Render3DUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponents
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.Enchantment
import org.lwjgl.glfw.GLFW
import java.util.Collections
import kotlin.math.roundToInt
object NameTags : Module(
    name = "NameTags",
    description = "Shows sleek separated pills with heart health, name and distance above entities",
    category = Category.RENDER,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {

    val players = BooleanSetting("Players", "Highlight player entities", true)
    val health = BooleanSetting("Health", "Display heart health pill", true)
    val distance = BooleanSetting("Distance", "Display distance pill in meters", true)
    val armor = BooleanSetting("Armor", "Render equipped armor and items", true)
    val enchantments = BooleanSetting("Enchantments", "Display enchantment levels above items", false, { armor.value })
    val onlyInvisible = BooleanSetting("Only Invisible", "Only render nametags for invisible entities", false)
    val self = BooleanSetting("Self", "Highlight local player in 3rd person view", false, { players.value })
    val monsters = BooleanSetting("Monsters", "Highlight hostile monsters", false)
    val animals = BooleanSetting("Animals", "Highlight passive animals", false)
    val scale = NumberSetting("Scale", "Nametag size scale multiplier", 1.0, 0.5, 2.0, 0.05)
    val maxDistance = NumberSetting("Max Distance", "Maximum render distance in blocks", 96.0, 16.0, 256.0, 8.0)
    val cancelVanilla = BooleanSetting("Cancel Vanilla", "Hide default vanilla name labels", true)
    val nocturneUsers = BooleanSetting("Nocturne Users", "Mark players also using Nocturne client", true)

    private data class ItemRenderTask(
        val stack: ItemStack,
        val x: Int,
        val y: Int
    )

    private val itemQueue = Collections.synchronizedList(ArrayList<ItemRenderTask>())

    private val LEGACY_CODE = Regex("§.")

    init {
        listen(Render2DEvent::class.java) { onRender2D(it.partialTicks) }
        listen(RenderHudEvent::class.java) { onRenderHud(it.graphics) }
    }

    private fun onRenderHud(graphics: GuiGraphicsExtractor) {
        if (!enabled || itemQueue.isEmpty()) return
        val mc = Minecraft.getInstance()
        val font = mc.font
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.scale(ResolutionHelper.uiScaleFactor, ResolutionHelper.uiScaleFactor)
        synchronized(itemQueue) {
            try {
                for (task in itemQueue) {
                    try {
                        graphics.item(task.stack, task.x, task.y)
                        graphics.itemDecorations(font, task.stack, task.x, task.y)
                    } catch (ignored: Throwable) {}
                }
            } finally {
                itemQueue.clear()
                pose.popMatrix()
            }
        }
    }

    private fun isValid(mc: Minecraft, entity: Entity): Boolean {
        val player = mc.player ?: return false
        if (entity === player) {
            return self.value && !mc.options.cameraType.isFirstPerson
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
        if (onlyInvisible.value && !entity.isInvisible) {
            return false
        }
        return when (entity) {
            is Player -> players.value
            is Monster -> monsters.value
            is Animal -> animals.value
            else -> false
        }
    }

    private fun onRender2D(partialTicks: Float) {
        itemQueue.clear()
        if (!enabled) return

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return

        val sw = ResolutionHelper.scaledWidth
        val sh = ResolutionHelper.scaledHeight
        if (sw <= 0f || sh <= 0f) return

        val font = RenderEngine.fontRenderer
        val s = scale.getFloat()

        for (entity in level.entitiesForRendering()) {
            if (!isValid(mc, entity)) continue

            val interpX = Mth.lerp(partialTicks.toDouble(), entity.xo, entity.x)
            val interpY = Mth.lerp(partialTicks.toDouble(), entity.yo, entity.y)
            val interpZ = Mth.lerp(partialTicks.toDouble(), entity.zo, entity.z)

            val headOffset = if (entity.isCrouching) 0.25 else 0.45
            val screenPos = Render3DUtil.worldToScreen(interpX, interpY + entity.bbHeight + headOffset, interpZ) ?: continue
            val screenX = screenPos.x
            val screenY = screenPos.y

            if (screenX < -150f || screenX > sw + 150f || screenY < -150f || screenY > sh + 150f) {
                continue
            }

            val isTeammate = Teams.isTeammate(entity)
            val living = entity as? LivingEntity
            val hp = if (living != null) (living.health + living.absorptionAmount).coerceAtLeast(0f) else 0f
            val maxHp = living?.maxHealth ?: 20f

            val isNocturneUser = nocturneUsers.value && entity is Player &&
                PresenceManager.isNocturneUser(entity.name.string)
            val protectedName = NameProtect.replacementFor(entity)
            val cleanName = (protectedName ?: entity.name.string).replace(LEGACY_CODE, "")

            HUDModule.withHudFont {
                val alpha = HUDModule.visualAlpha.getFloat()
                val pillH = 14.0f * s
                val padX = 5.5f * s
                val gap = 2.5f * s
                val radius = pillH * 0.5f
                val nameFont = 10.5f * s
                val hpFont = 10.0f * s
                val distFont = 9.5f * s
                val heartW = 7.5f * s
                val heartH = 7.0f * s
                val heartGap = 3.5f * s

                val prefix = if (isNocturneUser) "Nocturne " else ""
                val prefixW = if (prefix.isNotEmpty()) font.getStringWidth(prefix, nameFont) + 0.5f * s else 0f
                val nameW = font.getStringWidth(cleanName, nameFont)
                val showHealth = health.value && living != null
                val showDistance = distance.value

                val hpText = hp.roundToInt().toString()
                val hpW = font.getStringWidth(hpText, hpFont)
                val distText = "${player.distanceTo(entity).toInt()}m"
                val distW = font.getStringWidth(distText, distFont)

                var segCount = 1
                if (showHealth) segCount++
                if (showDistance) segCount++
                var totalW = padX * 2 + prefixW + nameW
                if (showHealth) totalW += (padX * 2 + heartW + heartGap + hpW) + gap
                if (showDistance) totalW += (padX * 2 + distW) + gap

                val boxY = screenY - pillH
                var px = screenX - totalW * 0.5f
                val centerY = boxY + pillH * 0.5f

                val pillBg = NanoVGColor(0.031f, 0.031f, 0.039f, (HUDModule.bgOpacity.getFloat() * alpha).coerceIn(0f, 1f))
                val shadowCol = NanoVGColor(0f, 0f, 0f, 0.40f * HUDModule.shadowStrength.getFloat() * alpha)

                fun drawPill(pw: Float) {
                    if (HUDModule.widgetShadow.value) {
                        Draw2D.shadow(px, boxY, pw, pillH, radius, 3.5f * s, shadowCol, 1f)
                    }
                    try {
                        RenderEngine.backdropBlurRenderer.renderBackdrop(
                            px, boxY, pw, pillH,
                            radius = radius,
                            tintColor = pillBg,
                            blurRadius = HUDModule.blurRadius.getFloat(),
                            enableBlur = HUDModule.blur.value,
                            backdropOpacity = pillBg.a
                        )
                    } catch (ignored: Throwable) {
                        Draw2D.roundedRect(px, boxY, pw, pillH, radius, pillBg)
                    }
                }

                if (showHealth) {
                    val segW = padX * 2 + heartW + heartGap + hpW
                    drawPill(segW)
                    val healthPct = (hp / maxHp.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val heartCol = when {
                        healthPct > 0.6f -> NanoVGColor(0.247f, 0.851f, 0.306f, alpha)
                        healthPct > 0.3f -> NanoVGColor(1.0f, 0.761f, 0.20f, alpha)
                        else -> NanoVGColor(1.0f, 0.302f, 0.302f, alpha)
                    }
                    drawHeart(px + padX + heartW * 0.5f, centerY, heartW, heartH, heartCol)
                    NanoVGRenderer.drawVerticallyCenteredString(
                        hpText, px + padX + heartW + heartGap, centerY,
                        NanoVGColor.WHITE.multiplyAlpha(alpha), hpFont, shadow = HUDModule.textShadow.value
                    )
                    px += segW + gap
                }

                drawPill(padX * 2 + prefixW + nameW)
                val nameCol = when {
                    isNocturneUser -> NanoVGColor(0.663f, 0.545f, 1.0f, alpha)
                    isTeammate -> NanoVGColor(0.49f, 0.94f, 0.667f, alpha)
                    else -> NanoVGColor.WHITE.multiplyAlpha(alpha)
                }
                if (prefix.isNotEmpty()) {
                    NanoVGRenderer.drawVerticallyCenteredString(
                        prefix, px + padX, centerY, nameCol, nameFont, shadow = HUDModule.textShadow.value
                    )
                }
                val protectedTextColor = if (isNocturneUser) NanoVGColor.WHITE.multiplyAlpha(alpha) else nameCol
                if (protectedName != null && NameProtect.colorfulName.value) {
                    font.drawSpans(
                        NameProtect.nameColorSpans(cleanName, protectedTextColor),
                        px + padX + prefixW,
                        centerY,
                        nameFont,
                        TextAlignment.MIDDLE_LEFT,
                        shadow = HUDModule.textShadow.value
                    )
                } else {
                    NanoVGRenderer.drawVerticallyCenteredString(
                        cleanName, px + padX + prefixW, centerY,
                        protectedTextColor,
                        nameFont, shadow = HUDModule.textShadow.value
                    )
                }
                px += padX * 2 + prefixW + nameW + gap

                if (showDistance) {
                    val segW = padX * 2 + distW
                    drawPill(segW)
                    NanoVGRenderer.drawVerticallyCenteredString(
                        distText, px + padX, centerY,
                        NanoVGColor(0.604f, 0.627f, 0.659f, 0.95f * alpha), distFont, shadow = HUDModule.textShadow.value
                    )
                }
            }

            if (armor.value && living != null) {
                val items = mutableListOf<ItemStack>()
                val mainHand = living.mainHandItem
                if (!mainHand.isEmpty) items.add(mainHand)

                val armorSlots = arrayOf(
                    EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.FEET
                )
                for (slot in armorSlots) {
                    val st = living.getItemBySlot(slot)
                    if (!st.isEmpty) items.add(st)
                }

                val offHand = living.offhandItem
                if (!offHand.isEmpty) items.add(offHand)

                if (items.isNotEmpty()) {
                    val itemSpacing = 18f
                    val armorStartX = screenX - (items.size * itemSpacing) * 0.5f
                    val armorY = screenY - 14.0f * s - itemSpacing - 2.0f

                    for (i in items.indices) {
                        val stack = items[i]
                        val itemX = armorStartX + i * itemSpacing

                        synchronized(itemQueue) {
                            itemQueue.add(ItemRenderTask(stack.copy(), itemX.toInt(), armorY.toInt()))
                        }

                        if (enchantments.value) {
                            HUDModule.withHudFont {
                                val enchants = getEnchantmentsList(stack).take(3)
                                for (j in enchants.indices) {
                                    val ench = enchants[j]
                                    val enchY = armorY - 7.5f * (enchants.size - j) - 1.0f
                                    val enchColor = if (isMaxEnchant(ench)) {
                                        NanoVGColor(1.0f, 0.40f, 0.40f, 1.0f)
                                    } else {
                                        NanoVGColor(0.90f, 0.92f, 0.98f, 0.95f)
                                    }
                                    font.drawCenteredString(ench, itemX + 8.0f, enchY, enchColor.multiplyAlpha(HUDModule.visualAlpha.getFloat()), 7.5f, shadow = HUDModule.textShadow.value)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun drawHeart(cx: Float, cy: Float, w: Float, h: Float, color: NanoVGColor) {
        val ctx = RenderEngine.nanoVG.handle
        val path = NanoVGPath(ctx)
        path.begin()
        path.circle(cx - w * 0.23f, cy - h * 0.14f, w * 0.26f)
        path.circle(cx + w * 0.23f, cy - h * 0.14f, w * 0.26f)
        path.moveTo(cx - w * 0.47f, cy - h * 0.06f)
        path.lineTo(cx + w * 0.47f, cy - h * 0.06f)
        path.lineTo(cx, cy + h * 0.48f)
        path.close()
        path.fill(color)
    }

    private fun isMaxEnchant(ench: String): Boolean {
        return ench.endsWith("4") || ench.endsWith("5") || ench.endsWith("3") || ench.equals("Mend", ignoreCase = true)
    }

    private fun getEnchantmentsList(stack: ItemStack): List<String> {
        val result = mutableListOf<String>()
        val itemEnchantments = stack.get(DataComponents.ENCHANTMENTS) ?: return result

        for (entry in itemEnchantments.entrySet()) {
            val holder = entry.key
            val level = entry.intValue
            val name = formatEnchantmentName(holder)
            result.add("$name$level")
        }
        return result
    }

    private fun formatEnchantmentName(holder: Holder<Enchantment>): String {
        val rawPath = holder.unwrapKey().map { it.identifier().path }.orElseGet { holder.registeredName }
        val clean = rawPath.substringAfterLast(':').substringAfterLast('/')
        return when (clean) {
            "protection" -> "P"
            "projectile_protection" -> "PP"
            "blast_protection" -> "BP"
            "fire_protection" -> "FP"
            "feather_falling" -> "FF"
            "sharpness" -> "S"
            "smite" -> "Smite"
            "bane_of_arthropods" -> "Bane"
            "knockback" -> "KB"
            "fire_aspect" -> "FA"
            "looting" -> "Loot"
            "sweeping_edge" -> "Swp"
            "unbreaking" -> "U"
            "mending" -> "M"
            "power" -> "Pow"
            "punch" -> "Pnch"
            "flame" -> "Flm"
            "infinity" -> "Inf"
            "efficiency" -> "Eff"
            "silk_touch" -> "Silk"
            "fortune" -> "Fort"
            "respiration" -> "Resp"
            "aqua_affinity" -> "Aqua"
            "thorns" -> "Th"
            "depth_strider" -> "Dpth"
            "frost_walker" -> "Frst"
            "soul_speed" -> "Soul"
            "swift_sneak" -> "Swft"
            "density" -> "Dens"
            "breach" -> "Brch"
            "wind_burst" -> "Wrst"
            else -> clean.take(3).replaceFirstChar { it.uppercase() }
        }
    }
}
