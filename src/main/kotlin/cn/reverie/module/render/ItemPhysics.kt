package cn.reverie.module.render

import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.render.entity.PhysicsItemRenderState
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.ItemEntityRenderer
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.item.ItemEntity
import org.lwjgl.glfw.GLFW
import kotlin.math.*

object ItemPhysics : Module(
    name = "ItemPhysics",
    description = "Realistic physics simulation for dropped items",
    category = Category.RENDER,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {

    val mode = ModeSetting("Mode", "Physics drop simulation style", "Realistic", listOf("Realistic", "Flat Only"))

    val rotateSpeed = NumberSetting("Rotate Speed", "Air tumbling rotation speed modifier", 1.0, 0.1, 5.0, 0.1)
    val itemScale = NumberSetting("Item Scale", "Dropped item render scale", 1.0, 0.2, 2.0, 0.05)
    val groundOffset = NumberSetting("Ground Offset", "Resting height offset above ground", 0.02, 0.0, 0.1, 0.005)

    val realisticMotion = BooleanSetting("Realistic Motion", "Dynamic tumbling rate calculated from velocity", true, { mode.isMode("Realistic") })
    val waterBuoyancy = BooleanSetting("Water Buoyancy", "Floating and swaying buoyant animation in water", true)
    val physicsStacking = BooleanSetting("Physics Stacking", "Neatly stack multiple items in a pile", true)
    val rotateOnGround = BooleanSetting("Rotate on Ground", "Slowly spin items even while resting on ground", false)
    val randomFlip = BooleanSetting("Random Flip", "Allow flat items to land face-up or face-down", true)
    val renderShadow = BooleanSetting("Item Shadow", "Render entity drop shadow under items", true)

    private val random: RandomSource = RandomSource.create()

    override val suffix: String
        get() = mode.value

    fun processRenderState(state: ItemEntityRenderState) {
        if (!enabled) return
        if (!renderShadow.value) {
            state.shadowRadius = 0.0f
            state.shadowPieces.clear()
        }
    }

    fun render(
        state: ItemEntityRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        camera: CameraRenderState
    ): Boolean {
        if (!enabled) return false
        if (state.item.isEmpty) return true

        val amount = state.count
        if (amount <= 0) return true

        val boundingBox = state.item.modelBoundingBox ?: return true
        if (boundingBox.xsize == 0.0 && boundingBox.ysize == 0.0 && boundingBox.zsize == 0.0) return true

        val width = boundingBox.xsize.toFloat()
        val height = boundingBox.ysize.toFloat()
        val depth = boundingBox.zsize.toFloat()
        val is3D = depth > 0.0625f

        val midX = ((boundingBox.minX + boundingBox.maxX) / 2.0).toFloat()
        val midY = ((boundingBox.minY + boundingBox.maxY) / 2.0).toFloat()
        val midZ = ((boundingBox.minZ + boundingBox.maxZ) / 2.0).toFloat()

        val physics = state as? PhysicsItemRenderState
        val onGround = physics?.`nocturne$isOnGround`() ?: false
        val inWater = physics?.`nocturne$isInWater`() ?: false
        val inLava = physics?.`nocturne$isInLava`() ?: false
        val motionX = physics?.`nocturne$getMotionX`() ?: 0.0
        val motionY = physics?.`nocturne$getMotionY`() ?: 0.0
        val motionZ = physics?.`nocturne$getMotionZ`() ?: 0.0
        val yRot = physics?.`nocturne$getYRot`() ?: 0.0f
        val entityId = physics?.`nocturne$getEntityId`() ?: 0

        val scale = itemScale.value.toFloat()

        // "Flat Only" mode in air retains vanilla tumbling/spinning & bobbing
        if (mode.isMode("Flat Only") && !onGround) {
            poseStack.pushPose()
            val minOffsetY = -boundingBox.minY.toFloat() + 0.0625f
            val bob = sin(state.ageInTicks / 10.0f + state.bobOffset) * 0.1f + 0.1f
            poseStack.translate(0.0f, (bob + minOffsetY) * scale, 0.0f)
            if (scale != 1.0f) {
                poseStack.scale(scale, scale, scale)
            }
            val spin = ItemEntity.getSpin(state.ageInTicks, state.bobOffset)
            poseStack.mulPose(Axis.YP.rotation(spin))
            ItemEntityRenderer.submitMultipleFromCount(poseStack, submitNodeCollector, state.lightCoords, state, random, boundingBox)
            poseStack.popPose()
            return true
        }

        val groundLift = groundOffset.value.toFloat()
        val speed = rotateSpeed.value.toFloat()

        var yaw: Float
        var pitch: Float
        var roll: Float
        var baseElevation: Float

        if (onGround) {
            // Resting flat on ground
            yaw = yRot
            if (rotateOnGround.value) {
                yaw = (yaw + state.ageInTicks * 2.0f * speed) % 360.0f
            }

            if (is3D) {
                pitch = 0.0f
                roll = 0.0f
                baseElevation = groundLift + (height / 2.0f) * scale
            } else {
                val shouldFlip = randomFlip.value && (((state.seed xor (entityId * 31))) and 2) != 0
                pitch = if (shouldFlip) 90.0f else -90.0f
                roll = 0.0f
                baseElevation = groundLift + (depth / 2.0f) * scale
            }
        } else {
            // Airborne tumbling / moving
            val inFluid = inWater || inLava
            var waterBob = 0.0f
            var waterTilt = 0.0f
            var waterRoll = 0.0f

            if (inFluid && waterBuoyancy.value) {
                val fluidFreq = if (inLava) 0.18f else 0.10f
                val fluidAmp = if (inLava) 0.02f else 0.035f
                waterBob = sin(state.ageInTicks * fluidFreq + state.bobOffset) * fluidAmp
                waterTilt = sin(state.ageInTicks * 0.08f + state.bobOffset) * 10.0f
                waterRoll = cos(state.ageInTicks * 0.08f + state.bobOffset) * 7.0f
            }

            val tumbleRate = if (realisticMotion.value) {
                val motionSq = motionX * motionX + motionY * motionY + motionZ * motionZ
                val totalSpeed = sqrt(motionSq).toFloat()
                val factor = if (motionSq > 0.0001) min(4.0f, max(0.4f, totalSpeed * 4.0f)) else 0.5f
                state.ageInTicks * 15.0f * speed * factor
            } else {
                state.ageInTicks * 18.0f * speed
            }

            val damping = if (inFluid) 0.35f else 1.0f
            pitch = ((tumbleRate * damping) % 360.0f) + waterTilt
            yaw = ((yRot + tumbleRate * damping * 0.5f) % 360.0f)
            roll = sin(state.ageInTicks * 0.15f + state.bobOffset) * (if (inFluid) 8.0f else 16.0f) + waterRoll
            baseElevation = (if (is3D) height / 2.0f else depth / 2.0f) * scale + 0.1f + waterBob
        }

        poseStack.pushPose()
        poseStack.translate(0.0f, baseElevation, 0.0f)
        if (scale != 1.0f) {
            poseStack.scale(scale, scale, scale)
        }

        val stackStep = if (is3D) (height * 0.95f + 0.01f) else (depth + 0.004f)

        if (amount == 1 || !physicsStacking.value) {
            random.setSeed(state.seed.toLong())
            for (i in 0 until amount) {
                poseStack.pushPose()
                if (i > 0) {
                    val xo = (random.nextFloat() * 2.0f - 1.0f) * 0.15f
                    val yo = (random.nextFloat() * 2.0f - 1.0f) * 0.15f
                    val zo = (random.nextFloat() * 2.0f - 1.0f) * 0.15f
                    poseStack.translate(xo, yo, zo)
                }
                poseStack.mulPose(Axis.YP.rotationDegrees(yaw))
                poseStack.mulPose(Axis.XP.rotationDegrees(pitch))
                poseStack.mulPose(Axis.ZP.rotationDegrees(roll))
                poseStack.translate(-midX, -midY, -midZ)
                state.item.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor)
                poseStack.popPose()
            }
        } else {
            for (i in 0 until amount) {
                poseStack.pushPose()
                if (i > 0) {
                    if (onGround) {
                        val shiftX = (((i * 19) % 7 - 3) * 0.003f)
                        val shiftZ = (((i * 37) % 7 - 3) * 0.003f)
                        val layerYaw = ((i * 27) % 25 - 12).toFloat()
                        poseStack.translate(shiftX, i * stackStep, shiftZ)
                        poseStack.mulPose(Axis.YP.rotationDegrees(layerYaw))
                    } else {
                        val shiftX = (((i * 7) % 5 - 2) * 0.015f)
                        val shiftY = (((i * 11) % 5 - 2) * 0.015f)
                        val shiftZ = i * (depth * 1.1f)
                        poseStack.translate(shiftX, shiftY, shiftZ)
                    }
                }
                poseStack.mulPose(Axis.YP.rotationDegrees(yaw))
                poseStack.mulPose(Axis.XP.rotationDegrees(pitch))
                poseStack.mulPose(Axis.ZP.rotationDegrees(roll))
                poseStack.translate(-midX, -midY, -midZ)
                state.item.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor)
                poseStack.popPose()
            }
        }

        poseStack.popPose()
        return true
    }
}
