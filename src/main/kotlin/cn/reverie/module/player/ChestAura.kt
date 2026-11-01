package cn.reverie.module.player

import cn.reverie.module.*
import cn.reverie.module.combat.AntiBot
import cn.reverie.module.player.Teams
import cn.reverie.module.world.Scaffold
import cn.reverie.util.rotation.*
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.AbstractChestBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

object ChestAura : Module("ChestAura", "Open nearby chests when no enemy is near", Category.PLAYER) {
    val range = NumberSetting("Range", "Maximum chest distance", 4.0, 1.0, 6.0, 0.1)
    val enemyRange = NumberSetting("Enemy Range", "Pause when an enemy is nearby", 8.0, 1.0, 16.0, 0.5)
    val delay = NumberSetting("Delay", "Minimum time between opens in ms", 400.0, 100.0, 2000.0, 50.0)
    val rotationSpeed = NumberSetting("Rotation Speed", "Degrees per tick", 120.0, 10.0, 180.0, 5.0)
    private val opened = HashSet<BlockPos>()
    private var target: BlockPos? = null
    private var rotation: Rotation? = null
    private var nextOpen = 0L
    private var world: Any? = null
    override fun onDisable() { target = null; rotation = null; opened.clear() }
    fun shouldRotate() = enabled && target != null && rotation != null
    fun getRotation() = rotation
    fun onTick(player: LocalPlayer) {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        if (!enabled || mc.gui.screen() != null || Scaffold.enabled || player.isSpectator) { target = null; rotation = null; return }
        if (world !== level) { world = level; opened.clear() }
        if (level.getEntities(player, player.boundingBox.inflate(enemyRange.value)).filterIsInstance<LivingEntity>()
                .any { it is Player && it.isAlive && !it.isSpectator && !AntiBot.isBot(it) && !Teams.isTeammate(it) }) {
            target = null; rotation = null; return
        }
        val origin = player.blockPosition()
        val radius = range.value.toInt() + 1
        val next = BlockPos.betweenClosed(origin.offset(-radius, -radius, -radius), origin.offset(radius, radius, radius))
            .map { it.immutable() }
            .filter { !opened.contains(it) && (level.getBlockState(it).block is AbstractChestBlock<*> || level.getBlockState(it).block == Blocks.ENDER_CHEST) }
            .filter { player.eyePosition.distanceToSqr(Vec3.atCenterOf(it)) <= range.value * range.value }
            .minByOrNull { player.eyePosition.distanceToSqr(Vec3.atCenterOf(it)) }
        target = next
        rotation = next?.let { RotationUtil.toRotation(Vec3.atCenterOf(it)) }
    }
    fun afterMotion(player: LocalPlayer) {
        val pos = target ?: return
        val aim = rotation ?: return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val gameMode = mc.gameMode ?: return
        if (!enabled || mc.gui.screen() != null || System.currentTimeMillis() < nextOpen) return
        if (level.getEntities(player, player.boundingBox.inflate(enemyRange.value)).filterIsInstance<Player>()
                .any { it.isAlive && !it.isSpectator && !AntiBot.isBot(it) && !Teams.isTeammate(it) }) return
        val sent = RotationManager.lastSentRotation() ?: return
        if (kotlin.math.abs(net.minecraft.util.Mth.wrapDegrees(sent.yaw - aim.yaw)) > 5f || kotlin.math.abs(sent.pitch - aim.pitch) > 5f) return
        val eyes = player.eyePosition
        val hit = level.clip(net.minecraft.world.level.ClipContext(eyes, Vec3.atCenterOf(pos), net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, player)) as? BlockHitResult ?: return
        if (hit.blockPos != pos) return
        gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
        player.swing(InteractionHand.MAIN_HAND)
        opened.add(pos)
        nextOpen = System.currentTimeMillis() + delay.value.toLong()
        target = null; rotation = null
    }
}
