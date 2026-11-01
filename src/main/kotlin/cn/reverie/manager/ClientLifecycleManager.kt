package cn.reverie.manager

import cn.reverie.app.account.AccountManager
import cn.reverie.app.config.ConfigManager
import cn.reverie.gui.splash.ProtectionSplashWindow
import cn.reverie.module.combat.AutoClicker
import cn.reverie.module.combat.Backtrack
import cn.reverie.module.combat.BlockHit
import cn.reverie.module.combat.Criticals
import cn.reverie.module.combat.KillAura
import cn.reverie.module.combat.Velocity
import cn.reverie.module.misc.Disabler
import cn.reverie.module.misc.AutoReconnect
import cn.reverie.module.movement.AutoJump
import cn.reverie.module.movement.NoSlow
import cn.reverie.module.player.AutoTool
import cn.reverie.module.player.AutoEat
import cn.reverie.module.player.AutoFish
import cn.reverie.module.player.AutoRespawn
import cn.reverie.module.player.DeathCoords
import cn.reverie.module.player.DurabilityAlert
import cn.reverie.module.player.InventoryFullAlert
import cn.reverie.module.player.LowHealthAlert
import cn.reverie.module.player.BedBreaker
import cn.reverie.module.player.BedDefender
import cn.reverie.module.player.ChestStealer
import cn.reverie.module.player.InvManager
import cn.reverie.module.render.ESP
import cn.reverie.module.world.Scaffold
import cn.reverie.nocturne.NocturneAuth
import cn.reverie.render.core.RenderEngine
import cn.reverie.util.player.HumanClicker
import cn.reverie.util.player.VanillaClickQueue
import cn.reverie.util.misc.ClientTimer
import net.minecraft.client.Minecraft
import net.minecraft.client.DeltaTracker
import net.minecraft.world.entity.Entity

/** Composition entry points invoked by client lifecycle mixins. */
object ClientLifecycleManager {
    @JvmStatic fun awaitStartupSplash() = ProtectionSplashWindow.awaitSplash()

    @JvmStatic fun applyTickTargetMillis(original: Float): Float =
        Criticals.applyTickTargetMillis(ClientTimer.applyTickTargetMillis(original))

    @JvmStatic fun shouldSuppressAuraUseItem(): Boolean = KillAura.shouldSuppressUseItem()
    @JvmStatic fun shouldSuppressAuraAttackInput(): Boolean = KillAura.shouldSuppressAttackInput()
    @JvmStatic fun auraOwnsUseItem(): Boolean = KillAura.ownsUseItem()

    @JvmStatic
    fun initializeClient() {
        RenderEngine.init()
        ConfigManager.init()
        AccountManager.init()
        NocturneAuth.startSecurity()
        cn.reverie.app.agent.AgentManager.init()
    }

    @JvmStatic
    fun tickClient() {
        cn.reverie.app.agent.AgentManager.onTick()
        Minecraft.getInstance().player?.let(BedDefender::onClientTick)
        RenderEngine.onTick()
        BlockHit.onTick()
        NoSlow.onTick()
        BedBreaker.onTick()
        ChestStealer.onUpdate()
        InvManager.onUpdate()
        AutoTool.onTick()
        AutoJump.onTick()
        AutoEat.onTick()
        AutoFish.onTick()
        AutoRespawn.onTick()
        DeathCoords.onTick()
        DurabilityAlert.onTick()
        InventoryFullAlert.onTick()
        LowHealthAlert.onTick()
        AutoReconnect.onTick()
        cn.reverie.app.account.AccountBanTracker.onTick()
        HumanClicker.onTick()
    }

    @JvmStatic
    fun postTickClient() {
        BlockHit.onPostTick()
    }

    @JvmStatic
    fun queueHumanClicks() {
        NoSlow.onClick()
        Scaffold.queueHumanClicks()
        KillAura.queueHumanClicks()
        AutoClicker.queueClicks()
    }

    @JvmStatic
    fun restoreHumanCrosshair() = VanillaClickQueue.restoreHit(Minecraft.getInstance())

    @JvmStatic
    fun renderFrame(deltaTracker: DeltaTracker) {
        HumanClicker.onRenderFrame()
        RenderEngine.onRender2D(deltaTracker)
    }

    @JvmStatic
    fun shouldEntityGlow(entity: Entity): Boolean = ESP.shouldGlow(entity)

    @JvmStatic
    fun onWorldChanged() {
        cn.reverie.app.agent.AgentManager.onWorldChanged()
        Criticals.onWorldChange()
        BlockHit.onWorldChange()
        NoSlow.onWorldChange()
        Scaffold.onWorldChange()
        Disabler.onWorld()
        Velocity.reset()
        KillAura.onWorldChange()
        Backtrack.reset()
        AutoTool.cleanReset()
        AutoEat.onWorldChange()
        AutoFish.onWorldChange()
        AutoRespawn.onWorldChange()
        DeathCoords.onWorldChange()
        DurabilityAlert.onWorldChange()
        InventoryFullAlert.onWorldChange()
        LowHealthAlert.onWorldChange()
        BedBreaker.onWorldChange()
        BedDefender.onWorldChange()
    }

    @JvmStatic
    fun shutdownClient() {
        cn.reverie.app.agent.AgentManager.shutdown()
        AccountManager.save()
        ConfigManager.saveActiveConfig()
        RenderEngine.destroy()
    }
}
