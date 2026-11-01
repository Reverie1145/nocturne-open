package com.viaversion.viafabricplus.features.global;

import java.util.Set;
import net.lenni0451.reflect.stream.RStream;

public final class LithiumWorkaround {

    public static void init() {

        final Set<String> loadedMixins = RStream.of("org.spongepowered.asm.mixin.transformer.MixinConfig").fields().by("globalMixinList").get();
        loadedMixins.add("net.caffeinemc.mods.lithium.mixin.math.sine_lut.MthMixin");
    }

}
