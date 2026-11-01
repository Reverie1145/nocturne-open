package com.viaversion.viafabricplus.injection.mixin.features.v1_9_3.network;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.viaversion.viafabricplus.ViaFabricPlus;
import net.minecraft.util.StringUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(StringUtil.class)
public abstract class MixinStringUtil {

    @ModifyExpressionValue(method = "trimChatMessage", at = @At(value = "CONSTANT", args = "intValue=256"))
    private static int modifyMaxChatLength(int constant) {
        return ViaFabricPlus.api().limitations().maxChatLength();
    }

}
