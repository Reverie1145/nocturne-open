package cn.reverie.module.render

import cn.reverie.module.render.animations.blockAnimationImpl

import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.module.combat.KillAura
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.ItemInHandRenderer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUseAnimation
import org.lwjgl.glfw.GLFW
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
object Animations : Module(
    name = "Animations",
    description = "Sword blocking & swing animations",
    category = Category.RENDER,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {

    internal const val FAKE_BLOCK_SWING_DURATION_NANOS = 380_000_000L
    internal var fakeBlockSwingTracking = false
    internal var fakeBlockSwingStartNanos = 0L
    internal var lastFakeAttackSwingSequence = 0L
    internal var fakeBlockSwingPending = false

    init {
        // Match Hanabi-Reborn: the renderer animation module is hidden and enabled by default.
        hidden.value = true
        enabled = true
    }

    val swingSpeed = NumberSetting("Swing Speed", "Swing speed duration modifier", 0.0, -4.0, 20.0, 1.0)
    val swingMode = ModeSetting("Swing Mode", "Hand swing motion style", "Vanilla", listOf("Vanilla", "Smooth"))

    val blockMode = ModeSetting(
        "Block Mode",
        "Sword blocking animation style",
        "1.7",
        listOf(
            "1.7", "Vanilla", "Swang", "Swank", "Swong", "Sigma", "Jello", "Slide",
            "Ohare", "Wizzard", "Lennox", "Butter", "Lucky", "Long Hit", "Tiny Whack",
            "Skid", "Slide2", "Mix", "SlideT", "SlideA", "Epic", "Punch", "Push", "Avatar",
            "Flux", "Stella", "SideDown", "Leaked", "Styles", "Spin", "Screw"
        )
    )

    val equipProgress = BooleanSetting("Equip Progress", "Enable sword lowering during attack or swap", true)

    val itemScale = NumberSetting("Item Scale", "Item render scale", 1.0, 0.2, 2.0, 0.05)
    val itemPosX = NumberSetting("Item PosX", "First person X offset", 0.0, -1.0, 1.0, 0.05)
    val itemPosY = NumberSetting("Item PosY", "First person Y offset", 0.0, -1.0, 1.0, 0.05)
    val itemPosZ = NumberSetting("Item PosZ", "First person Z offset", 0.0, -1.0, 1.0, 0.05)

    val killAura = BooleanSetting("KillAura Block", "Trigger blockhit when KillAura is active", true)
    val auraTrigger = ModeSetting(
        "Aura Trigger",
        "KillAura block condition",
        "AutoBlock",
        listOf("AutoBlock", "Target Only", "Always"),
        { killAura.value }
    )
    val rightClick = BooleanSetting("Right Click", "Fake block when holding right click with sword", true)
    val swordsOnly = BooleanSetting("Swords Only", "Only apply blockhit animations to swords", true)
    val thirdPerson = BooleanSetting("Third Person", "Render sword blocking pose in third person (F5)", true)

    override val suffix: String
        get() = blockMode.value

    override fun onDisable() {
        // Keep the renderer hook active, as in the source project.
        enabled = true
    }

    fun isBlockActive(): Boolean {
        if (!enabled) return false
        if (killAura.value && KillAura.enabled) {
            when (auraTrigger.value) {
                "AutoBlock" -> {
                    if (KillAura.isRenderBlock || KillAura.blocking || KillAura.renderBlock) {
                        return true
                    }
                }
                "Target Only" -> {
                    val target = KillAura.target
                    if (target != null && target.isAlive) {
                        return true
                    }
                }
                "Always" -> return true
            }
        }
        return false
    }

    private fun isFakeAutoBlockActive(player: AbstractClientPlayer): Boolean {
        // Read KillAura's tick-latched visual state; don't re-evaluate range per render frame.
        val localPlayer = Minecraft.getInstance().player ?: return false
        if (player !== localPlayer || !KillAura.enabled || !KillAura.autoBlockMode.isMode("Fake")) return false
        return KillAura.renderBlock
    }

    /** Smooth the visual block swing independently from CPS-driven vanilla swing resets. */
    private fun fakeBlockSwingProgress(): Float {
        val now = System.nanoTime()
        val attackSequence = KillAura.attackSwingSequence
        if (!fakeBlockSwingTracking) {
            fakeBlockSwingTracking = true
            lastFakeAttackSwingSequence = attackSequence
        } else if (attackSequence != lastFakeAttackSwingSequence) {
            lastFakeAttackSwingSequence = attackSequence
            fakeBlockSwingPending = true
        }

        if (fakeBlockSwingStartNanos == 0L && fakeBlockSwingPending) {
            fakeBlockSwingPending = false
            fakeBlockSwingStartNanos = now
        }
        if (fakeBlockSwingStartNanos == 0L) return 1.0f

        val progress = (now - fakeBlockSwingStartNanos).toFloat() / FAKE_BLOCK_SWING_DURATION_NANOS
        if (progress >= 1.0f) {
            if (fakeBlockSwingPending) {
                fakeBlockSwingPending = false
                fakeBlockSwingStartNanos = now
                return 0.0f
            }
            fakeBlockSwingStartNanos = 0L
            return 1.0f
        }
        return progress.coerceIn(0.0f, 1.0f)
    }

    private fun resetFakeBlockSwing() {
        fakeBlockSwingTracking = false
        fakeBlockSwingStartNanos = 0L
        fakeBlockSwingPending = false
        lastFakeAttackSwingSequence = KillAura.attackSwingSequence
    }

    fun shouldBlock(player: AbstractClientPlayer, itemStack: ItemStack): Boolean {
        if (!enabled) return false
        if (itemStack.isEmpty) return false
        if (swordsOnly.value && !itemStack.`is`(ItemTags.SWORDS)) return false

        if (player.isUsingItem) {
            val anim = player.useItem.useAnimation
            if (anim != ItemUseAnimation.NONE && anim != ItemUseAnimation.BLOCK) {
                return false
            }
        }

        val mc = Minecraft.getInstance()
        val offHandEmpty = player.offhandItem.isEmpty

        val isRightClickSword = rightClick.value && player === mc.player && mc.options.keyUse.isDown && offHandEmpty

        // In Fake mode, don't let the general Aura Trigger (or a stale renderBlock
        // flag) keep the pose active outside block range.
        val blockActive = if (KillAura.enabled && KillAura.autoBlockMode.isMode("Fake")) {
            isFakeAutoBlockActive(player)
        } else {
            isBlockActive()
        }

        return isRightClickSword || blockActive
    }

    fun shouldBlockThirdPerson(): Boolean {
        if (!enabled || !thirdPerson.value) return false
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        val mainItem = player.mainHandItem
        return shouldBlock(player, mainItem)
    }

    fun shouldCancelDrop(): Boolean {
        if (!enabled || equipProgress.value) return false
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        val mainItem = player.mainHandItem
        if (mainItem.isEmpty) return false
        if (swordsOnly.value && !mainItem.`is`(ItemTags.SWORDS)) return false
        return true
    }

    fun applyEquipOffset(poseStack: PoseStack, armX: Int, equipProgress: Float) {
        poseStack.translate(armX * 0.56f, -0.52f + equipProgress * -0.6f, -0.72f)
    }

    fun applySwingOffset(poseStack: PoseStack, armX: Int, swingProgress: Float) {
        val ySwingRotation = sin(swingProgress * swingProgress * PI.toFloat())
        poseStack.mulPose(Axis.YP.rotationDegrees(armX * (45.0f + ySwingRotation * -20.0f)))
        val xzSwingRotation = sin(sqrt(swingProgress) * PI.toFloat())
        poseStack.mulPose(Axis.ZP.rotationDegrees(armX * xzSwingRotation * -20.0f))
        poseStack.mulPose(Axis.XP.rotationDegrees(xzSwingRotation * -80.0f))
        poseStack.mulPose(Axis.YP.rotationDegrees(armX * -45.0f))
    }

    internal fun applyFakeBlockSwingOffset(poseStack: PoseStack, armX: Int, swingProgress: Float) {
        // Keep the sword in its guard pose throughout the hit. The vanilla
        // applySwingOffset above is an 80-degree attack slash in 26.2; using it
        // inside blockAnimation makes high-CPS Fake look like ordinary swinging.
        val pulse = sin(swingProgress.coerceIn(0.0f, 1.0f) * PI.toFloat())
        poseStack.translate(armX * 0.035f * pulse, -0.035f * pulse, 0.06f * pulse)
        poseStack.mulPose(Axis.XP.rotationDegrees(-14.0f * pulse))
        poseStack.mulPose(Axis.ZP.rotationDegrees(armX * 8.0f * pulse))
    }

    fun swingArm(poseStack: PoseStack, armX: Int, swingProgress: Float, equipProgress: Float) {
        if (swingMode.isMode("Vanilla")) {
            val f = -0.4f * sin(sqrt(swingProgress) * PI.toFloat())
            val g = 0.2f * sin(sqrt(swingProgress) * (PI.toFloat() * 2f))
            val h = -0.2f * sin(swingProgress * PI.toFloat())
            poseStack.translate(armX * f, g, h)
        }
        applyEquipOffset(poseStack, armX, equipProgress)
        applySwingOffset(poseStack, armX, swingProgress)
    }

    fun blockAnimation(
        swingProgress: Float,
        equipProgress: Float,
        poseStack: PoseStack,
        armX: Int,
        fakeAutoBlock: Boolean = false
    ) = this.blockAnimationImpl(swingProgress, equipProgress, poseStack, armX, fakeAutoBlock)
    fun onRenderItem(
        renderer: ItemInHandRenderer,
        player: AbstractClientPlayer,
        frameInterp: Float,
        xRot: Float,
        hand: InteractionHand,
        attack: Float,
        itemStack: ItemStack,
        inverseArmHeight: Float,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        lightCoords: Int
    ): Boolean {
        if (!enabled) return false
        if (player.isScoping) return false
        if (itemStack.isEmpty) return false

        val isMainHand = hand == InteractionHand.MAIN_HAND
        if (!isMainHand) return false

        val isSword = itemStack.`is`(ItemTags.SWORDS)
        if (swordsOnly.value && !isSword) return false

        if (player.isUsingItem) {
            val anim = itemStack.useAnimation
            if (anim != ItemUseAnimation.NONE && anim != ItemUseAnimation.BLOCK) {
                return false
            }
        }

        val arm = if (isMainHand) player.mainArm else player.mainArm.opposite
        val armX = if (arm == HumanoidArm.RIGHT) 1 else -1

        val effEquipProgress = if (equipProgress.value) inverseArmHeight else 0.0f
        val fakeAutoBlock = isFakeAutoBlockActive(player)
        val isBlocking = fakeAutoBlock || shouldBlock(player, itemStack)
        val visualSwingProgress = if (fakeAutoBlock) fakeBlockSwingProgress() else attack
        if (!fakeAutoBlock) resetFakeBlockSwing()

        poseStack.pushPose()

        if (isBlocking) {
            blockAnimation(visualSwingProgress, effEquipProgress, poseStack, armX, fakeAutoBlock)
        } else {

            val scaleVal = itemScale.getFloat()
            val posXVal = itemPosX.getFloat() * armX
            val posYVal = itemPosY.getFloat()
            val posZVal = itemPosZ.getFloat()
            poseStack.translate(posXVal, posYVal, posZVal)
            if (scaleVal != 1.0f) {
                poseStack.scale(scaleVal, scaleVal, scaleVal)
            }
            swingArm(poseStack, armX, attack, effEquipProgress)
        }

        val displayContext = if (arm == HumanoidArm.RIGHT) {
            ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
        } else {
            ItemDisplayContext.FIRST_PERSON_LEFT_HAND
        }

        renderer.renderItem(player, itemStack, displayContext, poseStack, submitNodeCollector, lightCoords)
        poseStack.popPose()

        return true
    }
}
