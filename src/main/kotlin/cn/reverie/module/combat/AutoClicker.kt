package cn.reverie.module.combat

import cn.reverie.module.*
import cn.reverie.mixin.input.KeyMappingAccessor
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import kotlin.random.Random

object AutoClicker : Module("AutoClicker", "Automatically clicks while attack is held", Category.COMBAT) {
    val minCps = NumberSetting("Min CPS", "Minimum clicks per second", 8.0, 1.0, 25.0, 1.0)
    val maxCps = NumberSetting("Max CPS", "Maximum clicks per second", 12.0, 1.0, 25.0, 1.0)
    val onlyEntity = BooleanSetting("Only Entity", "Click only while pointing at an entity", false)
    private var nextClick = 0L
    override fun onEnable() { nextClick = 0L }
    override fun onDisable() { nextClick = 0L }
    fun queueClicks() {
        val mc = Minecraft.getInstance()
        if (!enabled || KillAura.enabled || mc.player == null || mc.gui.screen() != null || !mc.options.keyAttack.isDown) return
        if (onlyEntity.value && mc.hitResult?.type != net.minecraft.world.phys.HitResult.Type.ENTITY) return
        val now = System.currentTimeMillis()
        if (now < nextClick) return
        val low = minCps.value.coerceAtMost(maxCps.value)
        val high = maxCps.value.coerceAtLeast(minCps.value)
        val cps = Random.nextDouble(low, high + 0.001)
        nextClick = now + (1000.0 / cps).toLong().coerceAtLeast(1L)
        KeyMapping.click((mc.options.keyAttack as KeyMappingAccessor).nocturneGetKey())
    }
}

