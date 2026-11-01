package cn.reverie.manager

import cn.reverie.module.render.Animations
import cn.reverie.module.render.ESP
import cn.reverie.module.render.Fullbright
import cn.reverie.module.render.ItemPhysics
import cn.reverie.module.render.NameTags
import cn.reverie.module.render.NoHurtCam
import cn.reverie.module.combat.Backtrack
import cn.reverie.module.combat.KnockbackDisplace
import cn.reverie.module.world.Scaffold
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.util.misc.ItemSpoofUtil
import cn.reverie.util.render.Render3DUtil
import cn.reverie.util.rotation.RotationManager
import cn.reverie.util.rotation.RotationSolver
import com.mojang.blaze3d.platform.Window
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.ItemInHandRenderer
import net.minecraft.client.renderer.LightmapRenderStateExtractor
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.client.renderer.state.LightmapRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.state.level.LevelRenderState
import cn.reverie.render.hud.impl.LyricHUDWidget
import net.minecraft.network.protocol.Packet
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4fc
import org.joml.Vector4f
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.resource.GraphicsResourceAllocator

object RenderHookManager {
    @JvmStatic fun updateAimAssistFrame() = PlayerHookManager.onAimAssistRenderFrame()
    @JvmStatic fun initialize() = RenderEngine.init()
    @JvmStatic fun tick() = RenderEngine.onTick()
    @JvmStatic fun render2D(deltaTracker: DeltaTracker) = RenderEngine.onRender2D(deltaTracker)
    @JvmStatic fun renderBeforeGui(deltaTracker: DeltaTracker) = RenderEngine.onBeforeGuiRender(deltaTracker)
    @JvmStatic fun renderHud(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) = RenderEngine.onRenderHud(graphics, deltaTracker)
    @JvmStatic fun captureCameraState(state: CameraRenderState) = Render3DUtil.capture(state)
    @JvmStatic fun renderInWorldLyrics(levelRenderState: LevelRenderState, submitNodeCollector: SubmitNodeCollector) =
        LyricHUDWidget.renderInWorld(levelRenderState, submitNodeCollector)
    @JvmStatic fun renderBacktrack(levelRenderState: LevelRenderState, submitNodeCollector: SubmitNodeCollector) =
        Backtrack.renderWorldBox(levelRenderState, submitNodeCollector)
    @JvmStatic fun renderKnockbackDisplace(levelRenderState: LevelRenderState, submitNodeCollector: SubmitNodeCollector) =
        KnockbackDisplace.renderWorld(levelRenderState, submitNodeCollector)
    @JvmStatic fun updateResolution(window: Window, guiScaleOverride: Int? = null) = ResolutionHelper.update(
        window.screenWidth, window.screenHeight, window.width, window.height,
        window.guiScaledWidth, window.guiScaledHeight, guiScaleOverride ?: window.guiScale
    )

    @JvmStatic fun renderScreenPre(screen: Screen, mouseX: Int, mouseY: Int, partial: Float): Boolean =
        RenderEngine.onRenderScreenPre(screen, mouseX, mouseY, partial)
    @JvmStatic fun renderScreenPost(screen: Screen, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partial: Float) =
        RenderEngine.onRenderScreenPost(screen, graphics, mouseX, mouseY, partial)
    @JvmStatic fun onScreenClosed(screen: Screen) = RenderEngine.onScreenClosed(screen)
    @JvmStatic fun onKeyPressed(screen: Screen, key: Int, scanCode: Int, modifiers: Int): Boolean =
        RenderEngine.onKeyPressed(screen, key, scanCode, modifiers)
    @JvmStatic fun onMouseMoved(screen: Screen?, x: Double, y: Double) = RenderEngine.onMouseMoved(screen, x, y)
    @JvmStatic fun shouldInterceptVanillaPotionHud(): Boolean = RenderEngine.hudRenderer.shouldInterceptVanillaPotion()

    @JvmStatic fun shouldCancelHurtCam(): Boolean = NoHurtCam.enabled
    @JvmStatic fun shouldCancelVanillaNameDisplay(): Boolean = NameTags.enabled && NameTags.cancelVanilla.value
    @JvmStatic fun shouldGlow(entity: Entity): Boolean = ESP.shouldGlow(entity)
    @JvmStatic fun glowColor(entity: Entity): Int? = ESP.getGlowColor(entity)
    @JvmStatic fun shouldReplaceScoreboard(): Boolean = cn.reverie.render.hud.impl.ScoreboardWidget.enabled

    @JvmStatic fun shouldSpoofHotbar(): Boolean = ItemSpoofUtil.shouldSpoofHotbar()
    @JvmStatic fun spoofedHotbarSlot(): Int = ItemSpoofUtil.getSpoofedSlot()
    @JvmStatic fun spoofingItem(): Boolean = ItemSpoofUtil.isSpoofing()
    @JvmStatic fun spoofedItemStack(): ItemStack = ItemSpoofUtil.getStack()
    @JvmStatic fun shouldCancelDropAnimation(): Boolean = Animations.shouldCancelDrop()

    @JvmStatic
    fun renderItem(
        renderer: ItemInHandRenderer, player: AbstractClientPlayer, frameInterp: Float, xRot: Float,
        hand: InteractionHand, attack: Float, itemStack: ItemStack, inverseArmHeight: Float,
        poseStack: PoseStack, submitNodeCollector: SubmitNodeCollector, lightCoords: Int
    ): Boolean = Animations.onRenderItem(
        renderer, player, frameInterp, xRot, hand, attack, itemStack, inverseArmHeight,
        poseStack, submitNodeCollector, lightCoords
    )

    @JvmStatic
    fun renderPhysicsItem(
        state: ItemEntityRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        camera: CameraRenderState
    ): Boolean = ItemPhysics.render(state, poseStack, submitNodeCollector, camera)

    @JvmStatic
    fun processPhysicsItemRenderState(state: ItemEntityRenderState) {
        ItemPhysics.processRenderState(state)
    }

    @JvmStatic fun swingDuration(playerIsLocal: Boolean): Int? =
        if (playerIsLocal && Animations.enabled) maxOf(1, 6 + Animations.swingSpeed.value.toInt()) else null

    @JvmStatic fun moveFixJumpYaw(isLocalPlayer: Boolean): Float? =
        if (isLocalPlayer) RotationManager.movementYaw() else null
    @JvmStatic fun visualLookRotation(partialTick: Float): FloatArray? = RotationManager.viewRotation(partialTick)
    @JvmStatic fun movementYaw(): Float? = RotationManager.movementYaw()
    @JvmStatic fun rotationVector(yaw: Float, pitch: Float): Vec3 = RotationSolver.getVectorForRotation(yaw, pitch)
    @JvmStatic fun movementInputToVelocity(input: Vec3, speed: Float, yaw: Float): Vec3 =
        RotationSolver.movementInputToVelocity(input, speed, yaw)

    @JvmStatic
    fun applyLocalLivingRotations(entity: LivingEntity, state: LivingEntityRenderState, partialTicks: Float) {
        if (state is ArmedEntityRenderState && Animations.shouldBlockThirdPerson()) {
            if (state.mainArm == HumanoidArm.RIGHT) state.rightArmPose = HumanoidModel.ArmPose.BLOCK
            else state.leftArmPose = HumanoidModel.ArmPose.BLOCK
            state.attackTime = 0.0F
        }
        if (!RotationManager.isVisualRotating()) return

        val headYaw = RotationManager.getLerpedYaw(partialTicks)
        val pitch = RotationManager.getLerpedPitch(partialTicks)
        val maxHeadOffset = if (entity.isBlocking) 15.0F else 50.0F
        var headOffset = Mth.wrapDegrees(headYaw - state.bodyRot)
        if (kotlin.math.abs(headOffset) > maxHeadOffset) {
            state.bodyRot = headYaw - kotlin.math.sign(headOffset) * maxHeadOffset
            headOffset = Mth.wrapDegrees(headYaw - state.bodyRot)
        }
        state.yRot = headOffset
        state.xRot = pitch
        if (state.isUpsideDown) {
            state.xRot *= -1.0F
            state.yRot *= -1.0F
        }
    }

    @JvmStatic fun needsFullbrightUpdate(): Boolean = Fullbright.needsUpdate
    @JvmStatic fun clearFullbrightUpdate() { Fullbright.needsUpdate = false }
    @JvmStatic
    fun applyFullbright(state: LightmapRenderState) {
        if (!Fullbright.enabled) return
        val mode = Fullbright.mode.value
        if (mode.equals("Gamma", true) || mode.equals("Both", true)) state.brightness = Fullbright.brightness.getFloat()
        if (mode.equals("NightVision", true) || mode.equals("Both", true)) state.nightVisionEffectIntensity = 1.0f
        if (Fullbright.removeDarkness.value) state.darknessEffectScale = 0.0f
    }
}
