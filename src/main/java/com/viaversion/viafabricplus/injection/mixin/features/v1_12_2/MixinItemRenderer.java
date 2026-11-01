package com.viaversion.viafabricplus.injection.mixin.features.v1_12_2;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemModelResolver.class)
public abstract class MixinItemRenderer {

    @Unique
    private static final Identifier viaFabricPlus$missingIdentifier = Identifier.parse(String.valueOf(System.currentTimeMillis()));

    @Redirect(method = "appendItemLayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
    private Object removeModel(ItemStack instance, DataComponentType<?> componentType) {
        if (ViaFabricPlus.api().targetVersion().betweenInclusive(LegacyProtocolVersion.r1_3_1tor1_3_2, ProtocolVersion.v1_12_2) && instance.is(Items.PETRIFIED_OAK_SLAB)) {
            return viaFabricPlus$missingIdentifier;
        } else {
            return instance.get(componentType);
        }
    }

}
