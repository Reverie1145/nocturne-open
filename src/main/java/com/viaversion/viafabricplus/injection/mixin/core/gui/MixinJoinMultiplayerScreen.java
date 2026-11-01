package com.viaversion.viafabricplus.injection.mixin.core.gui;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.api.settings.impl.Orientation;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MixinJoinMultiplayerScreen extends Screen {

    @Unique
    private Button viaFabricPlus$button;

    public MixinJoinMultiplayerScreen(Component title) {
        super(title);
    }

    @Inject(method = "repositionElements", at = @At("RETURN"))
    private void addProtocolSelectionButton(CallbackInfo ci) {
        final Orientation orientation = ViaFabricPlusImpl.impl().options().multiplayerScreenButtonOrientation().value();
        if (orientation != Orientation.NONE) {
            if (viaFabricPlus$button == null) {
                viaFabricPlus$button = Button
                    .builder(Component.nullToEmpty("ViaFabricPlus"), _ -> ViaFabricPlus.api().screens().openViaFabricPlusScreen(this))
                    .size(98, 20)
                    .build();
                this.addRenderableWidget(viaFabricPlus$button);
            }
            orientation.getPositioner().setPosition(viaFabricPlus$button, width, height);
        }
    }

}
