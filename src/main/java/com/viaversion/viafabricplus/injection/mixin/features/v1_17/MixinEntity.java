package com.viaversion.viafabricplus.injection.mixin.features.v1_17;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.injection.access.v1_17.IEntity;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class MixinEntity implements IEntity {

    @Unique
    private boolean viaFabricPlus$isInLoadedChunkAndShouldTick;

    @Override
    public boolean viaFabricPlus$isInLoadedChunkAndShouldTick() {
        return this.viaFabricPlus$isInLoadedChunkAndShouldTick || !ViaFabricPlus.api().targetVersion().betweenInclusive(ProtocolVersion.v1_8, ProtocolVersion.v1_17);
    }

    @Override
    public void viaFabricPlus$setInLoadedChunkAndShouldTick(final boolean inLoadedChunkAndShouldTick) {
        this.viaFabricPlus$isInLoadedChunkAndShouldTick = inLoadedChunkAndShouldTick;
    }

}
