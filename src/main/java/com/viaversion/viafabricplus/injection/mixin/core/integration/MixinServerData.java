package com.viaversion.viafabricplus.injection.mixin.core.integration;

import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.injection.access.core.IServerData;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerData.class)
public abstract class MixinServerData implements IServerData {

    @Shadow
    public String name;

    @Unique
    private ProtocolVersion viaFabricPlus$forcedVersion = null;

    @Unique
    private boolean viaFabricPlus$passedDirectConnectScreen;

    @Unique
    private ProtocolVersion viaFabricPlus$translatingVersion;

    @Inject(method = "write", at = @At("TAIL"))
    private void saveForcedVersion(CallbackInfoReturnable<CompoundTag> cir, @Local(name = "tag") CompoundTag tag) {
        if (viaFabricPlus$forcedVersion != null) {
            tag.putString("viafabricplus_forcedversion", viaFabricPlus$forcedVersion.getName());
        }
    }

    @Inject(method = "read", at = @At("TAIL"))
    private static void loadForcedVersion(CompoundTag tag, CallbackInfoReturnable<ServerData> cir, @Local(name = "server") ServerData server) {
        if (tag.contains("viafabricplus_forcedversion")) {
            final ProtocolVersion version = ProtocolVersion.getClosest(tag.getStringOr("viafabricplus_forcedversion", ""));
            if (version != null) {
                ((IServerData) server).viaFabricPlus$forceVersion(version);
            }
        }
    }

    @Inject(method = "copyNameIconFrom", at = @At("RETURN"))
    private void syncForcedVersion(ServerData other, CallbackInfo ci) {
        viaFabricPlus$forceVersion(((IServerData) other).viaFabricPlus$forcedVersion());
    }

    @Override
    public ProtocolVersion viaFabricPlus$forcedVersion() {
        return viaFabricPlus$forcedVersion;
    }

    @Override
    public void viaFabricPlus$forceVersion(ProtocolVersion version) {
        viaFabricPlus$forcedVersion = version;
    }

    @Override
    public boolean viaFabricPlus$passedDirectConnectScreen() {
        return viaFabricPlus$passedDirectConnectScreen;
    }

    @Override
    public void viaFabricPlus$passDirectConnectScreen(boolean state) {
        viaFabricPlus$passedDirectConnectScreen = state;
    }

    @Override
    public ProtocolVersion viaFabricPlus$translatingVersion() {
        return viaFabricPlus$translatingVersion;
    }

    @Override
    public void viaFabricPlus$setTranslatingVersion(ProtocolVersion version) {
        viaFabricPlus$translatingVersion = version;
    }

}
