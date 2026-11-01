package com.viaversion.viafabricplus.injection.mixin.features.v1_21_2;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.features.v1_21_2.ItemPick1_21_3;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
    private void pickItemClientside(CallbackInfo ci) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_2)) {
            ItemPick1_21_3.doItemPick((Minecraft) (Object) this);
            ci.cancel();
        }
    }

}
