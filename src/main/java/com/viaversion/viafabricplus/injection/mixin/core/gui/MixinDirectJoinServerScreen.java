package com.viaversion.viafabricplus.injection.mixin.core.gui;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.api.settings.impl.Orientation;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DirectJoinServerScreen.class)
public abstract class MixinDirectJoinServerScreen extends Screen {

    public MixinDirectJoinServerScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addProtocolSelectionButton(CallbackInfo ci) {
        final Orientation orientation = ViaFabricPlusImpl.impl().options().directConnectScreenButtonOrientation().value();
        if (orientation != Orientation.NONE) {
            final Button.Builder builder = Button.builder(Component.nullToEmpty("ViaFabricPlus"), _ -> ViaFabricPlus.api().screens().openViaFabricPlusScreen(this)).size(98, 20);
            orientation.getPositioner().setPosition(this.addRenderableWidget(builder.build()), width, height);
        }
    }

}
