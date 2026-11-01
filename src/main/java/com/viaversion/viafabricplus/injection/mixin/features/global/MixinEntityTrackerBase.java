package com.viaversion.viafabricplus.injection.mixin.features.global;

import com.viaversion.viaversion.data.entity.EntityTrackerBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = EntityTrackerBase.class, remap = false)
public abstract class MixinEntityTrackerBase {

    @Overwrite
    public boolean canInstaBuild() {
        return true;
    }

}
