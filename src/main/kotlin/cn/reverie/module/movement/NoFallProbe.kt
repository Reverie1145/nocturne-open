package cn.reverie.module.movement

import cn.reverie.app.event.packet.PacketEvent
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.util.network.PacketUtil
import cn.reverie.util.network.ProtocolUtil
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

object NoFallProbe : Module(
    name = "NoFallProbe",
    description = "Reports ground state to the server independently of actual support",
    category = Category.MOVEMENT
) {
    private const val MODE_OFF = "Off"
    private const val MODE_STATUS = "StatusOnly"
    private const val MODE_ROTATION = "Rotation"
    private const val MODE_DUPLICATE = "Duplicate"

    val mode = ModeSetting(
        "Mode",
        "Primitive used to report ground state to the server",
        MODE_OFF,
        listOf(MODE_OFF, MODE_STATUS, MODE_ROTATION, MODE_DUPLICATE)
    )
    val budget = NumberSetting(
        "Budget",
        "Reported drop distance accumulated before a report is emitted",
        2.5,
        0.5,
        3.0,
        0.1
    )
    val verbose = BooleanSetting("Verbose", "Log emitted reports and server corrections", true)

    private var referenceX = 0.0
    private var referenceY = 0.0
    private var referenceZ = 0.0
    private var hasReference = false
    private var pendingDrop = 0.0
    private var lastGroundClaim = false
    private var emittedReports = 0
    private var observedCorrections = 0

    init {
        listen(PacketEvent::class.java) { event -> onPacket(event) }
    }

    override fun onEnable() {
        resetState()
        val version = ProtocolUtil.targetVersion()
        val duplicateUsable = version != null &&
            version.newerThanOrEqualTo(ProtocolVersion.v1_17) &&
            version.olderThan(ProtocolVersion.v1_21)
        if (mode.isMode(MODE_DUPLICATE) && !duplicateUsable) {
            println("[Nocturne] NoFallProbe: target protocol $version cannot produce duplicate slots outside a vehicle")
        }
    }

    override fun onDisable() {
        resetState()
    }

    private fun resetState() {
        hasReference = false
        pendingDrop = 0.0
        lastGroundClaim = false
        emittedReports = 0
        observedCorrections = 0
    }

    private fun onPacket(event: PacketEvent) {
        if (!enabled) return
        val packet = event.packet
        if (event.isReceive) {
            handleIncoming(packet)
            return
        }
        val movePacket = packet as? ServerboundMovePlayerPacket ?: return
        val player = Minecraft.getInstance().player ?: return
        handleOutgoing(player, movePacket)
    }

    private fun handleIncoming(packet: Packet<*>) {
        if (packet !is ClientboundPlayerPositionPacket) return
        observedCorrections++
        pendingDrop = 0.0
        if (verbose.value) {
            println("[Nocturne] NoFallProbe: server correction #$observedCorrections received, drop counter reset")
        }
    }

    private fun handleOutgoing(player: LocalPlayer, packet: ServerboundMovePlayerPacket) {
        lastGroundClaim = packet.isOnGround()
        if (!packet.hasPosition()) return

        val nextX = packet.getX(player.x)
        val nextY = packet.getY(player.y)
        val nextZ = packet.getZ(player.z)

        if (hasReference) {
            val drop = referenceY - nextY
            if (drop > 0.0) pendingDrop += drop
        }
        referenceX = nextX
        referenceY = nextY
        referenceZ = nextZ
        hasReference = true

        if (mode.isMode(MODE_OFF)) return
        if (player.onGround() || player.isSpectator || player.isCreative || player.isPassenger) {
            pendingDrop = 0.0
            return
        }
        if (packet.isOnGround()) {
            pendingDrop = 0.0
            return
        }
        if (pendingDrop < budget.value) return

        if (!emit(player)) return
        pendingDrop = 0.0
    }

    private fun emit(player: LocalPlayer): Boolean {
        when {
            mode.isMode(MODE_STATUS) ->
                PacketUtil.sendPacketNoEvent(ServerboundMovePlayerPacket.StatusOnly(true, false))
            mode.isMode(MODE_ROTATION) ->
                PacketUtil.sendPacketNoEvent(ServerboundMovePlayerPacket.Rot(player.yRot, player.xRot, true, false))
            mode.isMode(MODE_DUPLICATE) ->
                PacketUtil.sendPacketNoEvent(
                    ServerboundMovePlayerPacket.PosRot(
                        referenceX,
                        referenceY,
                        referenceZ,
                        player.yRot,
                        player.xRot,
                        lastGroundClaim,
                        false
                    )
                )
            else -> return false
        }
        emittedReports++
        if (verbose.value) {
            println("[Nocturne] NoFallProbe: report #$emittedReports mode=${mode.value} drop=${"%.3f".format(pendingDrop)} claim=$lastGroundClaim")
        }
        return true
    }

    val stats: String
        get() = "$emittedReports / $observedCorrections"
}
