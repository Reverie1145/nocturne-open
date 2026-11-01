package cn.reverie.module

import cn.reverie.gui.clickgui.ClickGUIModule
import cn.reverie.module.combat.AimAssist
import cn.reverie.module.combat.AutoClicker
import cn.reverie.module.combat.AntiBot
import cn.reverie.module.combat.Backtrack
import cn.reverie.module.combat.BlockHit
import cn.reverie.module.combat.BowAim
import cn.reverie.module.combat.Criticals
import cn.reverie.module.combat.KillAura
import cn.reverie.module.combat.KnockbackDisplace
import cn.reverie.module.combat.ProjectileAura
import cn.reverie.module.combat.Velocity
import cn.reverie.module.misc.AccountManagerModule
import cn.reverie.module.misc.AutoReconnect
import cn.reverie.module.misc.BanTracker
import cn.reverie.module.misc.Crash
import cn.reverie.module.misc.Disabler
import cn.reverie.module.misc.FlagDeleter
import cn.reverie.module.misc.GhostHand
import cn.reverie.module.misc.MusicPlayerModule
import cn.reverie.module.misc.YuriGallery
import cn.reverie.module.movement.Fly
import cn.reverie.module.movement.AutoJump
import cn.reverie.module.movement.AutoWalk
import cn.reverie.module.movement.InventoryMove
import cn.reverie.module.movement.Sprint
import cn.reverie.module.movement.GrimSpeed
import cn.reverie.module.movement.NoFallProbe
import cn.reverie.module.movement.NoSlow
import cn.reverie.module.movement.Stuck
import cn.reverie.module.player.AutoTool
import cn.reverie.module.player.AutoEat
import cn.reverie.module.player.AutoFish
import cn.reverie.module.player.AutoRespawn
import cn.reverie.module.player.DeathCoords
import cn.reverie.module.player.DurabilityAlert
import cn.reverie.module.player.InventoryFullAlert
import cn.reverie.module.player.LowHealthAlert
import cn.reverie.module.player.AutoMLG
import cn.reverie.module.player.BedBreaker
import cn.reverie.module.player.BedDefender
import cn.reverie.module.player.ChestAura
import cn.reverie.module.player.ChestStealer
import cn.reverie.module.player.InvManager
import cn.reverie.module.player.Teams
import cn.reverie.module.render.Animations
import cn.reverie.module.render.BedESP
import cn.reverie.module.render.ChestESP
import cn.reverie.module.render.ChatBarModule
import cn.reverie.module.render.ESP
import cn.reverie.module.render.Fullbright
import cn.reverie.module.render.Xray
import cn.reverie.module.render.ItemPhysics
import cn.reverie.module.render.NameTags
import cn.reverie.module.render.NameProtect
import cn.reverie.module.render.NoHurtCam
import cn.reverie.module.render.MotionCamera
import cn.reverie.module.render.NotificationModule
import cn.reverie.module.world.Scaffold
import cn.reverie.module.world.TimeChanger
import cn.reverie.module.world.WeatherChanger
import cn.reverie.render.hud.HUDModule
import cn.reverie.util.rotation.RotationProfile
import org.lwjgl.glfw.GLFW
object ModuleManager {

    private val moduleList = mutableListOf<Module>()
    val modules: List<Module> get() = moduleList
    var revision: Long = 0
        private set

    init {
        registerDefaultModules()
        RotationProfile.configure(
            instantRotation = { Scaffold.isInstantRotation() },
            scaffoldRotations = { Scaffold.rotations }
        )
    }

    private fun registerDefaultModules() {

        register(ClickGUIModule)
        register(HUDModule)
        register(ESP)
        register(BedESP)
        register(ChestESP)
        register(Xray)
        register(ChatBarModule)
        register(NameTags)
        register(NameProtect)
        register(Animations)
        register(NotificationModule)
        register(Fullbright)
        register(NoHurtCam)
        register(MotionCamera)
        register(ItemPhysics)

        register(KillAura)
        register(Criticals)
        register(BlockHit)
        register(AimAssist)
        register(AutoClicker)
        register(AntiBot)
        register(Backtrack)
        register(Velocity)
        register(KnockbackDisplace)
        register(ProjectileAura)
        register(BowAim)

        register(Sprint)
        register(AutoJump)
        register(AutoWalk)
        register(InventoryMove)
        register(Fly)
        register(NoSlow)
        register(GrimSpeed)
        register(Stuck)
        register(NoFallProbe)

        register(Scaffold)
        register(TimeChanger)
        register(WeatherChanger)

        register(ChestAura)
        register(ChestStealer)
        register(InvManager)
        register(Teams)
        register(AutoTool)
        register(AutoEat)
        register(AutoFish)
        register(AutoRespawn)
        register(DeathCoords)
        register(DurabilityAlert)
        register(InventoryFullAlert)
        register(LowHealthAlert)
        register(BedBreaker)
        register(BedDefender)
        register(AutoMLG)

        register(Disabler)
        register(Crash)
        register(FlagDeleter)
        register(AutoReconnect)
        register(GhostHand)
        register(YuriGallery)
        register(AccountManagerModule)
        register(MusicPlayerModule)
        register(cn.reverie.module.misc.AIAgentModule)
        register(BanTracker)
    }

    fun register(module: Module) {
        if (!moduleList.contains(module)) {
            moduleList.add(module)
            revision++
        }
    }

    /** Dynamic script modules are removed on their main-thread lifecycle. */
    fun unregister(module: Module) {
        if (module.enabled) module.enabled = false
        if (moduleList.remove(module)) revision++
    }

    fun getModulesByCategory(category: Category): List<Module> {
        return moduleList.filter { it.category == category }
    }

    fun getModuleByName(name: String): Module? {
        return moduleList.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    fun onKey(keyCode: Int) {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN) return
        for (module in moduleList.toList()) {
            if (module.keyCode == keyCode) {
                module.toggle()
            }
        }
    }
}
