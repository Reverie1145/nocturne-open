package cn.reverie.util.player

import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.*
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.item.enchantment.EnchantmentHelper
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.tags.ItemTags
import net.minecraft.world.level.block.*

object ArmorUtils {
    val armorHelmets: List<Item> = listOf(
        Items.LEATHER_HELMET, Items.CHAINMAIL_HELMET, Items.GOLDEN_HELMET,
        Items.IRON_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET, Items.TURTLE_HELMET
    )

    val armorChestPlates: List<Item> = listOf(
        Items.LEATHER_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.GOLDEN_CHESTPLATE,
        Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE
    )

    val armorLeggings: List<Item> = listOf(
        Items.LEATHER_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.GOLDEN_LEGGINGS,
        Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS
    )

    val armorBoots: List<Item> = listOf(
        Items.LEATHER_BOOTS, Items.CHAINMAIL_BOOTS, Items.GOLDEN_BOOTS,
        Items.IRON_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS
    )
}

object InvItemHelper {

    const val HELMET_SLOT_INVENTORY = 39
    const val HELMET_SLOT_INTERACTION_MANAGER = 5
    const val CHEST_PLATE_SLOT_INVENTORY = 38
    const val CHEST_PLATE_SLOT_INTERACTION_MANAGER = 6
    const val LEGGINGS_SLOT_INVENTORY = 37
    const val LEGGINGS_SLOT_INTERACTION_MANAGER = 7
    const val BOOTS_SLOT_INVENTORY = 36
    const val BOOTS_SLOT_INTERACTION_MANAGER = 8

    enum class ArmorType {
        HELMET, CHESTPLATE, LEGGINGS, BOOTS
    }

    private val mc: Minecraft get() = Minecraft.getInstance()

    fun getItemPath(stack: ItemStack): String {
        if (stack.isEmpty) return ""
        return BuiltInRegistries.ITEM.getKey(stack.item).path.lowercase()
    }

    fun getEquipmentSlot(stack: ItemStack): EquipmentSlot? {
        if (stack.isEmpty) return null
        val item = stack.item
        return when {
            ArmorUtils.armorHelmets.contains(item) -> EquipmentSlot.HEAD
            ArmorUtils.armorChestPlates.contains(item) -> EquipmentSlot.CHEST
            ArmorUtils.armorLeggings.contains(item) -> EquipmentSlot.LEGS
            ArmorUtils.armorBoots.contains(item) -> EquipmentSlot.FEET
            else -> {
                val path = getItemPath(stack)
                when {
                    path.endsWith("_helmet") || path.endsWith("_cap") || path == "turtle_helmet" -> EquipmentSlot.HEAD
                    path.endsWith("_chestplate") || path.endsWith("_tunic") || path == "elytra" -> EquipmentSlot.CHEST
                    path.endsWith("_leggings") || path.endsWith("_pants") -> EquipmentSlot.LEGS
                    path.endsWith("_boots") -> EquipmentSlot.FEET
                    else -> null
                }
            }
        }
    }

    fun getEquipmentType(stack: ItemStack): ArmorType? {
        if (stack.isEmpty) return null
        val item = stack.item
        return when {
            ArmorUtils.armorHelmets.contains(item) -> ArmorType.HELMET
            ArmorUtils.armorChestPlates.contains(item) -> ArmorType.CHESTPLATE
            ArmorUtils.armorLeggings.contains(item) -> ArmorType.LEGGINGS
            ArmorUtils.armorBoots.contains(item) -> ArmorType.BOOTS
            else -> {
                when (getEquipmentSlot(stack)) {
                    EquipmentSlot.HEAD -> ArmorType.HELMET
                    EquipmentSlot.CHEST -> ArmorType.CHESTPLATE
                    EquipmentSlot.LEGS -> ArmorType.LEGGINGS
                    EquipmentSlot.FEET -> ArmorType.BOOTS
                    else -> null
                }
            }
        }
    }

    fun isArmor(stack: ItemStack): Boolean = getEquipmentType(stack) != null

    fun getEnchantLevel(stack: ItemStack, key: ResourceKey<Enchantment>): Int {
        if (stack.isEmpty) return 0
        val level = mc.level ?: return 0
        return try {
            val registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
            val holder = registry.getOrThrow(key)
            EnchantmentHelper.getItemEnchantmentLevel(holder, stack)
        } catch (_: Exception) {
            0
        }
    }

    fun getProtection(stack: ItemStack): Float {
        if (stack.isEmpty) return 0f
        var valence = 0f

        val modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS)
        if (modifiers != null) {
            for (entry in modifiers.modifiers()) {
                val holder = entry.attribute()
                if (holder == Attributes.ARMOR || holder.value() == Attributes.ARMOR.value()) {
                    if (entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                        valence += entry.modifier().amount().toFloat()
                    }
                }
                if (holder == Attributes.ARMOR_TOUGHNESS || holder.value() == Attributes.ARMOR_TOUGHNESS.value()) {
                    if (entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                        valence += entry.modifier().amount().toFloat()
                    }
                }
            }
        }

        if (valence == 0f) {
            val path = getItemPath(stack)
            valence += when {
                path.contains("leather_") -> when {
                    path.endsWith("helmet") -> 1f; path.endsWith("chestplate") -> 3f; path.endsWith("leggings") -> 2f; path.endsWith("boots") -> 1f; else -> 1f
                }
                path.contains("chainmail_") -> when {
                    path.endsWith("helmet") -> 2f; path.endsWith("chestplate") -> 5f; path.endsWith("leggings") -> 4f; path.endsWith("boots") -> 1f; else -> 2f
                }
                path.contains("golden_") -> when {
                    path.endsWith("helmet") -> 2f; path.endsWith("chestplate") -> 5f; path.endsWith("leggings") -> 3f; path.endsWith("boots") -> 1f; else -> 2f
                }
                path.contains("iron_") -> when {
                    path.endsWith("helmet") -> 2f; path.endsWith("chestplate") -> 6f; path.endsWith("leggings") -> 5f; path.endsWith("boots") -> 2f; else -> 2f
                }
                path.contains("diamond_") -> when {
                    path.endsWith("helmet") -> 5f; path.endsWith("chestplate") -> 10f; path.endsWith("leggings") -> 8f; path.endsWith("boots") -> 5f; else -> 5f
                }
                path.contains("netherite_") -> when {
                    path.endsWith("helmet") -> 6f; path.endsWith("chestplate") -> 11f; path.endsWith("leggings") -> 9f; path.endsWith("boots") -> 6f; else -> 6f
                }
                path == "turtle_helmet" -> 2f
                else -> 1f
            }
        }

        valence += getEnchantLevel(stack, Enchantments.PROTECTION).toFloat()
        return valence
    }

    fun getArmorScore(stack: ItemStack): Float = getProtection(stack)

    fun getBestArmorScore(type: ArmorType): Float {
        return getAllItems()
            .filter { !it.isEmpty && getEquipmentType(it) == type }
            .map { getProtection(it) }
            .maxOrNull() ?: 0f
    }

    fun getBestArmorScore(slot: EquipmentSlot, items: List<ItemStack> = getAllItems()): Float {
        return items
            .filter { !it.isEmpty && getEquipmentSlot(it) == slot }
            .map { getProtection(it) }
            .maxOrNull() ?: 0f
    }

    fun isDuplicateBestArmor(stack: ItemStack, items: List<ItemStack> = getAllItems()): Boolean {
        val slot = getEquipmentSlot(stack) ?: return false
        val score = getProtection(stack)
        val best = getBestArmorScore(slot, items)
        if (score < best) return false
        val bestList = items.filter { !it.isEmpty && getEquipmentSlot(it) == slot && getProtection(it) == best }
        return bestList.indexOf(stack) > 0
    }

    fun isDuplicateBestSword(stack: ItemStack, items: List<ItemStack> = getAllItems()): Boolean {
        val best = getBestSword(items) ?: return false
        if (stack === best) return false
        val bestDamage = getSwordDamage(best)
        val score = getSwordDamage(stack)
        if (score < bestDamage) return false
        val bestList = items.filter { !it.isEmpty && isSword(it) && getSwordDamage(it) == bestDamage }
        return bestList.indexOf(stack) > 0
    }

    fun isDuplicateBestBow(stack: ItemStack, items: List<ItemStack> = getAllItems()): Boolean {
        val best = getBestPowerBow(items) ?: return false
        if (stack === best) return false
        val bestScore = getPowerBowScore(best)
        val score = getPowerBowScore(stack)
        if (score < bestScore) return false
        val bestList = items.filter { !it.isEmpty && it.item is BowItem && getPowerBowScore(it) == bestScore }
        return bestList.indexOf(stack) > 0
    }

    fun isDuplicateBestPickaxe(stack: ItemStack, items: List<ItemStack> = getAllItems()): Boolean {
        val best = getBestPickaxe(items) ?: return false
        if (stack === best) return false
        val bestScore = getToolScore(best)
        val score = getToolScore(stack)
        if (score < bestScore) return false
        val bestList = items.filter { !it.isEmpty && isPickaxe(it) && getToolScore(it) == bestScore }
        return bestList.indexOf(stack) > 0
    }

    fun isDuplicateBestAxe(stack: ItemStack, items: List<ItemStack> = getAllItems()): Boolean {
        val best = getBestAxe(items) ?: return false
        if (stack === best) return false
        val bestScore = getAxeScore(best)
        val score = getAxeScore(stack)
        if (score < bestScore) return false
        val bestList = items.filter { !it.isEmpty && isAxe(it) && getAxeScore(it) == bestScore }
        return bestList.indexOf(stack) > 0
    }

    fun isDuplicateBestShovel(stack: ItemStack, items: List<ItemStack> = getAllItems()): Boolean {
        val best = getBestShovel(items) ?: return false
        if (stack === best) return false
        val bestScore = getToolScore(best)
        val score = getToolScore(stack)
        if (score < bestScore) return false
        val bestList = items.filter { !it.isEmpty && isShovel(it) && getToolScore(it) == bestScore }
        return bestList.indexOf(stack) > 0
    }

    fun getSwordScore(stack: ItemStack): Float = getSwordDamage(stack)
    fun getFoodScore(stack: ItemStack): Float = if (isFood(stack)) stack.count.toFloat() else 0f

    fun isGodItem(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        if (stack.item == Items.GOLDEN_AXE && getEnchantLevel(stack, Enchantments.SHARPNESS) > 100) return true
        if (stack.item == Items.SLIME_BALL && getEnchantLevel(stack, Enchantments.KNOCKBACK) > 1) return true
        if (stack.item == Items.TOTEM_OF_UNDYING) return true
        return stack.item == Items.END_CRYSTAL
    }

    fun isSword(stack: ItemStack): Boolean = !stack.isEmpty && (stack.typeHolder().`is`(ItemTags.SWORDS) || getItemPath(stack).contains("sword"))
    fun isBow(stack: ItemStack): Boolean = !stack.isEmpty && (stack.item is BowItem || getItemPath(stack).contains("bow"))
    fun isPickaxe(stack: ItemStack): Boolean = !stack.isEmpty && (stack.typeHolder().`is`(ItemTags.PICKAXES) || getItemPath(stack).contains("pickaxe"))
    fun isAxe(stack: ItemStack): Boolean = !stack.isEmpty && (stack.typeHolder().`is`(ItemTags.AXES) || getItemPath(stack).contains("axe"))
    fun isShovel(stack: ItemStack): Boolean = !stack.isEmpty && (stack.typeHolder().`is`(ItemTags.SHOVELS) || getItemPath(stack).contains("shovel"))
    fun isHoe(stack: ItemStack): Boolean = !stack.isEmpty && (stack.typeHolder().`is`(ItemTags.HOES) || getItemPath(stack).contains("hoe"))

    fun getToolScore(stack: ItemStack): Float {
        if (stack.isEmpty) return 0f
        var valence = 0f

        valence += when {
            isPickaxe(stack) -> stack.getDestroySpeed(Blocks.STONE.defaultBlockState())
            isAxe(stack) -> stack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState())
            isShovel(stack) -> stack.getDestroySpeed(Blocks.DIRT.defaultBlockState())
            else -> return 0f
        }

        val efficiency = getEnchantLevel(stack, Enchantments.EFFICIENCY)
        if (efficiency > 0) {
            valence += efficiency * 0.0075f
        }
        return valence
    }

    fun getAxeScore(stack: ItemStack): Float {
        if (stack.isEmpty || !isAxe(stack)) return 0f
        var valence = getToolScore(stack)
        if (isGodItem(stack)) {
            return valence * 1000f
        }
        val sharpness = getEnchantLevel(stack, Enchantments.SHARPNESS)
        if (sharpness > 0) {
            valence += 0.5f * sharpness + 0.5f
        }
        return valence
    }

    fun getSwordDamage(stack: ItemStack): Float {
        if (stack.isEmpty || !isSword(stack)) return 0f
        var valence = 0f

        val comp = stack.get(DataComponents.ATTRIBUTE_MODIFIERS)
        if (comp != null) {
            for (entry in comp.modifiers()) {
                val holder = entry.attribute()
                if (holder == Attributes.ATTACK_DAMAGE || holder.value() == Attributes.ATTACK_DAMAGE.value()) {
                    if (entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                        valence += entry.modifier().amount().toFloat()
                    }
                }
            }
        }

        if (valence == 0f) {
            val path = getItemPath(stack)
            valence += when {
                path.startsWith("netherite_") -> 8f
                path.startsWith("diamond_") -> 7f
                path.startsWith("iron_") -> 6f
                path.startsWith("stone_") -> 5f
                path.startsWith("golden_") -> 4f
                path.startsWith("wooden_") -> 4f
                else -> 4f
            }
        }

        val sharpness = getEnchantLevel(stack, Enchantments.SHARPNESS)
        if (sharpness > 0) {
            valence += 0.5f * sharpness + 0.5f
        }
        return valence
    }

    fun getPowerBowScore(stack: ItemStack): Float {
        if (stack.isEmpty || stack.item !is BowItem) return 0f
        var valence = 10f
        valence += getEnchantLevel(stack, Enchantments.PUNCH) / 10f
        valence += getEnchantLevel(stack, Enchantments.INFINITY)
        valence += getEnchantLevel(stack, Enchantments.FLAME)
        valence += getEnchantLevel(stack, Enchantments.POWER)
        if (stack.maxDamage > 0) {
            valence += stack.damageValue.toFloat() / stack.maxDamage.toFloat()
        }
        return valence
    }

    fun isValidItem(stack: ItemStack): Boolean {
        if (stack.isEmpty) return true
        val item = stack.item
        if (item is BlockItem && item.block is SkullBlock) return false
        val str = stack.hoverName.string
        if (str.contains("Click") || str.contains("Right") || str.contains("\u70B9\u51FB") ||
            str.contains("Teleport") || str.contains("\u4F7F\u7528") || str.contains("\u4F20\u9001") ||
            str.contains("\u518D\u6765")
        ) {
            return false
        }
        return true
    }

    fun isValidBlock(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val item = stack.item
        if (item !is BlockItem) return false
        if (!isValidItem(stack)) return false

        val block = item.block
        if (block is AirBlock || block is StairBlock || block is SlabBlock ||
            block is FenceBlock || block is FenceGateBlock || block is DoorBlock ||
            block is TrapDoorBlock || block is PressurePlateBlock || block is ButtonBlock ||
            block is WallBlock || block is CarpetBlock || block is ConcretePowderBlock ||
            block is TintedGlassBlock || block is StainedGlassBlock || block is StainedGlassPaneBlock ||
            block is BannerBlock || block is LeavesBlock || block is SaplingBlock ||
            block is MushroomBlock || block is FlowerBlock || block is ComposterBlock ||
            block is NoteBlock || block is JukeboxBlock || block is SignBlock ||
            block == Blocks.IRON_BARS || block == Blocks.IRON_CHAIN || block == Blocks.GLASS_PANE ||
            block == Blocks.DIRT_PATH || block == Blocks.FARMLAND || block == Blocks.SNOW ||
            block == Blocks.AMETHYST_CLUSTER || block == Blocks.MANGROVE_ROOTS ||
            block == Blocks.SUGAR_CANE || block == Blocks.CACTUS
        ) {
            return false
        }
        return true
    }

    fun isFood(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        return stack.has(DataComponents.FOOD) || stack.item == Items.POTION || stack.item == Items.MILK_BUCKET
    }

    fun isUsefulItem(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val item = stack.item

        if (isArmor(stack)) return true
        if (item is CrossbowItem || item is BowItem || isAxe(stack) ||
            isPickaxe(stack) || isShovel(stack) || isHoe(stack) || isSword(stack)
        ) return true

        if (item is PotionItem || item is PlayerHeadItem) return true
        if (item is BlockItem) return isValidBlock(stack)

        if (item == Items.SLIME_BALL || item == Items.FIRE_CHARGE || item == Items.TOTEM_OF_UNDYING ||
            item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE || item == Items.FISHING_ROD ||
            item == Items.SNOWBALL || item == Items.EGG || item == Items.BUCKET ||
            item == Items.WATER_BUCKET || item == Items.LAVA_BUCKET || item == Items.MILK_BUCKET ||
            item == Items.ENDER_PEARL || item == Items.ARROW || item == Items.SHIELD ||
            item == Items.END_CRYSTAL || stack.has(DataComponents.FOOD)
        ) {
            return true
        }

        return false
    }

    fun isTrashItem(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        return !isUsefulItem(stack)
    }

    fun getAllItems(): List<ItemStack> {
        val player = mc.player ?: return emptyList()
        val list = ArrayList<ItemStack>(41)
        for (i in 0 until player.inventory.containerSize) {
            list.add(player.inventory.getItem(i))
        }
        return list
    }

    fun getBestSword(items: List<ItemStack> = getAllItems()): ItemStack? {
        return items
            .filter { !it.isEmpty && isSword(it) && isValidItem(it) }
            .maxByOrNull { (getSwordDamage(it) * 100).toInt() }
    }

    fun getBestAxe(items: List<ItemStack> = getAllItems()): ItemStack? {
        return items
            .filter { !it.isEmpty && isAxe(it) && isValidItem(it) }
            .maxByOrNull { (getAxeScore(it) * 100).toInt() }
    }

    fun getBestPickaxe(items: List<ItemStack> = getAllItems()): ItemStack? {
        return items
            .filter { !it.isEmpty && isPickaxe(it) && isValidItem(it) }
            .maxByOrNull { (getToolScore(it) * 100).toInt() }
    }

    fun getBestShovel(items: List<ItemStack> = getAllItems()): ItemStack? {
        return items
            .filter { !it.isEmpty && isShovel(it) && isValidItem(it) }
            .maxByOrNull { (getToolScore(it) * 100).toInt() }
    }

    fun getBestPowerBow(items: List<ItemStack> = getAllItems()): ItemStack? {
        return items
            .filter { !it.isEmpty && it.item is BowItem && isValidItem(it) }
            .maxByOrNull { (getPowerBowScore(it) * 100).toInt() }
    }

    fun isBuildingBlock(stack: ItemStack): Boolean = isValidBlock(stack)

    fun findSlot(item: Item): Int {
        val player = mc.player ?: return -1
        for (i in 0 until 36) {
            val stack = player.inventory.getItem(i)
            if (!stack.isEmpty && stack.item == item) return i
        }
        return -1
    }

    fun findPotionSlot(): Int {
        val player = mc.player ?: return -1
        for (i in 0 until 36) {
            val stack = player.inventory.getItem(i)
            if (!stack.isEmpty && stack.item is PotionItem) return i
        }
        return -1
    }

    fun isInLobbyOrSpectator(): Boolean {
        val player = mc.player ?: return false
        for (i in 0..8) {
            val stack = player.inventory.getItem(i)
            if (!stack.isEmpty && stack.item is BedItem) return true
        }
        return findSlot(Items.NETHER_STAR) != -1 || findSlot(Items.PAPER) != -1
    }
}
