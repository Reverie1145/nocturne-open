package cn.reverie.module.misc

import cn.reverie.module.misc.disabler.DisablerRotationRules

import cn.reverie.app.event.packet.PacketEvent
import cn.reverie.module.*
import cn.reverie.module.world.Scaffold
import cn.reverie.module.player.InvManager
import cn.reverie.util.misc.MathUtil
import cn.reverie.util.misc.TimerUtil
import cn.reverie.util.network.PacketUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket
import net.minecraft.network.protocol.game.*
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.player.Input
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import org.lwjgl.glfw.GLFW
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.max
object Disabler : Module(
    name = "Disabler",
    description = "Disables anticheat checks and bypasses server detections",
    category = Category.MISC,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {

    val mode = ModeSetting(
        "Mode",
        "Target AntiCheat profile",
        "Custom",
        listOf("Custom", "Heypixel", "Grim", "ACA", "Hypixel", "Verus", "Vulcan")
    )

    val heypixelHealthCorrection = BooleanSetting("Heypixel Health", "Correct player health displays on Heypixel", false, { mode.isMode("Custom") || mode.isMode("Heypixel") })
    val heypixelProtocol = BooleanSetting("Heypixel Protocol", "Prevent team scoreboard disconnects on Heypixel", false, { mode.isMode("Custom") || mode.isMode("Heypixel") })

    val grimBadPacketsA = BooleanSetting("BadPackets A", "Prevent duplicate slot update packets", false, { mode.isMode("Custom") || mode.isMode("Grim") })
    val grimAimDuplicateLook = BooleanSetting("Aim Duplicate Look", "Jitter identical yaw/pitch look packets", false, { mode.isMode("Custom") || mode.isMode("Grim") })
    val grimDuplicateRotPlace = BooleanSetting("Duplicate Rot Place", "Prevent duplicate rotation deltas on block place", false, { mode.isMode("Custom") || mode.isMode("Grim") })
    val grimAim360 = BooleanSetting("Aim 360", "Add 720 degrees to yaw to bypass 360 checks", false, { mode.isMode("Custom") || mode.isMode("Grim") })

    val acaFastSwitch = BooleanSetting("Fast Switch", "Send intermediate slot packets during rapid switch", false, { mode.isMode("Custom") || mode.isMode("ACA") })
    val acaInventoryFrequency = BooleanSetting("Inventory Frequency", "Delay close screen packet if closed too quickly", false, { mode.isMode("Custom") || mode.isMode("ACA") })
    val acaAimStep = BooleanSetting("Aim Step", "Add micro-jitter to one-axis aim step packets", false, { mode.isMode("Custom") || mode.isMode("ACA") })
    val acaPerfectRotation = BooleanSetting("Perfect Rotation", "Disrupt mathematically perfect rotation increments", false, { mode.isMode("Custom") || mode.isMode("ACA") })

    val hypixelInventoryMove = BooleanSetting("Hypixel InvMove", "Bypass Hypixel inventory move check", true, { mode.isMode("Hypixel") })

    val verusKeepAlive = BooleanSetting("Verus KeepAlive", "Disrupt Verus KeepAlive checks", false, { mode.isMode("Custom") || mode.isMode("Verus") })
    val vulcanScaffold = BooleanSetting("Vulcan Scaffold", "Bypass Vulcan Scaffold check with sprint/sneak reset", false, { mode.isMode("Custom") || mode.isMode("Vulcan") })
    val mineplexAbilities = BooleanSetting("Mineplex Fly", "Cancel flying abilities packet for Mineplex", false, { mode.isMode("Custom") })

    private val healths = ConcurrentHashMap<String, AtomicInteger>()

    private var storedClosePacket: ServerboundContainerClosePacket? = null
    private val inventoryCloseTimer = TimerUtil()
    private var inventoryJustOpened = false
    private var lastInventoryOpenTime: Long = 0L
    private var delayTime: Long = 0L
    private var lastSlot = -1

    private val random = Random()

    private var lastSentPitch = 0f
    private var lastSentYaw = 0f
    private var lastPitch = 0f
    private var lastYaw = 0f

    private var lastPlacedDeltaPitch = 0f
    private var lastPlacedDeltaYaw = 0f
    private var playerPitch = 0f
    private var playerYaw = 0f
    private var deltaPitch = 0f
    private var deltaYaw = 0f
    private var rotated = false

    private var sentFirstOpen = false
    private var caughtClientStatus = false
    private var caughtCloseWindow = false

    override val suffix: String
        get() = mode.value

    init {
        listen(PacketEvent::class.java) { onPacket(it) }
        mode.onChanged = { resetInventoryMoveState() }
        hypixelInventoryMove.onChanged = { resetInventoryMoveState() }
    }

    override fun onEnable() {
        reset()
    }

    override fun onDisable() {
        val closePkt = storedClosePacket
        if (closePkt != null) {
            PacketUtil.sendPacketNoEvent(closePkt)
            storedClosePacket = null
        }
        reset()
    }

    @JvmStatic
    fun isHeypixelProtocolEnabled(): Boolean {
        return enabled && (heypixelProtocol.value || mode.isMode("Heypixel"))
    }

    @JvmStatic
    fun onWorld() {
        healths.clear()
        reset()
    }

    @JvmStatic
    fun onLivingUpdate(player: LocalPlayer) {
    }

    @JvmStatic
    fun onUpdate(player: LocalPlayer) {
        if (!enabled) return
        val mc = Minecraft.getInstance()

        if (heypixelHealthCorrection.value || mode.isMode("Heypixel")) {
            val level = mc.level
            if (level != null) {
                for (entity in level.entitiesForRendering()) {
                    if (entity is Player && entity !== player) {
                        val atomic = healths[entity.name.string]
                        if (atomic != null) {
                            entity.health = max(1f, atomic.get().toFloat())
                        }
                    }
                }
            }
        }

        val closePkt = storedClosePacket
        if (closePkt != null && inventoryCloseTimer.hasTimeElapsed(delayTime)) {
            PacketUtil.sendPacketNoEvent(closePkt)
            storedClosePacket = null
        }

        if (isHypixelInventoryMoveActive()) {
            if (isInventoryMovePausedForChest()) {
                resetInventoryMoveState()
            } else {
                caughtClientStatus = false
                caughtCloseWindow = false

                val inInventory = mc.gui.screen() is InventoryScreen || (InvManager.enabled && InvManager.cleaning)
                if (inInventory) {
                    if (!sentFirstOpen) {
                        PacketUtil.sendPacketNoEvent(ServerboundContainerClosePacket(0))
                        sentFirstOpen = true
                    }

                    val mod = if (player.hasEffect(MobEffects.SPEED)) 3 else 4
                    if (player.tickCount % mod == 0) {
                        PacketUtil.sendPacketNoEvent(ServerboundContainerClosePacket(0))
                    } else if (player.tickCount % mod == 1) {
                        PacketUtil.sendPacket(
                            ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.OPEN_INVENTORY)
                        )
                    }
                } else {
                    sentFirstOpen = false
                }
            }
        }

        if ((vulcanScaffold.value || mode.isMode("Vulcan")) && Scaffold.enabled && player.tickCount % 15 == 0 &&
            !player.isInWater && !player.isDeadOrDying && !player.onClimbable()
        ) {
            PacketUtil.sendPacket(ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING))
            PacketUtil.sendPacket(ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING))
            PacketUtil.sendPacket(ServerboundPlayerInputPacket(Input(false, false, false, false, false, true, false)))
            PacketUtil.sendPacket(ServerboundPlayerInputPacket(Input.EMPTY))
        }
    }

    private fun onPacket(event: PacketEvent) {
        if (!enabled) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val packet = event.packet

        if (event.isSend) {

            if (packet is ServerboundSetCarriedItemPacket) {
                val targetSlot = packet.slot

                if ((grimBadPacketsA.value || mode.isMode("Grim")) && targetSlot == lastSlot && targetSlot != -1) {
                    event.cancel()
                    return
                }

                if ((acaFastSwitch.value || mode.isMode("ACA")) && lastSlot != -1 && targetSlot != lastSlot) {
                    generateIntermediateSlots(lastSlot, targetSlot)
                }

                lastSlot = targetSlot
            }

            if (grimAimDuplicateLook.value || mode.isMode("Grim")) {
                if (packet is ServerboundMovePlayerPacket && packet.hasRotation()) {
                    val yaw = packet.getYRot(0f)
                    val pitch = packet.getXRot(0f)

                    if (yaw == lastSentYaw && pitch == lastSentPitch) {
                        val baseRange = if (isPlayerMoving(player)) 0.0003f else 0.00015f
                        val yawRange = baseRange * (0.8f + random.nextFloat() * 0.4f)
                        val pitchRange = baseRange * (0.8f + random.nextFloat() * 0.4f)

                        var yawOffset = (random.nextGaussian() * yawRange * 0.3f).toFloat()
                        var pitchOffset = (random.nextGaussian() * pitchRange * 0.3f).toFloat()

                        yawOffset = MathUtil.clamp(yawOffset, -0.0005f, 0.0005f)
                        pitchOffset = MathUtil.clamp(pitchOffset, -0.0003f, 0.0003f)

                        val newYaw = yaw + yawOffset
                        val newPitch = MathUtil.clamp(pitch + pitchOffset, -90f, 90f)

                        PacketUtil.setPlayerMovePacketYaw(packet, newYaw)
                        PacketUtil.setPlayerMovePacketPitch(packet, newPitch)
                    }

                    lastSentYaw = packet.getYRot(0f)
                    lastSentPitch = packet.getXRot(0f)
                }
            }

            if (grimDuplicateRotPlace.value || mode.isMode("Grim")) {
                if (packet is ServerboundMovePlayerPacket && packet.hasRotation()) {
                    val lastPlayerYaw = playerYaw
                    val lastPlayerPitch = playerPitch

                    playerYaw = packet.getYRot(0f)
                    playerPitch = packet.getXRot(0f)

                    deltaYaw = abs(playerYaw - lastPlayerYaw)
                    deltaPitch = abs(playerPitch - lastPlayerPitch)
                    rotated = true

                    if (deltaYaw > 2f) {
                        val yawDiff = abs(deltaYaw - lastPlacedDeltaYaw)
                        if (yawDiff < 0.0001f) {
                            val randomOffset = (0.001f + random.nextFloat() * 0.009f)
                            PacketUtil.setPlayerMovePacketYaw(packet, playerYaw - randomOffset)
                        }
                    }

                    if (deltaPitch > 2f) {
                        val pitchDiff = abs(deltaPitch - lastPlacedDeltaPitch)
                        if (pitchDiff < 0.0001f) {
                            val randomOffset = (0.001f + random.nextFloat() * 0.009f)
                            PacketUtil.setPlayerMovePacketPitch(packet, MathUtil.clamp(playerPitch - randomOffset, -90f, 90f))
                        }
                    }
                } else if (packet is ServerboundUseItemOnPacket) {
                    if (rotated) {
                        lastPlacedDeltaYaw = deltaYaw
                        lastPlacedDeltaPitch = deltaPitch
                        rotated = false
                    }
                }
            }

            if (grimAim360.value || mode.isMode("Grim")) {
                if (packet is ServerboundMovePlayerPacket && packet.hasRotation()) {
                    val yaw = packet.getYRot(0f)
                    if (yaw < 360f && yaw > -360f) {
                        PacketUtil.setPlayerMovePacketYaw(packet, yaw + 720f)
                    }
                }
            }

            if (acaInventoryFrequency.value || mode.isMode("ACA")) {
                if (packet is ServerboundContainerClosePacket && inventoryJustOpened) {
                    val currentTime = System.currentTimeMillis()
                    val timeSinceOpen = currentTime - lastInventoryOpenTime

                    if (timeSinceOpen <= 150L) {
                        event.cancel()
                        storedClosePacket = packet
                        delayTime = 151L - timeSinceOpen
                        inventoryCloseTimer.reset()
                        inventoryJustOpened = false
                        return
                    }

                    inventoryJustOpened = false
                }
            }

            val aimStepActive = acaAimStep.value || mode.isMode("ACA")
            val perfectRotActive = acaPerfectRotation.value || mode.isMode("ACA")

            if (aimStepActive || perfectRotActive) {
                if (packet is ServerboundMovePlayerPacket && packet.hasRotation()) {
                    var currentYaw = packet.getYRot(0f)
                    var currentPitch = packet.getXRot(0f)
                    var modified = false

                    if (aimStepActive && DisablerRotationRules.shouldModifyRotation(currentYaw, currentPitch, lastYaw, lastPitch)) {
                        val mod = DisablerRotationRules.getModifiedRotation(currentYaw, currentPitch, lastYaw, lastPitch, random)
                        currentYaw = mod[0]
                        currentPitch = mod[1]
                        modified = true
                    }

                    if (perfectRotActive) {
                        val anti = DisablerRotationRules.getAntiPerfectRotation(currentYaw, currentPitch, lastYaw, lastPitch, random)
                        if (anti[0] != currentYaw || anti[1] != currentPitch) {
                            currentYaw = anti[0]
                            currentPitch = anti[1]
                            modified = true
                        }
                    }

                    if (modified) {
                        PacketUtil.setPlayerMovePacketYaw(packet, currentYaw)
                        PacketUtil.setPlayerMovePacketPitch(packet, MathUtil.clamp(currentPitch, -90f, 90f))
                    }

                    lastYaw = packet.getYRot(0f)
                    lastPitch = packet.getXRot(0f)
                }
            }

            if (isHypixelInventoryMoveActive()) {
                if (!isInventoryMovePausedForChest()) {
                    if (packet is ServerboundClientCommandPacket) {
                        if (caughtClientStatus) {
                            event.cancel()
                        }
                        caughtClientStatus = true
                    }
                    if (packet is ServerboundContainerClosePacket) {
                        if (caughtCloseWindow) {
                            event.cancel()
                        }
                        caughtCloseWindow = true
                    }
                }
            }

            if (verusKeepAlive.value || mode.isMode("Verus")) {
                if (packet is ServerboundKeepAlivePacket) {
                    event.cancel()
                    val newId = packet.id - (random.nextInt(100000) + 1)
                    PacketUtil.sendPacketNoEvent(ServerboundKeepAlivePacket(newId))
                } else if (packet is ServerboundMovePlayerPacket) {
                    PacketUtil.sendPacket(ServerboundPlayerInputPacket(Input.EMPTY))
                }
            }

            if (mineplexAbilities.value || mode.isMode("Mineplex")) {
                if (packet is ServerboundPlayerAbilitiesPacket && packet.isFlying) {
                    event.cancel()
                }
            }

            val closePkt = storedClosePacket
            if (closePkt != null && inventoryCloseTimer.hasTimeElapsed(delayTime)) {
                PacketUtil.sendPacketNoEvent(closePkt)
                storedClosePacket = null
            }
        }

        if (event.isReceive) {

            if (packet is ClientboundOpenScreenPacket) {
                lastInventoryOpenTime = System.currentTimeMillis()
                inventoryJustOpened = true
            }

            if (packet is ClientboundSetScorePacket) {
                if (heypixelHealthCorrection.value || mode.isMode("Heypixel")) {
                    try {
                        val objective = packet.objectiveName()
                        if ("belowHealth" == objective || "health" == objective) {
                            val name = packet.owner()
                            healths.computeIfAbsent(name) { AtomicInteger() }.set(packet.score())
                        }
                    } catch (_: Exception) {
                        event.cancel()
                    }
                }
            }
        }
    }

    private fun generateIntermediateSlots(from: Int, to: Int) {
        val diff = abs(from - to)
        if (diff > 1 && !isSpecialJump(from, to)) {
            val direction = if (from > to) -1 else 1
            var middle = from + direction
            while (middle != to) {
                if (middle in 0..8) {
                    PacketUtil.sendPacketNoEvent(ServerboundSetCarriedItemPacket(middle))
                }
                middle += direction
            }
        }
    }

    private fun isSpecialJump(from: Int, to: Int): Boolean {
        return (from == 0 && to == 8) || (from == 8 && to == 0)
    }

    private fun isPlayerMoving(player: LocalPlayer): Boolean {
        return player.deltaMovement.lengthSqr() > 0.001
    }

    private fun isHypixelInventoryMoveActive(): Boolean = enabled && mode.isMode("Hypixel") && hypixelInventoryMove.value

    @JvmStatic
    fun isInventoryMovePausedForChest(): Boolean {
        if (!isHypixelInventoryMoveActive()) return false
        val player = Minecraft.getInstance().player ?: return false
        val level = Minecraft.getInstance().level ?: return false

        val hitResult = player.pick(5.0, 0.0f, false)
        if (hitResult.type != HitResult.Type.BLOCK) return false

        val blockHitResult = hitResult as BlockHitResult
        return level.getBlockState(blockHitResult.blockPos).block is ChestBlock
    }

    private fun resetInventoryMoveState() {
        sentFirstOpen = false
        caughtClientStatus = false
        caughtCloseWindow = false
    }

    fun reset() {
        lastSlot = -1
        lastInventoryOpenTime = 0L
        inventoryJustOpened = false
        storedClosePacket = null
        delayTime = 0L
        lastYaw = 0f
        lastPitch = 0f
        lastSentYaw = 0f
        lastSentPitch = 0f
        playerYaw = 0f
        playerPitch = 0f
        deltaYaw = 0f
        deltaPitch = 0f
        lastPlacedDeltaYaw = 0f
        lastPlacedDeltaPitch = 0f
        rotated = false
        resetInventoryMoveState()
    }
}
