package com.viaversion.viafabricplus.injection.mixin.features.v1_3_1;

import com.viaversion.viafabricplus.ViaFabricPlus;
import java.util.Optional;
import javax.naming.directory.DirContext;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerRedirectHandler;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerRedirectHandler.class)
public interface MixinServerRedirectHandler {

    @Inject(method = "lambda$createDnsSrvRedirectHandler$0", at = @At("HEAD"), cancellable = true)
    private static void disableSrvForPre1_3(DirContext context, ServerAddress originalAddress, CallbackInfoReturnable<Optional<ServerAddress>> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThan(LegacyProtocolVersion.r1_3_1tor1_3_2)) {
            cir.setReturnValue(Optional.empty());
        }
    }

}
