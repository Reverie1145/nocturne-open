package com.viaversion.viafabricplus.injection.mixin.features.v1_8.block;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LadderBlock.class)
public abstract class MixinLadderBlock extends Block {

    @Shadow
    @Final
    public static Map<Direction, VoxelShape> SHAPES;

    @Shadow
    @Final
    public static EnumProperty<Direction> FACING;

    @Unique
    private static final Map<Direction, VoxelShape> viaFabricPlus$shapes_r1_8_x = Map.of(
        Direction.NORTH, Block.box(0.0D, 0.0D, 14.0D, 16.0D, 16.0D, 16.0D),
        Direction.SOUTH, Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 2.0D),
        Direction.WEST, Block.box(14.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D),
        Direction.EAST, Block.box(0.0D, 0.0D, 0.0D, 2.0D, 16.0D, 16.0D)
    );

    public MixinLadderBlock(final Properties settings) {
        super(settings);
    }

    @Redirect(method = "getShape", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/block/LadderBlock;SHAPES:Ljava/util/Map;", opcode = Opcodes.GETSTATIC))
    private Map<Direction, VoxelShape> changeOutlineShape() {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            return viaFabricPlus$shapes_r1_8_x;
        } else {
            return SHAPES;
        }
    }

    @Override
    public @NonNull VoxelShape getOcclusionShape(@NonNull BlockState state) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            return SHAPES.get(state.getValue(FACING));
        } else {
            return super.getOcclusionShape(state);
        }
    }

}
