package com.viaversion.viafabricplus.injection.mixin.features.v1_20_2.network;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.injection.access.v1_20_2.ILevelLoadingScreen;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class MixinLevelLoadingScreen extends Screen implements ILevelLoadingScreen {

    @Unique
    private long viaFabricPlus$loadStartTime;

    @Unique
    private int viaFabricPlus$tickCounter;

    @Unique
    private boolean viaFabricPlus$ready;

    @Unique
    private boolean viaFabricPlus$closeOnNextTick = false;

    public MixinLevelLoadingScreen(Component title) {
        super(title);
        this.viaFabricPlus$loadStartTime = Util.getMillis();
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void modifyCloseCondition(CallbackInfo ci) {
        if (Minecraft.getInstance() != null && Minecraft.getInstance().isLocalServer()) {

            return;
        }

        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_2)) {
            ci.cancel();

            if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_18)) {
                if (this.viaFabricPlus$ready) {
                    this.onClose();
                }

                if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_1)) {
                    this.viaFabricPlus$tickCounter++;
                    if (this.viaFabricPlus$tickCounter % 20 == 0) {
                        this.minecraft.getConnection().send(new ServerboundKeepAlivePacket(0));
                    }
                }
            } else {
                if (System.currentTimeMillis() > this.viaFabricPlus$loadStartTime + 30000L) {
                    this.onClose();
                } else {
                    if (this.viaFabricPlus$closeOnNextTick) {
                        if (this.minecraft.player == null) return;

                        final BlockPos blockPos = this.minecraft.player.blockPosition();
                        final boolean isOutOfHeightLimit = this.minecraft.level != null && this.minecraft.level.isOutsideBuildHeight(blockPos.getY());
                        if (isOutOfHeightLimit || this.minecraft.levelRenderer.isSectionCompiledAndVisible(blockPos) || this.minecraft.player.isSpectator() || !this.minecraft.player.isAlive()) {
                            this.onClose();
                        }
                    } else {
                        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_19_1)) {
                            this.viaFabricPlus$closeOnNextTick = this.viaFabricPlus$ready || System.currentTimeMillis() > this.viaFabricPlus$loadStartTime + 2000;
                        } else {
                            this.viaFabricPlus$closeOnNextTick = this.viaFabricPlus$ready;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void viaFabricPlus$setReady() {
        this.viaFabricPlus$ready = true;
    }

}
