package cn.reverie.mixin.gui;

import cn.reverie.manager.GuiHookManager;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$1")
public abstract class MixinChatComponentAnimatedPrefix {

    @ModifyArg(
            method = "accept",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z"
            ),
            index = 2
    )
    private FormattedCharSequence nocturne$animateClientPrefix(FormattedCharSequence message) {
        return GuiHookManager.animateClientPrefix(message);
    }
}
