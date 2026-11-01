package com.viaversion.viafabricplus.injection.mixin.features.v1_11_1;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AvatarRenderer.class)
public abstract class MixinAvatarRenderer {

    @Redirect(method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/world/phys/Vec3;",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;isCrouching:Z", opcode = Opcodes.GETFIELD))
    private boolean disableSneakPositionOffset(AvatarRenderState instance) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_11_1) && instance.isCrouching;
    }

}
