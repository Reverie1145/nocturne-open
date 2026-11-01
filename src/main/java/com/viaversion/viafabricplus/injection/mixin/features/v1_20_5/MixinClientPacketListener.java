package com.viaversion.viafabricplus.injection.mixin.features.v1_20_5;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {

    @Shadow
    private @Nullable LevelLoadTracker levelLoadTracker;

    @Redirect(method = "handleGameEvent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", ordinal = 0))
    private void handleWinGameState0(Gui instance, Screen screen, @Local(name = "param") int param) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_5)) {
            if (param == 0) {
                Minecraft.getInstance().player.connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                instance.setScreen(new LevelLoadingScreen(this.levelLoadTracker, LevelLoadingScreen.Reason.END_PORTAL));
            } else if (param == 1) {
                instance.setScreen(screen);
            }
        } else {
            instance.setScreen(screen);
        }
    }

    @WrapWithCondition(method = "handleConfigurationStart", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendChatAcknowledgement()V"))
    private boolean dontSendChatAck(ClientPacketListener instance) {
        return ViaFabricPlus.api().targetVersion().newerThanOrEqualTo(ProtocolVersion.v1_20_5);
    }

    @Redirect(method = "handleOpenBook", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/BookViewScreen$BookAccess;fromItem(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/client/gui/screens/inventory/BookViewScreen$BookAccess;"))
    private BookViewScreen.BookAccess dontOpenWriteableBookScreen(ItemStack itemStack) {
        if (ViaFabricPlus.api().targetVersion().newerThanOrEqualTo(ProtocolVersion.v1_20_5) || itemStack.is(Items.WRITTEN_BOOK)) {
            return BookViewScreen.BookAccess.fromItem(itemStack);
        } else {
            return null;
        }
    }

}
