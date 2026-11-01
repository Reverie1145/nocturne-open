package com.viaversion.viafabricplus.injection;

import com.viaversion.viafabricplus.features.global.FabricAPIWorkaround;
import com.viaversion.viafabricplus.features.global.LithiumWorkaround;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class ViaFabricPlusMixinPlugin implements IMixinConfigPlugin {

    private static final String MIXINS_PACKAGE = "com.viaversion.viafabricplus.injection.mixin.";

    public static boolean IPNEXT_PRESENT;
    public static boolean MORE_CULLING_PRESENT;
    public static boolean LITHIUM_PRESENT;
    public static boolean MOONRISE_PRESENT;
    public static boolean LEGENDARYTOOLTIPS_PRESENT;
    public static boolean LEGACY_PRESENT;

    @Override
    public void onLoad(String mixinPackage) {

        final FabricLoader loader = FabricLoader.getInstance();
        IPNEXT_PRESENT = loader.isModLoaded("inventoryprofilesnext");
        MORE_CULLING_PRESENT = loader.isModLoaded("moreculling");
        LITHIUM_PRESENT = loader.isModLoaded("lithium");
        MOONRISE_PRESENT = loader.isModLoaded("moonrise");
        LEGENDARYTOOLTIPS_PRESENT = loader.isModLoaded("legendarytooltips");
        LEGACY_PRESENT = loader.isModLoaded("legacy");

        FabricAPIWorkaround.init();
        LithiumWorkaround.init();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return switch (mixinClassName) {
            case MIXINS_PACKAGE + "compat.ipnext.MixinAutoRefillHandler_ItemSlotMonitor" -> IPNEXT_PRESENT;
            case MIXINS_PACKAGE + "compat.lithium.MixinEntity" -> LITHIUM_PRESENT && !MOONRISE_PRESENT;
            case MIXINS_PACKAGE + "features.v1_8.item.MixinItemStack" -> !LEGENDARYTOOLTIPS_PRESENT;
            case MIXINS_PACKAGE + "features.v1_10.MixinGuiGraphicsExtractor" -> !LEGACY_PRESENT;
            default -> true;
        };
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

}
