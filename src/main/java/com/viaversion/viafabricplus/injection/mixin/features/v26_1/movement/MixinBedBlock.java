package com.viaversion.viafabricplus.injection.mixin.features.v26_1.movement;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BedBlock.class)
public abstract class MixinBedBlock extends HorizontalDirectionalBlock {

    protected MixinBedBlock(final Properties properties) {
        super(properties);
    }

    @Override
    public float getBounceRestitution() {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_11_1)) {
            return 0F;
        } else if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1)) {
            return 0.66F;
        } else {
            return super.getBounceRestitution();
        }
    }

}
