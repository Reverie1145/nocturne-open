package com.viaversion.viafabricplus.injection.mixin.features.global;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.features.global.ResourcePackHeaderDiff;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.WorldVersion;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.resources.server.DownloadedPackSource$4")
public abstract class MixinDownloadedPackSource_4 {

    @Redirect(method = "createDownloadHeaders", at = @At(value = "INVOKE", target = "Lnet/minecraft/SharedConstants;getCurrentVersion()Lnet/minecraft/WorldVersion;"))
    private WorldVersion editHeaders() {
        return ResourcePackHeaderDiff.get(ViaFabricPlus.api().targetVersion());
    }

    @Redirect(method = "createDownloadHeaders", at = @At(value = "INVOKE", target = "Ljava/lang/String;valueOf(Ljava/lang/Object;)Ljava/lang/String;"))
    private String editHeaders(Object obj) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_7) && obj instanceof final PackFormat packVersion) {
            return String.valueOf(packVersion.major());
        } else {
            return String.valueOf(obj);
        }
    }

    @Inject(method = "createDownloadHeaders", at = @At("TAIL"), cancellable = true)
    private void removeHeaders(CallbackInfoReturnable<Map<String, String>> cir) {
        final LinkedHashMap<String, String> modifiableMap = new LinkedHashMap<>(cir.getReturnValue());
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14_3)) {
            modifiableMap.remove("X-Minecraft-Version-ID");
            if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
                modifiableMap.remove("X-Minecraft-Pack-Format");
                modifiableMap.remove("User-Agent");
            }
        }
        cir.setReturnValue(modifiableMap);
    }

}
