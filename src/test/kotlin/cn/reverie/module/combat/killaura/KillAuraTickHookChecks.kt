package cn.reverie.module.combat.killaura

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verify the injection point against the actual Minecraft bytecode on this classpath. */
class KillAuraTickHookChecks {
    @Test
    fun overriddenMovementStillSendsLegacySneakAfterSprintThroughBlink() {
        val mixin = readClass("cn/reverie/mixin/entity/MixinLocalPlayer")
        val send = mixin.methods.single { it.name == "nocturne\$sendPosition" }
            .instructions.filterIsInstance<MethodInsnNode>()
        val sneakCalls = send.withIndex().filter {
            it.value.owner == "cn/reverie/manager/LegacySneakingSender" &&
                it.value.name == "nocturne\$sendLegacySneakingPacket"
        }
        assertEquals(2, sneakCalls.size, "Both controlled and detached camera paths need the legacy bridge")
        for ((index, _) in sneakCalls) {
            assertEquals("sendIsSprintingIfNeeded", send[index - 1].name)
        }

        val via = readClass("com/viaversion/viafabricplus/injection/mixin/features/v1_21_5/movement/MixinLocalPlayer")
        assertTrue("cn/reverie/manager/LegacySneakingSender" in via.interfaces)
        for (name in listOf("viaFabricPlus\$sendSneakingPacket", "viaFabricPlus\$sendInputPacket")) {
            val calls = via.methods.single { it.name == name }.instructions.filterIsInstance<MethodInsnNode>()
            val capture = calls.indexOfFirst {
                it.owner == "cn/reverie/manager/NetworkHookManager" && it.name == "captureAuraProtocolPacket"
            }
            val directSend = calls.indexOfFirst { it.name == "scheduleSendToServer" }
            assertTrue(capture >= 0 && directSend > capture, "$name must join the FIFO before direct sending")
        }
        val config = checkNotNull(javaClass.classLoader.getResourceAsStream("viafabricplus.mixins.json"))
            .bufferedReader().use { it.readText() }
        assertTrue(config.contains("\"features.v1_21_5.movement.MixinLocalPlayer\""))
    }

    @Test
    fun legacySwordHooksResolveAgainstCurrentMinecraft() {
        for (mixinName in listOf("cn/reverie/mixin/item/MixinItemStack", "cn/reverie/mixin/entity/MixinLivingEntity")) {
            val mixin = readClass(mixinName)
            val mixinAnnotation = (mixin.visibleAnnotations.orEmpty() + mixin.invisibleAnnotations.orEmpty())
                .single { it.desc == "Lorg/spongepowered/asm/mixin/Mixin;" }
            val targetType = (mixinAnnotation.value("value") as List<*>).single() as Type
            val target = readClass(targetType.internalName)
            val hooks = mixin.methods.filter { it.name.startsWith("nocturne\$legacySword") }
            assertTrue(hooks.isNotEmpty())
            for (hook in hooks) {
                val inject = (hook.visibleAnnotations.orEmpty() + hook.invisibleAnnotations.orEmpty())
                    .single { it.desc == "Lorg/spongepowered/asm/mixin/injection/Inject;" }
                val selectors = inject.value("method") as List<*>
                val parameters = Type.getArgumentTypes(hook.desc).dropLast(1)
                assertTrue(target.methods.any {
                    it.name in selectors && Type.getArgumentTypes(it.desc).toList() == parameters
                }, "${hook.name} must match a method in ${targetType.className}")
            }
        }
        val config = checkNotNull(javaClass.classLoader.getResourceAsStream("nocturne.mixins.json"))
            .bufferedReader().use { it.readText() }
        assertTrue(config.contains("\"item.MixinItemStack\""))
    }

    @Test
    fun cycleRunsAfterFreshRotationsBeforeInputAndPhysics() {
        val player = readClass("net/minecraft/client/player/LocalPlayer")
        val tick = player.methods.single { it.name == "tick" && it.desc == "()V" }
        val calls = tick.instructions.filterIsInstance<MethodInsnNode>()
        val updateIndex = calls.indexOfFirst {
            it.owner == "net/minecraft/client/player/AbstractClientPlayer" && it.name == "tick"
        }
        val motionIndex = calls.indexOfFirst { it.name == "sendPosition" }
        assertTrue(updateIndex >= 0 && motionIndex > updateIndex)

        val mixin = readClass("cn/reverie/mixin/entity/MixinLocalPlayer")
        val before = mixin.methods.single { it.name == "nocturne\$onAiStepHead" }
        val inject = (before.visibleAnnotations.orEmpty() + before.invisibleAnnotations.orEmpty())
            .single { it.desc == "Lorg/spongepowered/asm/mixin/injection/Inject;" }
        @Suppress("UNCHECKED_CAST")
        val at = (inject.value("at") as List<AnnotationNode>).single()
        assertEquals(listOf("aiStep"), inject.value("method"))
        assertEquals("HEAD", at.value("value"))
        val hookCalls = before.instructions.filterIsInstance<MethodInsnNode>()
        val rotationUpdate = hookCalls.indexOfFirst { it.name == "onLivingUpdate" }
        val auraPre = hookCalls.indexOfFirst { it.name == "prepareAuraMovement" }
        assertTrue(rotationUpdate >= 0 && auraPre > rotationUpdate)

        val movement = player.methods.single { it.name == "aiStep" && it.desc == "()V" }
        val movementCalls = movement.instructions.filterIsInstance<MethodInsnNode>()
        val input = movementCalls.indexOfFirst {
            it.owner == "net/minecraft/client/player/ClientInput" && it.name == "tick"
        }
        val physics = movementCalls.indexOfFirst {
            it.owner == "net/minecraft/client/player/AbstractClientPlayer" && it.name == "aiStep"
        }
        assertTrue(input >= 0 && physics > input, "Aura must run before the attack origin changes")

        val hooks = readClass("cn/reverie/manager/PlayerHookManager")
        val freshRotation = hooks.methods.single { it.name == "onLivingUpdate" }
            .instructions.filterIsInstance<MethodInsnNode>()
        val auraRotation = freshRotation.indexOfFirst {
            it.owner == "cn/reverie/module/combat/KillAura" && it.name == "onLivingUpdate"
        }
        val rotationStep = freshRotation.indexOfFirst {
            it.owner == "cn/reverie/manager/RotationHookManager" && it.name == "onLivingUpdate"
        }
        assertTrue(auraRotation >= 0 && rotationStep > auraRotation)

        // Minecraft.tick HEAD happens before player target selection and rotation.
        // Keeping a second driver there would run the cycle twice or use stale state.
        val lifecycle = readClass("cn/reverie/manager/ClientLifecycleManager")
        val clientTickCalls = lifecycle.methods.single { it.name == "tickClient" }
            .instructions.filterIsInstance<MethodInsnNode>()
        assertTrue(clientTickCalls.none {
            it.owner == "cn/reverie/module/combat/KillAura" && it.name == "onPreTick"
        })
    }

    private fun readClass(name: String): ClassNode {
        val input = checkNotNull(javaClass.classLoader.getResourceAsStream("$name.class"))
        return input.use { stream ->
            ClassNode().also { ClassReader(stream).accept(it, ClassReader.SKIP_DEBUG) }
        }
    }

    private fun AnnotationNode.value(name: String): Any? = values.orEmpty()
        .chunked(2).firstOrNull { it[0] == name }?.get(1)
}
