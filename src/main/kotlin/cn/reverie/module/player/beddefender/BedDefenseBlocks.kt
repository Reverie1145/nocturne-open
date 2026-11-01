package cn.reverie.module.player.beddefender

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Items
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.FallingBlock
import java.util.IdentityHashMap
import java.util.Locale

/** Registry lookup and block rules shared by the picker and the placement controller. */
internal object BedDefenseBlocks {
    private val usableCache = IdentityHashMap<Block, Boolean>()
    private val nameCache = IdentityHashMap<Block, String>()

    fun find(value: String): Block? {
        val id = Identifier.tryParse(value.trim().lowercase(Locale.ROOT)) ?: return null
        return BuiltInRegistries.BLOCK.getOptional(id).orElse(null)
    }

    fun canUse(block: Block): Boolean = usableCache.getOrPut(block) {
        block !is EntityBlock && block !is FallingBlock && block.asItem() !== Items.AIR &&
            block.defaultBlockState().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
    }

    fun id(block: Block): String = BuiltInRegistries.BLOCK.getKey(block).toString()

    /** Registry paths keep these labels English even when the game's language is changed. */
    fun displayName(block: Block): String = nameCache.getOrPut(block) {
        BuiltInRegistries.BLOCK.getKey(block).path.split('_', '/', '-')
            .filter { it.isNotBlank() }.joinToString(" ") {
                if (it == "tnt") "TNT" else it.replaceFirstChar(Char::uppercaseChar)
            }
    }

    fun canSupport(level: BlockGetter, pos: BlockPos): Boolean {
        val state = level.getBlockState(pos)
        return !state.isAir && !state.canBeReplaced() && state.block !is BedBlock && state.block !is EntityBlock &&
            !state.getCollisionShape(level, pos).isEmpty
    }

    fun previewRgb(block: Block): Int {
        val color = block.defaultBlockState().getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col
        return if (color == 0) 0x94B8CB else color
    }
}
