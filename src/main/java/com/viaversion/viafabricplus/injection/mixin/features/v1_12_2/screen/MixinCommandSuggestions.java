package com.viaversion.viafabricplus.injection.mixin.features.v1_12_2.screen;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CommandSuggestions.class)
public abstract class MixinCommandSuggestions {

    @Shadow
    @Nullable
    private CommandSuggestions.@Nullable SuggestionsList suggestions;

    @Shadow
    @Final
    private EditBox input;

    @Shadow
    @Final
    private List<FormattedCharSequence> commandUsage;

    @Shadow
    public abstract void updateCommandInfo();

    @Inject(method = "formatChat", at = @At(value = "HEAD"), cancellable = true)
    private void disableTextFieldColors(String text, int offset, CallbackInfoReturnable<FormattedCharSequence> cir) {
        if (this.viaFabricPlus$cancelTabComplete()) {
            cir.setReturnValue(FormattedCharSequence.forward(text, Style.EMPTY));
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void handle1_12_2KeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (this.viaFabricPlus$cancelTabComplete()) {
            if (event.key() == GLFW.GLFW_KEY_TAB && this.suggestions == null) {
                this.updateCommandInfo();
            } else if (this.suggestions != null) {
                if (this.suggestions.keyPressed(event)) {
                    cir.setReturnValue(true);
                    return;
                }
                this.input.setSuggestion(null);
                this.suggestions = null;
            }
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void clearMessages(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (this.viaFabricPlus$cancelTabComplete()) {
            this.commandUsage.clear();
        }
    }

    @Unique
    private boolean viaFabricPlus$cancelTabComplete() {
        return ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2) && this.input.getValue().startsWith("/");
    }

}
