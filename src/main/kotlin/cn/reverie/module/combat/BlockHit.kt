package cn.reverie.module.combat

import cn.reverie.app.event.player.AttackEvent
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.tags.ItemTags
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.EntityHitResult
import kotlin.math.abs
import kotlin.math.atan2

object BlockHit : Module(
    name = "BlockHit",
    description = "Sword block-hit modes",
    category = Category.COMBAT
) {
    val mode = ModeSetting("Mode", "Block-hit mode", "Predict", listOf("Manual", "Predict", "Auto"))
    val requireMouseDown = BooleanSetting("RequireMouseDown", "Require the use key to be held", true)
    val chance = NumberSetting("Chance", "Manual mode trigger chance", 70.0, 0.0, 100.0, 1.0)
    val predictDistance = NumberSetting("PredictDistance", "Prediction range in blocks", 5.0, 0.0, 6.0, 0.1)
    val predictAngle = NumberSetting("PredictAngle", "Prediction angle in degrees", 90.0, 0.0, 360.0, 1.0)

    private var useKeyPressed = false
    private var useKeyWasDown = false
    private var damageTriggered = false
    private var resetAfterLivingUpdate = false
    private var manualReleaseNextTick = false
    private var releaseTime = 0L
    private var predictedTarget: LivingEntity? = null
    private var targetStreak = 1
    private var activeMode = "Predict"

    init {
        listen(AttackEvent::class.java) { onAttack(it) }
    }

    @JvmStatic
    fun isRenderBlocking(): Boolean = enabled && useKeyPressed && isReady()

    @JvmStatic
    fun onTick() {
        if (!enabled) return
        if (!mode.value.equals(activeMode, ignoreCase = true)) {
            setUseKeyPressed(false)
            predictedTarget = null
            targetStreak = 1
            manualReleaseNextTick = false
            activeMode = mode.value
        }
        val mc = Minecraft.getInstance()
        val player = mc.player
        if (player == null || mc.level == null || mc.gameMode == null) {
            setUseKeyPressed(false)
            predictedTarget = null
            return
        }
        if (!isReady()) {
            setUseKeyPressed(false)
            predictedTarget = null
            return
        }
        if (mode.isMode("Manual")) return
        if (mode.isMode("Auto")) {
            tickAuto()
            return
        }

        val target = findTarget()
        val expectedHurtTime = if (target != null && target.hurtDuration > 0) target.hurtDuration else 10
        val playerHurtTimeExpired = player.hurtTime > expectedHurtTime + 1
        val releaseTimeReached = releaseTime > 0L && System.currentTimeMillis() >= releaseTime
        if (resetAfterLivingUpdate || target == null || playerHurtTimeExpired || releaseTimeReached) {
            resetAfterLivingUpdate = false
            setUseKeyPressed(false)
            predictedTarget = null
            return
        }
        predictedTarget = target
        if (requireMouseDown.value && !mc.options.keyUse.isDown) return
        val targetCanBeHit = target.hurtTime <= expectedHurtTime + 2
        val playerHurtTime = player.hurtTime
        if (!(useKeyPressed && damageTriggered && targetCanBeHit || useKeyPressed
                || playerHurtTime > expectedHurtTime + 1 || playerHurtTime <= 0)
        ) {
            pressUseKey()
            damageTriggered = true
            releaseTime = System.currentTimeMillis() + 50L * expectedHurtTime
        }
    }

    @JvmStatic
    fun onPostTick() {
        if (!enabled || !mode.isMode("Manual")) return
        if (manualReleaseNextTick) {
            manualReleaseNextTick = false
            setUseKeyPressed(false)
        }
    }

    @JvmStatic
    fun onLivingUpdate(player: LocalPlayer) {
        if (!enabled || !mode.isMode("Predict")) return
        if (!isReady()) return
        if (Minecraft.getInstance().player === player) {
            releaseTime = System.currentTimeMillis()
            resetAfterLivingUpdate = true
        }
    }

    @JvmStatic
    fun onWorldChange() {
        setUseKeyPressed(false)
        predictedTarget = null
        targetStreak = 1
        manualReleaseNextTick = false
        resetAfterLivingUpdate = false
        releaseTime = 0L
    }

    private fun onAttack(event: AttackEvent) {
        if (event.isCancelled || !isReady()) return
        val target = event.target as? LivingEntity ?: return
        if (mode.isMode("Manual")) {
            if (Math.random() * 100.0 < chance.value
                && (!requireMouseDown.value || Minecraft.getInstance().options.keyUse.isDown)
            ) {
                pressUseKey()
                manualReleaseNextTick = true
            }
            return
        }
        if (mode.isMode("Auto")) return
        val player = Minecraft.getInstance().player ?: return
        if (target !== player && target.hurtTime <= 11
            && target.distanceTo(player) <= predictDistance.value && withinPredictionAngle(target)
        ) {
            predictedTarget = target
        }
        triggerUseForTarget()
    }

    private fun triggerUseForTarget() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (!isReady() || requireMouseDown.value && !mc.options.keyUse.isDown) return
        val hit = mc.hitResult as? EntityHitResult ?: return
        val target = hit.entity as? LivingEntity ?: return
        val expectedHurtTime = if (target.hurtDuration > 0) target.hurtDuration else 10
        if (!useKeyPressed && !player.isUsingItem && target.hurtTime <= expectedHurtTime + 1
            && target.hurtTime <= expectedHurtTime && findTarget() === target
        ) {
            predictedTarget = target
            pressUseKey()
            releaseTime = System.currentTimeMillis() + 50L * (expectedHurtTime + 1L)
        }
    }

    private fun tickAuto() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (requireMouseDown.value && !mc.options.keyUse.isDown) {
            targetStreak = 1
            setUseKeyPressed(false)
            return
        }
        val hit = mc.hitResult as? EntityHitResult ?: return
        val targeting = hit.entity is LivingEntity && player.distanceTo(hit.entity) <= predictDistance.value
        if (!targeting) {
            targetStreak = 1
            setUseKeyPressed(false)
            return
        }
        val shouldBlock = targetStreak == 1
        targetStreak = if (targetStreak >= 3) 0 else targetStreak + 1
        if (shouldBlock) pressUseKey() else setUseKeyPressed(false)
    }

    private fun pressUseKey() {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (useKeyPressed) return
        useKeyWasDown = mc.options.keyUse.isDown
        useKeyPressed = true
        mc.options.keyUse.setDown(true)
        mc.gameMode?.useItem(player, InteractionHand.MAIN_HAND)
    }

    private fun setUseKeyPressed(pressed: Boolean) {
        if (useKeyPressed == pressed) return
        useKeyPressed = pressed
        releaseTime = 0L
        damageTriggered = false
        Minecraft.getInstance().options.keyUse.setDown(pressed || useKeyWasDown)
        if (!pressed) useKeyWasDown = false
    }

    private fun isReady(): Boolean {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        return mc.connection != null
            && player.mainHandItem.`is`(ItemTags.SWORDS)
            && mc.gui.screen() == null
    }

    private fun findTarget(): LivingEntity? {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return null
        val level = mc.level ?: return null
        var best: LivingEntity? = null
        var bestDistance = Float.MAX_VALUE
        for (entity in level.entitiesForRendering()) {
            if (entity !is LivingEntity || entity === player || !entity.isAlive) continue
            val distance = entity.distanceTo(player)
            if (distance > predictDistance.value || !withinPredictionAngle(entity)) continue
            if (distance < bestDistance) {
                best = entity
                bestDistance = distance
            }
        }
        return best
    }

    private fun withinPredictionAngle(target: LivingEntity): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val dx = target.x - player.x
        val dz = target.z - player.z
        val targetYaw = (Math.toDegrees(atan2(dz, dx)) - 90.0).toFloat()
        return abs(Mth.wrapDegrees(targetYaw - player.yRot)) <= predictAngle.value / 2.0
    }

    override fun onEnable() {
        activeMode = mode.value
        setUseKeyPressed(false)
    }

    override fun onDisable() {
        setUseKeyPressed(false)
        predictedTarget = null
        targetStreak = 1
        manualReleaseNextTick = false
        resetAfterLivingUpdate = false
        releaseTime = 0L
    }
}
