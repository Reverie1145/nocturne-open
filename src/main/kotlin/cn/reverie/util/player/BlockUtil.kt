package cn.reverie.util.player

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.StairBlock

object BlockUtil {

    fun getBlockSlot(maxStack: Boolean): Int {
        val player = Minecraft.getInstance().player ?: return -1
        var bestSlot = -1
        var maxCount = -1

        for (i in 0 until 9) {
            val stack = player.inventory.getItem(i)
            if (stack.isEmpty || stack.item !is BlockItem) {
                continue
            }

            val blockItem = stack.item as BlockItem
            if (!isPlaceable(blockItem.block)) {
                continue
            }

            if (!maxStack) {
                return i
            }

            val currentCount = stack.count
            if (currentCount > maxCount) {
                maxCount = currentCount
                bestSlot = i
            }
        }

        return bestSlot
    }

    fun isPlaceable(block: Block): Boolean {
        if (block == Blocks.AIR || block is EntityBlock) return false
        val state = block.defaultBlockState()
        return state.isSolidRender || block is SlabBlock || block is StairBlock
    }

    fun getBlockCount(): Int {
        val player = Minecraft.getInstance().player ?: return 0
        var total = 0
        for (i in 0 until 36) {
            val stack = player.inventory.getItem(i)
            if (!stack.isEmpty && stack.item is BlockItem) {
                val blockItem = stack.item as BlockItem
                if (isPlaceable(blockItem.block)) {
                    total += stack.count
                }
            }
        }
        return total
    }
}
