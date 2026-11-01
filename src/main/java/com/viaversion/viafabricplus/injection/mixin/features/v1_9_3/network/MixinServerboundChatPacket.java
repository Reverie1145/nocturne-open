package com.viaversion.viafabricplus.injection.mixin.features.v1_9_3.network;

import com.viaversion.viafabricplus.ViaFabricPlus;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ServerboundChatPacket.class)
public abstract class MixinServerboundChatPacket {

    @ModifyConstant(method = "write", constant = @Constant(intValue = 256))
    private int modifyChatLength(int maxLength) {
        return ViaFabricPlus.api().limitations().maxChatLength();
    }

}
