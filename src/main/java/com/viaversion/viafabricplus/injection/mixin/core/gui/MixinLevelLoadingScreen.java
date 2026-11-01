package com.viaversion.viafabricplus.injection.mixin.core.gui;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.util.ChatUtil;
import com.viaversion.viaversion.api.connection.UserConnection;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.raphimc.vialegacy.protocol.classic.c0_28_30toa1_0_15.storage.ClassicProgressStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class MixinLevelLoadingScreen extends Screen {

    public MixinLevelLoadingScreen(Component title) {
        super(title);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void renderClassicProgress(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        final UserConnection connection = ViaFabricPlus.api().userConnection();
        if (connection != null) {
            final ClassicProgressStorage storage = connection.get(ClassicProgressStorage.class);
            if (storage != null) {
                graphics.centeredText(minecraft.font, ChatUtil.prefixText(storage.getStatus()), width / 2, height / 2 - 30, -1);
            }
        }
    }

}
