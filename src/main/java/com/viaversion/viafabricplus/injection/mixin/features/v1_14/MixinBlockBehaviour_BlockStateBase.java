package com.viaversion.viafabricplus.injection.mixin.features.v1_14;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MixinBlockBehaviour_BlockStateBase {

    @Shadow
    @Final
    private boolean requiresCorrectToolForDrops;

    @Shadow
    public abstract Block getBlock();

    @Overwrite
    public boolean requiresCorrectToolForDrops() {
        if (this.getBlock() instanceof ShulkerBoxBlock && ViaFabricPlus.api().targetVersion().olderThan(ProtocolVersion.v1_14)) {
            return true;
        } else {
            return this.requiresCorrectToolForDrops;
        }
    }

}
