package cn.reverie.module.movement

import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.combat.KillAura
import cn.reverie.module.world.Scaffold
import cn.reverie.app.event.player.MoveInputEvent
import cn.reverie.util.player.MovementUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.effect.MobEffects
import org.lwjgl.glfw.GLFW

object Sprint : Module(
    name = "Sprint",
    description = "Automatically keeps player sprinting",
    category = Category.MOVEMENT,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {
    val mode = ModeSetting("Mode", "Sprint mode", "Legit", listOf("Legit", "Omni"))
    val checkHunger = BooleanSetting("Check Hunger", "Stop sprinting when food level is too low", true)
    val checkBlindness = BooleanSetting("Check Blindness", "Stop sprinting when blinded", true)
    val checkSneak = BooleanSetting("Check Sneak", "Stop sprinting when sneaking", true)
    val checkCollision = BooleanSetting("Check Collision", "Stop sprinting when colliding horizontally", false)
    val checkUsingItem = BooleanSetting("Check Using Item", "Stop sprinting when using items", false)
    val keepSprint = BooleanSetting("Keep Sprint", "Keep sprinting active continuously", true)

    override val suffix: String
        get() = mode.value

    @JvmStatic
    fun canSprint(player: LocalPlayer, checkDirection: Boolean = true): Boolean {
        if (KillAura.shouldPauseSprint(player)) return false
        if (Scaffold.enabled && !Scaffold.sprint.value) return false
        if (checkSneak.value && (player.isShiftKeyDown || player.input.keyPresses.shift())) return false
        if (checkBlindness.value && player.hasEffect(MobEffects.BLINDNESS)) return false
        if (checkHunger.value && !player.abilities.mayfly && !player.abilities.invulnerable && !player.foodData.hasEnoughFood()) return false
        if (checkCollision.value && player.horizontalCollision) return false
        if (checkUsingItem.value && player.isUsingItem) return false

        if (checkDirection) {
            return if (mode.isMode("Legit")) {
                player.input.hasForwardImpulse() || player.input.moveVector.y > 1.0E-5f
            } else {
                MovementUtil.isMoving(player)
            }
        }
        return MovementUtil.isMoving(player)
    }

    @JvmStatic
    fun shouldOmniSprint(): Boolean {
        if (!enabled) return false
        if (!mode.isMode("Omni")) return false
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        return canSprint(player, checkDirection = false)
    }

    @JvmStatic
    fun onMoveInput(player: LocalPlayer, event: MoveInputEvent) {
        if (!enabled) return
        if (KillAura.shouldPauseSprint(player)) {
            event.sprint = false
            return
        }
        if (Scaffold.enabled && !Scaffold.sprint.value) return

        if (checkSneak.value && event.sneak) return
        if (checkBlindness.value && player.hasEffect(MobEffects.BLINDNESS)) return
        if (checkHunger.value && !player.abilities.mayfly && !player.abilities.invulnerable && !player.foodData.hasEnoughFood()) return
        if (checkCollision.value && player.horizontalCollision) return
        if (checkUsingItem.value && player.isUsingItem) return

        val moving = if (mode.isMode("Legit")) {
            event.forward > 1.0E-5f
        } else {
            event.forward != 0.0f || event.strafe != 0.0f
        }

        if (moving) {
            event.sprint = true
        }
    }

    @JvmStatic
    fun onLivingUpdate(player: LocalPlayer) {
        if (!enabled) return
        if (keepSprint.value && canSprint(player, checkDirection = true)) {
            if (!player.isSprinting) {
                player.isSprinting = true
            }
        }
    }

    override fun onDisable() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (!mc.options.keySprint.isDown) {
            player.isSprinting = false
        }
    }
}
