package com.viaversion.viafabricplus.injection.mixin.features.v1_7_6.screen;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.injection.access.v1_7_6.IPlayerInfo;
import com.viaversion.viafabricplus.injection.access.v1_7_6.IPlayerTabOverlay;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class MixinPlayerTabOverlay implements IPlayerTabOverlay {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private int viaFabricPlus$maxSlots;

    @Unique
    private boolean viaFabricPlus$hideSkins = true;

    @Unique
    private static final Comparator<PlayerInfo> viaFabricPlus$FIFO_COMPARATOR = Comparator.comparingInt(e -> ((IPlayerInfo) e).viaFabricPlus$getIndex());

    @Inject(method = "getPlayerInfos", at = @At("HEAD"), cancellable = true)
    private void collectPlayerEntries(CallbackInfoReturnable<List<PlayerInfo>> result) {
        if (ViaFabricPlusImpl.impl().visuals().enableLegacyTablist().isActive()) {
            result.setReturnValue(this.minecraft.player.connection.getListedOnlinePlayers().stream()
                .sorted(viaFabricPlus$FIFO_COMPARATOR)
                .limit(viaFabricPlus$maxSlots)
                .collect(Collectors.collectingAndThen(Collectors.toList(), this::viaFabricPlus$transpose)));
        } else {
            viaFabricPlus$hideSkins = false;
        }
    }

    @ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;onlineMode()Z"))
    private boolean hideSkins(boolean original) {
        return original && !viaFabricPlus$hideSkins;
    }

    @Override
    public void viaFabricPlus$setMaxPlayers(int maxPlayers) {
        this.viaFabricPlus$maxSlots = Math.clamp(((maxPlayers + PlayerTabOverlay.MAX_ROWS_PER_COL - 1) / PlayerTabOverlay.MAX_ROWS_PER_COL) * PlayerTabOverlay.MAX_ROWS_PER_COL, 20, 200);
    }

    @Unique
    private List<PlayerInfo> viaFabricPlus$transpose(final List<PlayerInfo> list) {

        if (list.size() != viaFabricPlus$maxSlots) {
            viaFabricPlus$hideSkins = list.stream().noneMatch(e -> e.getProfile().properties().containsKey("textures"));
            return list;
        }

        final List<PlayerInfo> result = new ArrayList<>(list.size());

        final int columns = viaFabricPlus$maxSlots / PlayerTabOverlay.MAX_ROWS_PER_COL;
        boolean anyHasSkinData = false;
        for (int i = 0; i < viaFabricPlus$maxSlots; i++) {
            final int row = i % PlayerTabOverlay.MAX_ROWS_PER_COL;
            final int col = i / PlayerTabOverlay.MAX_ROWS_PER_COL;
            final PlayerInfo current = list.get(row * columns + col);
            result.add(current);
            anyHasSkinData = anyHasSkinData || current.getProfile().properties().containsKey("textures");
        }
        viaFabricPlus$hideSkins = !anyHasSkinData;
        return result;
    }

}
