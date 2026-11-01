package cn.reverie.module.combat

import cn.reverie.module.combat.projectileaura.ProjectileTrajectorySolver

import cn.reverie.mixin.client.MultiPlayerGameModeAccessor
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.combat.AntiBot
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.module.player.Teams
import cn.reverie.module.world.Scaffold
import cn.reverie.util.misc.ItemSpoofUtil
import cn.reverie.util.misc.TimerUtil
import cn.reverie.util.network.ProtocolUtil
import cn.reverie.util.rotation.*
import com.viaversion.viafabricplus.protocoltranslator.impl.provider.viaversion.ViaFabricPlusHandItemProvider
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import java.util.Comparator
import kotlin.math.atan2
import kotlin.math.sqrt
object ProjectileAura : Module(
    name = "ProjectileAura",
    description = "Automatically aims and throws projectiles with prediction",
    category = Category.COMBAT,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {

    val priority = ModeSetting("Priority", "Target sort order", "Distance", listOf("Distance", "Health", "Fov"))
    val minRange = NumberSetting("Min Range", "Minimum distance to target", 3.0, 0.0, 10.0, 0.5)
    val maxRange = NumberSetting("Max Range", "Maximum distance to target", 20.0, 5.0, 40.0, 1.0)
    val delay = NumberSetting("Delay", "Throw delay in ms", 150.0, 50.0, 1000.0, 25.0)

    val slotMode = ModeSetting("Slot Mode", "Item selection method", "Silent", listOf("Silent", "Switch", "Held Only"))
    val switchBack = BooleanSetting("Switch Back", "Restore original slot after throwing", true, { slotMode.isMode("Switch") })
    val swapDelay = NumberSetting("Swap Delay", "Delay in ticks before switching back", 1.0, 0.0, 5.0, 1.0, { slotMode.isMode("Switch") && switchBack.value })
    val itemSpoof = BooleanSetting("Item Spoof", "Spoof hand model during switch", true, { slotMode.isMode("Switch") })
    val clientNoSwing = BooleanSetting("Client NoSwing", "Visual-only: send swing packet without client hand animation", true)

    val rotationSpeed = NumberSetting("Rotation Speed", "Aim rotation speed in deg/tick", 180.0, 10.0, 360.0, 5.0)
    val movementFixMode = ModeSetting("MovementFix Mode", "Movement vector alignment", "Silent", listOf("None", "Silent", "Strict"))

    val predict = BooleanSetting("Predict", "Predict enemy movement and projectile trajectory", true)
    val predictFactor = NumberSetting("Predict Size", "Target motion prediction multiplier", 1.0, 0.0, 2.0, 0.1, { predict.value })
    val rayCast = BooleanSetting("Ray Cast", "Verify clear trajectory before throwing", true)

    val pauseOnKillAura = BooleanSetting("Pause on KillAura", "Yield control when KillAura is engaging", true)

    val snowballs = BooleanSetting("Snowballs", "Throw snowballs", true)
    val eggs = BooleanSetting("Eggs", "Throw eggs", true)
    val fishingRod = BooleanSetting("Fishing Rod", "Use fishing rod", true)
    val potions = BooleanSetting("Potions", "Throw splash / lingering potions", false)
    val pearls = BooleanSetting("Pearls", "Throw ender pearls", false)
    val windCharges = BooleanSetting("Wind Charges", "Throw wind charges", true)

    val players = BooleanSetting("Players", "Target players", true)
    val monsters = BooleanSetting("Monsters", "Target monsters", true)
    val animals = BooleanSetting("Animals", "Target animals", false)
    val invisibles = BooleanSetting("Invisibles", "Target invisible entities", false)

    val targets = ArrayList<LivingEntity>()
    private val throwTimer = TimerUtil()

    var target: LivingEntity? = null
        private set

    var rotations: Rotation? = null
        private set

    private var oldSlot = -1
    private var swapBackTicks = -1
    private var ownsItemSpoof = false
    private var ownedSpoofSlot = -1

    data class TrajectoryResult(
        val rotation: Rotation,
        val hitTick: Int,
        val predictedPos: Vec3
    )

    override val suffix: String
        get() = slotMode.value

    override fun onEnable() {
        reset()
    }

    override fun onDisable() {
        reset(restoreSelectedSlot = !Scaffold.enabled)
    }

    fun reset(restoreSelectedSlot: Boolean = true) {
        val player = Minecraft.getInstance().player
        if (restoreSelectedSlot && player != null && oldSlot in 0..8) {
            player.inventory.selectedSlot = oldSlot
            val gameMode = Minecraft.getInstance().gameMode
            if (gameMode is MultiPlayerGameModeAccessor) {
                gameMode.nocturneEnsureHasSentCarriedItem()
            }
        }
        stopOwnedItemSpoof()
        throwTimer.reset()
        target = null
        rotations = null
        targets.clear()
        oldSlot = -1
        swapBackTicks = -1
    }

    @JvmStatic
    fun isWaitingToRestoreSlot(player: LocalPlayer): Boolean =
        enabled && swapBackTicks > 0 && oldSlot in 0..8 &&
            player.inventory.selectedSlot != oldSlot && isProjectile(player.mainHandItem)

    fun getMoveFix(): MoveFix = when {
        movementFixMode.isMode("Silent") -> MoveFix.Silent
        movementFixMode.isMode("Strict") -> MoveFix.Strict
        else -> MoveFix.None
    }

    fun getRotationSpeed(): Double = rotationSpeed.value

    fun shouldRotate(): Boolean {
        if (!enabled || Scaffold.enabled) return false
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        if (mc.level == null || !player.isAlive || player.isSpectator) return false

        if (isPausedByKillAura()) return false
        if (KillAura.blocking) return false

        return target != null && rotations != null
    }

    private fun isPausedByKillAura(): Boolean {
        if (!pauseOnKillAura.value || !KillAura.enabled) return false
        if (KillAura.target != null) return true
        if (KillAura.shouldRotate()) return true
        return false
    }

    @JvmStatic
    fun onUpdate(player: LocalPlayer) {
        if (!enabled || Scaffold.enabled) {
            if (Scaffold.enabled && (target != null || rotations != null || oldSlot != -1)) {
                reset()
            }
            return
        }
        val mc = Minecraft.getInstance()
        if (mc.level == null || mc.gameMode == null || !player.isAlive || player.isSpectator) {
            reset()
            return
        }

        if (swapBackTicks > 0) {
            swapBackTicks--
            if (swapBackTicks == 0) {
                restoreSlot(player)
            }
            return
        }

        if (isPausedByKillAura() || KillAura.blocking) {
            target = null
            rotations = null
            return
        }

        val slot = findProjectileSlot(player)
        if (slot == -1) {
            target = null
            rotations = null
            return
        }

        updateTargets(player)
        if (targets.isEmpty()) {
            target = null
            rotations = null
            return
        }

        target = targets.first()
        val currentTarget = target ?: return
        val itemStack = player.inventory.getItem(slot)
        val traj = solveTrajectory(player, currentTarget, itemStack.item)

        if (traj != null) {
            rotations = traj.rotation

            if (throwTimer.hasTimeElapsed(delay.value.toLong())) {
                doThrow(player, traj.rotation, slot)
                throwTimer.reset()
            }
        } else {
            rotations = null
        }
    }

    @JvmStatic
    fun onLivingUpdate(player: LocalPlayer) {
        if (!enabled || Scaffold.enabled || isPausedByKillAura() || KillAura.blocking) {
            target = null
            rotations = null
            return
        }

        val currentTarget = target
        if (currentTarget != null && currentTarget.isAlive) {
            val slot = findProjectileSlot(player)
            if (slot != -1) {
                val itemStack = player.inventory.getItem(slot)
                val traj = solveTrajectory(player, currentTarget, itemStack.item)
                rotations = traj?.rotation
            } else {
                rotations = null
            }
        } else {
            target = null
            rotations = null
        }
    }

    @JvmStatic
    fun onVisualTick(player: LocalPlayer) {
        if (!enabled || Scaffold.enabled) return
        if (shouldRotate()) {
            RotationManager.updateRenderAngles(player)
        }
    }

    private fun doThrow(player: LocalPlayer, rot: Rotation, slot: Int) {
        if (!enabled || Scaffold.enabled) return
        val mc = Minecraft.getInstance()
        val gameMode = mc.gameMode ?: return

        val currentSlot = player.inventory.selectedSlot
        val needsSwitch = slot != currentSlot

        when (slotMode.value) {
            "Silent" -> {
                if (needsSwitch) {
                    player.inventory.selectedSlot = slot
                }

                val oldYaw = player.yRot
                val oldPitch = player.xRot
                player.yRot = rot.yaw
                player.xRot = rot.pitch

                if (ProtocolUtil.is1_8OrOlder()) {
                    ViaFabricPlusHandItemProvider.lastUsedItem = player.mainHandItem.copy()
                }

                gameMode.useItem(player, InteractionHand.MAIN_HAND)

                player.yRot = oldYaw
                player.xRot = oldPitch

                if (needsSwitch) {
                    player.inventory.selectedSlot = currentSlot
                    val gameModeAccessor = gameMode as? MultiPlayerGameModeAccessor
                    gameModeAccessor?.nocturneEnsureHasSentCarriedItem()
                }

                handleSwing(player)
            }
            "Switch" -> {
                if (needsSwitch) {
                    oldSlot = currentSlot
                    if (itemSpoof.value) {
                        ItemSpoofUtil.startSpoof(oldSlot)
                        ownsItemSpoof = true
                        ownedSpoofSlot = oldSlot
                    }
                    player.inventory.selectedSlot = slot
                }

                val oldYaw = player.yRot
                val oldPitch = player.xRot
                player.yRot = rot.yaw
                player.xRot = rot.pitch

                if (ProtocolUtil.is1_8OrOlder()) {
                    ViaFabricPlusHandItemProvider.lastUsedItem = player.mainHandItem.copy()
                }

                gameMode.useItem(player, InteractionHand.MAIN_HAND)

                player.yRot = oldYaw
                player.xRot = oldPitch

                handleSwing(player)

                if (needsSwitch && switchBack.value) {
                    if (swapDelay.getInt() <= 0) {
                        restoreSlot(player)
                    } else {
                        swapBackTicks = swapDelay.getInt()
                    }
                }
            }
            "Held Only" -> {
                val oldYaw = player.yRot
                val oldPitch = player.xRot
                player.yRot = rot.yaw
                player.xRot = rot.pitch

                if (ProtocolUtil.is1_8OrOlder()) {
                    ViaFabricPlusHandItemProvider.lastUsedItem = player.mainHandItem.copy()
                }

                gameMode.useItem(player, InteractionHand.MAIN_HAND)

                player.yRot = oldYaw
                player.xRot = oldPitch

                handleSwing(player)
            }
        }
    }

    private fun handleSwing(player: LocalPlayer) {
        if (clientNoSwing.value) {
            player.connection.send(ServerboundSwingPacket(InteractionHand.MAIN_HAND))
        } else {
            player.swing(InteractionHand.MAIN_HAND)
        }
    }

    private fun restoreSlot(player: LocalPlayer) {
        if (oldSlot in 0..8) {
            player.inventory.selectedSlot = oldSlot
            val gameMode = Minecraft.getInstance().gameMode
            if (gameMode is MultiPlayerGameModeAccessor) {
                gameMode.nocturneEnsureHasSentCarriedItem()
            }
        }
        stopOwnedItemSpoof()
        oldSlot = -1
        swapBackTicks = -1
    }

    private fun stopOwnedItemSpoof() {
        if (ownsItemSpoof) {
            if (ItemSpoofUtil.isSpoofing() && ItemSpoofUtil.getSpoofedSlot() == ownedSpoofSlot) {
                ItemSpoofUtil.stopSpoof()
            }
            ownsItemSpoof = false
            ownedSpoofSlot = -1
        }
    }

    fun findProjectileSlot(player: LocalPlayer): Int {
        val held = player.mainHandItem
        if (isProjectile(held)) {
            return player.inventory.selectedSlot
        }

        if (slotMode.isMode("Held Only")) {
            return -1
        }

        for (i in 0..8) {
            val stack = player.inventory.getItem(i)
            if (isProjectile(stack)) {
                return i
            }
        }
        return -1
    }

    fun isProjectile(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val item = stack.item

        if (snowballs.value && item == Items.SNOWBALL) return true
        if (eggs.value && item == Items.EGG) return true
        if (fishingRod.value && item == Items.FISHING_ROD) return true
        if (pearls.value && item == Items.ENDER_PEARL) return true
        if (potions.value && (item == Items.SPLASH_POTION || item == Items.LINGERING_POTION)) return true
        if (windCharges.value && item.descriptionId.contains("wind_charge")) return true

        return false
    }

    fun solveTrajectory(player: LocalPlayer, target: LivingEntity, item: Item): TrajectoryResult? = ProjectileTrajectorySolver.solve(player, target, item, if (predict.value) predictFactor.getFloat() else 0.0f, rayCast.value)

    private fun updateTargets(player: LocalPlayer) {
        targets.clear()
        val level = player.level()
        val searchBox = player.boundingBox.inflate(maxRange.value + 2.0)
        for (entity in level.getEntities(player, searchBox)) {
            if (entity is LivingEntity && filter(player, entity)) {
                targets.add(entity)
            }
        }

        if (targets.isNotEmpty()) {
            targets.sortWith(sortTargets(player, priority.value))
        }
    }

    private fun sortTargets(player: LocalPlayer, priority: String): Comparator<LivingEntity> {
        return when (priority) {
            "Health" -> Comparator.comparingDouble { entity -> (entity.health + entity.absorptionAmount).toDouble() }
            "Fov" -> Comparator.comparingDouble { entity -> getRotationDifference(player, entity) }
            else -> Comparator.comparingDouble { entity -> player.distanceTo(entity).toDouble() }
        }
    }

    private fun filter(player: LocalPlayer, entity: LivingEntity): Boolean {
        if (entity === player) return false
        if (AntiBot.isBot(entity)) return false
        if (Teams.isTeammate(entity)) return false
        if (!entity.isAlive || entity.isSpectator || entity.isDeadOrDying || entity.health <= 0f) return false
        if (!invisibles.value && entity.isInvisible) return false

        when (entity) {
            is Player -> if (!players.value) return false
            is Monster -> if (!monsters.value) return false
            is Animal -> if (!animals.value) return false
            is ArmorStand -> return false
            else -> if (!monsters.value && !animals.value) return false
        }

        val distance = player.distanceTo(entity)
        return distance in minRange.value..maxRange.value
    }

    private fun getRotationDifference(player: LocalPlayer, entity: LivingEntity): Double {
        val diffX = entity.x - player.x
        val diffY = (entity.y + entity.bbHeight * 0.5) - player.eyeY
        val diffZ = entity.z - player.z
        val distXZ = sqrt(diffX * diffX + diffZ * diffZ)

        val yaw = (Math.toDegrees(atan2(diffZ, diffX)) - 90.0).toFloat()
        val pitch = (-Math.toDegrees(atan2(diffY, distXZ))).toFloat()

        return RotationUtil.getRotationDifference(
            RotationUtil.getRotationOrElseMC(),
            Rotation(yaw, pitch)
        )
    }
}
