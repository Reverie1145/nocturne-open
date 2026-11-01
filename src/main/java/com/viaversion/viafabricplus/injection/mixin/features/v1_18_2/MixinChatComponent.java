package com.viaversion.viafabricplus.injection.mixin.features.v1_18_2;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public abstract class MixinChatComponent {

    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true)
    private GuiMessageTag removeIndicator(GuiMessageTag tag) {
        return ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_18_2) ? null : tag;
    }

}
