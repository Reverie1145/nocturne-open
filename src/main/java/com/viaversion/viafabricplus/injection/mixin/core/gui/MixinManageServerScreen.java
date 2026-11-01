package com.viaversion.viafabricplus.injection.mixin.core.gui;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.api.settings.impl.Orientation;
import com.viaversion.viafabricplus.injection.access.core.IServerData;
import com.viaversion.viafabricplus.screen.impl.PerServerVersionScreen;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ManageServerScreen.class)
public abstract class MixinManageServerScreen extends Screen {

    @Shadow
    @Final
    private ServerData serverData;

    @Shadow
    private EditBox nameEdit;

    @Shadow
    private EditBox ipEdit;

    @Unique
    private String viaFabricPlus$nameField;

    @Unique
    private String viaFabricPlus$addressField;

    public MixinManageServerScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addVersionSetterButton(CallbackInfo ci) {
        final Orientation orientation = ViaFabricPlusImpl.impl().options().addServerScreenButtonOrientation().value();
        if (orientation != Orientation.NONE) {
            final IServerData mixinServerInfo = (IServerData) serverData;
            final ProtocolVersion forcedVersion = mixinServerInfo.viaFabricPlus$forcedVersion();

            if (viaFabricPlus$nameField != null && viaFabricPlus$addressField != null) {
                this.nameEdit.setValue(viaFabricPlus$nameField);
                this.ipEdit.setValue(viaFabricPlus$addressField);

                viaFabricPlus$nameField = null;
                viaFabricPlus$addressField = null;
            }

            final Button.Builder buttonBuilder = Button.builder(forcedVersion == null ? Component.translatable("base.viafabricplus.set_version") : Component.nullToEmpty(forcedVersion.getName()), _ -> {

                viaFabricPlus$nameField = nameEdit.getValue();
                viaFabricPlus$addressField = ipEdit.getValue();

                minecraft.gui.setScreen(new PerServerVersionScreen(this, mixinServerInfo::viaFabricPlus$forceVersion, mixinServerInfo::viaFabricPlus$forcedVersion));
            }).size(98, 20);
            orientation.getPositioner().setPosition(this.addRenderableWidget(buttonBuilder.build()), width, height);
        }
    }

}
