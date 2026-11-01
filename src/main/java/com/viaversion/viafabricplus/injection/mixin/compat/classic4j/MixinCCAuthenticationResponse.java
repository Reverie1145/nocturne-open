package com.viaversion.viafabricplus.injection.mixin.compat.classic4j;

import de.florianreuth.classic4j.model.classicube.CCAuthenticationResponse;
import de.florianreuth.classic4j.model.classicube.CCError;
import net.minecraft.network.chat.Component;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CCAuthenticationResponse.class, remap = false)
public abstract class MixinCCAuthenticationResponse {

    @Redirect(method = "getErrorDisplay", at = @At(value = "FIELD", target = "Lde/florianreuth/classic4j/model/classicube/CCError;description:Ljava/lang/String;", opcode = Opcodes.GETFIELD))
    private String mapTranslations(CCError instance) {
        return switch (instance) {
            case TOKEN -> Component.translatable("classic4j_library.viafabricplus.error.token").getString();
            case USERNAME -> Component.translatable("classic4j_library.viafabricplus.error.username").getString();
            case PASSWORD -> Component.translatable("classic4j_library.viafabricplus.error.password").getString();
            case VERIFICATION -> Component.translatable("classic4j_library.viafabricplus.error.verification").getString();
            case LOGIN_CODE -> Component.translatable("classic4j_library.viafabricplus.error.logincode").getString();
        };
    }

}
