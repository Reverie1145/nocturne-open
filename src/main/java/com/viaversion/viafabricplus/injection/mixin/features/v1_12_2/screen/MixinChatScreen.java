package com.viaversion.viafabricplus.injection.mixin.features.v1_12_2.screen;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChatScreen.class)
public abstract class MixinChatScreen {

    @Shadow
    protected EditBox input;

    @Shadow
    protected String initial;

    @Shadow
    private CommandSuggestions commandSuggestions;

    @WrapWithCondition(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/EditBox;setValue(Ljava/lang/String;)V"))
    public boolean moveSetTextDown(EditBox instance, String value) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_12_2);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void moveSetTextDown(CallbackInfo ci) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            this.input.setValue(this.initial);
            this.commandSuggestions.updateCommandInfo();
        }
    }

    @ModifyArg(method = "onEdited", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/CommandSuggestions;setAllowSuggestions(Z)V"), index = 0)
    private boolean fixCommandKey(boolean windowActive) {
        final String instance = this.input.getValue();
        if (this.viaFabricPlus$keepTabComplete()) {
            return true;
        } else {
            return !instance.isEmpty();
        }
    }

    @WrapWithCondition(method = "onEdited", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/CommandSuggestions;updateCommandInfo()V"))
    private boolean disableAutoTabComplete(CommandSuggestions instance) {
        return this.viaFabricPlus$keepTabComplete();
    }

    @Unique
    private boolean viaFabricPlus$keepTabComplete() {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_12_2) || !this.input.getValue().startsWith("/");
    }

}
