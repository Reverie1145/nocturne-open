package com.viaversion.viafabricplus.injection.mixin.features.v1_8.item;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.util.Map;
import net.minecraft.world.item.ShovelItem;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(ShovelItem.class)
public abstract class MixinShovelItem {

    @Redirect(method = "useOn", slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/world/item/ShovelItem;FLATTENABLES:Ljava/util/Map;", opcode = Opcodes.GETSTATIC)), at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0, remap = false))
    private Object disablePathAction(Map<Object, Object> instance, Object grassBlock) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            return null;
        } else {
            return instance.get(grassBlock);
        }
    }

}
