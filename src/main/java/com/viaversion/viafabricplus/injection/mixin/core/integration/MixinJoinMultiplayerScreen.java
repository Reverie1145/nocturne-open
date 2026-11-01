package com.viaversion.viafabricplus.injection.mixin.core.integration;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.viaversion.viafabricplus.injection.access.core.IServerData;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MixinJoinMultiplayerScreen extends Screen {

    public MixinJoinMultiplayerScreen(final Component component) {
        super(component);
    }

    @WrapOperation(method = "directJoinCallback", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/multiplayer/JoinMultiplayerScreen;join(Lnet/minecraft/client/multiplayer/ServerData;)V"))
    private void storeDirectConnectionPhase(JoinMultiplayerScreen instance, ServerData data, Operation<Void> original) {
        ((IServerData) data).viaFabricPlus$passDirectConnectScreen(true);
        original.call(instance, data);
    }

}
