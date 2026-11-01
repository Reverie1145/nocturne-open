package com.viaversion.viafabricplus.injection.mixin.features.v1_7_6.screen;

import com.viaversion.viafabricplus.features.v1_7_6.LegacyTabList;
import com.viaversion.viafabricplus.injection.access.v1_7_6.IPlayerInfo;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerInfo.class)
public abstract class MixinPlayerInfo implements IPlayerInfo {

    @Unique
    private final int viaFabricPlus$index = LegacyTabList.globalTablistIndex++;

    @Override
    public int viaFabricPlus$getIndex() {
        return viaFabricPlus$index;
    }

}
