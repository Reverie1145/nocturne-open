package cn.reverie.mixin.gui;

import cn.reverie.manager.GuiHookManager;
import cn.reverie.render.ChatComponentAccess;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import java.util.List;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.components.ChatComponent.ChatGraphicsAccess;
import net.minecraft.client.gui.components.ChatComponent.DisplayMode;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent")
public abstract class MixinChatComponent implements ChatComponentAccess {

    @Inject(method = "addServerSystemMessage", at = @At("HEAD"))
    private void nocturne$agentSystemChat(net.minecraft.network.chat.Component message, CallbackInfo ci) {
        GuiHookManager.publishAgentChat(message.getString(), "received");
    }

    @Inject(method = "addPlayerMessage", at = @At("HEAD"))
    private void nocturne$agentPlayerChat(net.minecraft.network.chat.Component message,
            net.minecraft.network.chat.MessageSignature signature, net.minecraft.client.multiplayer.chat.GuiMessageTag tag,
            CallbackInfo ci) {
        GuiHookManager.publishAgentChat(message.getString(), "received");
    }

    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow private int chatScrollbarPos;

    @Override
    @Unique
    public List<GuiMessage.Line> nocturne$getTrimmedMessages() {
        return this.trimmedMessages;
    }

    @Override
    @Unique
    public int nocturne$getChatScrollbarPos() {
        return this.chatScrollbarPos;
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void nocturne$replaceVanillaChatRenderer(ChatGraphicsAccess graphics, int mouseX, int mouseY, DisplayMode mode, CallbackInfo ci) {
        if (!GuiHookManager.shouldReplaceVanillaChatRenderer()) return;
        // The separate clickable-text pass uses this adapter to retain vanilla hover and click behavior.
        if (!graphics.getClass().getName().contains("ClickableTextOnlyGraphicsAccess")) ci.cancel();
    }
}
