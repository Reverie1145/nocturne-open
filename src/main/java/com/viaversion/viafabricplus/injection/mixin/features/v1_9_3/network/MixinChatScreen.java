package com.viaversion.viafabricplus.injection.mixin.features.v1_9_3.network;

import com.viaversion.viafabricplus.ViaFabricPlus;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChatScreen.class, priority = 1)
public abstract class MixinChatScreen {

    @Shadow
    protected EditBox input;

    @Inject(method = "init", at = @At("RETURN"))
    private void changeChatLength(CallbackInfo ci) {
        if (this.input.getMaxLength() == SharedConstants.MAX_CHAT_LENGTH) {
            this.input.setMaxLength(ViaFabricPlus.api().limitations().maxChatLength());
        }
    }

}
