package com.viaversion.viafabricplus.injection.mixin.core.gui;

import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.injection.access.core.IServerData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class MixinServerSelectionList_OnlineServerEntry {

    @Shadow
    @Final
    private ServerData serverData;

    @WrapOperation(method = "extractContent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Lnet/minecraft/network/chat/Component;II)V"))
    private void drawTranslatingState(GuiGraphicsExtractor instance, Component component, int x, int y, Operation<Void> original) {
        final List<Component> tooltips = new ArrayList<>();
        tooltips.add(component);
        if (ViaFabricPlusImpl.impl().options().showAdvertisedServerVersion().isActive()) {
            tooltips.add(Component.translatable("base.viafabricplus.target_version", ((IServerData) serverData).viaFabricPlus$translatingVersion()));
            tooltips.add(Component.translatable("base.viafabricplus.server_version", serverData.version.getString() + " (" + serverData.protocol + ")"));
        }
        instance.setTooltipForNextFrame(Lists.transform(tooltips, Component::getVisualOrderText), x, y);
    }

}
