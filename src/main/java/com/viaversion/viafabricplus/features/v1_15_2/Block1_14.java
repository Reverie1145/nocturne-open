package com.viaversion.viafabricplus.features.v1_15_2;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.TrapDoorBlock;

public final class Block1_14 {

    public static boolean isExceptBlockForAttachWithPiston(final Block block) {
        return block instanceof ShulkerBoxBlock
            || block instanceof LeavesBlock
            || block instanceof TrapDoorBlock
            || block instanceof StainedGlassBlock
            || block == Blocks.BEACON
            || block == Blocks.CAULDRON
            || block == Blocks.GLASS
            || block == Blocks.GLOWSTONE
            || block == Blocks.ICE
            || block == Blocks.SEA_LANTERN
            || block == Blocks.PISTON
            || block == Blocks.STICKY_PISTON
            || block == Blocks.PISTON_HEAD;
    }

}
