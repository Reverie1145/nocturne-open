package com.viaversion.viafabricplus.injection.mixin.core.access;

import com.viaversion.viafabricplus.injection.access.core.IExtensionProtocolMetadataStorage;
import java.util.EnumMap;
import net.raphimc.vialegacy.protocol.classic.c0_30cpetoc0_28_30.data.ClassicProtocolExtension;
import net.raphimc.vialegacy.protocol.classic.c0_30cpetoc0_28_30.storage.ExtensionProtocolMetadataStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ExtensionProtocolMetadataStorage.class)
public abstract class MixinExtensionProtocolMetadataStorage implements IExtensionProtocolMetadataStorage {

    @Shadow
    @Final
    private EnumMap<ClassicProtocolExtension, Integer> serverExtensions;

    @Override
    public EnumMap<ClassicProtocolExtension, Integer> viaFabricPlus$getServerExtensions() {
        return this.serverExtensions;
    }

}
