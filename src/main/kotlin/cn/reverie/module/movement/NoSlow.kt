package cn.reverie.module.movement

import cn.reverie.app.event.packet.PacketEvent
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.module.combat.KillAura
import cn.reverie.module.movement.noslow.GrimNoSlow
import cn.reverie.module.movement.noslow.HypixelBNoSlow
import cn.reverie.module.movement.noslow.HypixelNoSlow
import cn.reverie.module.movement.noslow.NoSlowMode
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer

object NoSlow : Module(
    name = "NoSlow",
    description = "Cancels slowdown while using items",
    category = Category.MOVEMENT
) {
    val mode = ModeSetting("Mode", "NoSlow implementation", "Grim", listOf("Grim", "HypixelA", "HypixelB"))

    val releaseTicks = NumberSetting("Release Ticks", "Ticks before a held use is released", 32.0, 0.0, 150.0, 1.0)
    val bow = BooleanSetting("Bow", "Include bows", true)
    val crossbow = BooleanSetting("Crossbow", "Include crossbows", true)
    val sword = BooleanSetting("Sword", "Include swords", true)
    val keepSprinting = BooleanSetting("Keep Sprinting", "Keep sprinting while blocking", true)

    val slowInterval = NumberSetting("Slow Interval", "Ticks between slowdown windows", 0.0, 0.0, 5.0, 1.0)
    val boost = BooleanSetting("Boost", "Micro boost while blocking", true)
    val slotResync = BooleanSetting("Slot Resync", "Resynchronise the held slot", false)
    val c09Interval = NumberSetting("C09 Interval", "Ticks between slot resync cycles", 2.0, 2.0, 40.0, 1.0)
    val slotRelease = BooleanSetting("C08+C07", "Use release packets instead of slot packets", false)
    val backFullTick = BooleanSetting("BACK Full Tick", "Full tick on the resync return", false)
    val blink = BooleanSetting("Blink", "Hold movement packets during the resync", false)
    val blinkTicks = NumberSetting("Blink Ticks", "Ticks to hold movement packets", 3.0, 1.0, 8.0, 1.0)
    val blinkBlock = BooleanSetting("Blink Block", "Blink on the resync return", false)
    val timerBurst = BooleanSetting("Timer Burst", "Burst the client timer while blocking", false)
    val burstTicks = NumberSetting("Burst Ticks", "Ticks per timer burst", 8.0, 4.0, 20.0, 1.0)
    val maxTimer = NumberSetting("Max Timer", "Highest timer multiplier", 1.5, 1.05, 2.0, 0.05)
    val safetyMs = NumberSetting("Safety", "Milliseconds reserved before a burst", 30.0, 10.0, 60.0, 5.0)

    private val modes: List<NoSlowMode> = listOf(GrimNoSlow, HypixelNoSlow, HypixelBNoSlow)
    private var active: NoSlowMode? = null

    init {
        listen(PacketEvent::class.java) { event -> availableMode()?.onPacket(event) }
        releaseTicks.visibility = { mode.isMode("Grim") }
        bow.visibility = { mode.isMode("Grim") }
        crossbow.visibility = { mode.isMode("Grim") }
        sword.visibility = { mode.isMode("HypixelB") }
        keepSprinting.visibility = { mode.isMode("HypixelB") }
        slowInterval.visibility = { mode.isMode("HypixelA") }
        boost.visibility = { mode.isMode("HypixelA") }
        slotResync.visibility = { mode.isMode("HypixelA") }
        c09Interval.visibility = { mode.isMode("HypixelA") }
        slotRelease.visibility = { mode.isMode("HypixelA") }
        backFullTick.visibility = { mode.isMode("HypixelA") }
        blink.visibility = { mode.isMode("HypixelA") }
        blinkTicks.visibility = { mode.isMode("HypixelA") }
        blinkBlock.visibility = { mode.isMode("HypixelA") }
        timerBurst.visibility = { mode.isMode("HypixelA") }
        burstTicks.visibility = { mode.isMode("HypixelA") }
        maxTimer.visibility = { mode.isMode("HypixelA") }
        safetyMs.visibility = { mode.isMode("HypixelA") }
    }

    @JvmStatic
    fun releaseTicks(): Int = releaseTicks.getInt()

    @JvmStatic
    fun bowEnabled(): Boolean = bow.value

    @JvmStatic
    fun crossbowEnabled(): Boolean = crossbow.value

    private fun selected(): NoSlowMode =
        modes.firstOrNull { it.modeName.equals(mode.value, ignoreCase = true) } ?: modes[0]

    private fun currentMode(): NoSlowMode {
        val selected = selected()
        if (active !== selected) {
            active?.onDisable()
            active = selected
            selected.onEnable()
        }
        return selected
    }

    private fun availableMode(player: LocalPlayer? = Minecraft.getInstance().player): NoSlowMode? {
        if (!enabled) return null
        // WatchdogBW owns sword-use packets; finish an existing NoSlow session
        // before giving it control, rather than running two packet cycles together.
        if (player != null && KillAura.wantsRealBlock()) {
            suspendMode()
            return null
        }
        return currentMode()
    }

    private fun suspendMode() {
        val previous = active ?: return
        // Clear before cleanup: queued packet flushes can re-enter the packet listener.
        active = null
        previous.onDisable()
    }

    /** Finish packet cleanup before Aura can send an attack or sword-use action. */
    fun prepareAuraControl(player: LocalPlayer) {
        if (enabled && KillAura.shouldPauseSprint(player)) suspendMode()
    }

    @JvmStatic
    fun onTick() {
        availableMode()?.onTick()
    }

    @JvmStatic
    fun onPreUpdate() {
        availableMode()?.onPreUpdate()
    }

    @JvmStatic
    fun onPreMotion(player: LocalPlayer) {
        availableMode(player)?.onPreMotion(player)
    }

    @JvmStatic
    fun onSendPosition(player: LocalPlayer, yaw: Float, pitch: Float) {
        availableMode(player)?.onSendPosition(yaw, pitch)
    }

    @JvmStatic
    fun onItemUseSlowdown(original: Float): Float {
        val player = Minecraft.getInstance().player
        if (enabled && player != null && KillAura.wantsRealBlock()) {
            suspendMode()
            // Aura sends real sword use before this movement. Its server-side use
            // state requires the same slowdown until the cycle sends a release.
            return original
        }
        return availableMode(player)?.onItemUseSlowdown(original) ?: original
    }

    @JvmStatic
    fun onClick() {
        availableMode()?.onClick()
    }

    @JvmStatic
    fun onWorldChange() {
        if (!enabled) return
        active?.onWorldChange()
    }

    override fun onEnable() {
        availableMode()
    }

    override fun onDisable() {
        suspendMode()
    }
}
