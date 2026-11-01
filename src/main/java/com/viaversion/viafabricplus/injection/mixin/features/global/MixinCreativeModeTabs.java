package com.viaversion.viafabricplus.injection.mixin.features.global;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.api.settings.impl.ItemFilter;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreativeModeTabs.class)
public abstract class MixinCreativeModeTabs {

    @Shadow
    @Nullable
    private static CreativeModeTab.@Nullable ItemDisplayParameters CACHED_PARAMETERS;

    @Shadow
    private static void buildAllTabContents(CreativeModeTab.ItemDisplayParameters parameters) {
    }

    @Unique
    private static ProtocolVersion viaFabricPlus$version;

    @Unique
    private static ItemFilter viaFabricPlus$state;

    @Inject(method = "tryRebuildTabContents", at = @At("HEAD"), cancellable = true)
    private static void trackLastVersion(FeatureFlagSet enabledFeatures, boolean hasPermissions, HolderLookup.Provider lookup, CallbackInfoReturnable<Boolean> cir) {
        if (viaFabricPlus$version != ViaFabricPlus.api().targetVersion() || viaFabricPlus$state != ViaFabricPlusImpl.impl().options().removeNotAvailableItemsFromCreativeTab().value()) {
            viaFabricPlus$version = ViaFabricPlus.api().targetVersion();
            viaFabricPlus$state = ViaFabricPlusImpl.impl().options().removeNotAvailableItemsFromCreativeTab().value();

            CACHED_PARAMETERS = new CreativeModeTab.ItemDisplayParameters(enabledFeatures, hasPermissions, lookup);
            buildAllTabContents(CACHED_PARAMETERS);

            cir.setReturnValue(true);
        }
    }

}
