package com.viaversion.viafabricplus.injection.mixin.features.v1_12_2;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTeam.class)
public abstract class MixinPlayerTeam {

    @Shadow
    private Component playerPrefix;

    @Shadow
    private Component playerSuffix;

    @Inject(method = "getFormattedName(Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;", at = @At("HEAD"), cancellable = true)
    private void decorateName1_12_2(Component teamMemberName, CallbackInfoReturnable<MutableComponent> cir) {

        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            final Style prefixStyle = viaFabricPlus$getLastStyle(this.playerPrefix);
            final Component nameWithStyle = viaFabricPlus$fillStyle(teamMemberName, prefixStyle);
            final Style nameStyle = viaFabricPlus$getLastStyle(nameWithStyle);

            cir.setReturnValue(Component.empty()
                .append(this.playerPrefix)
                .append(nameWithStyle)
                .append(viaFabricPlus$fillStyle(this.playerSuffix, nameStyle))
            );
        }
    }

    @Unique
    private Style viaFabricPlus$getLastStyle(final Component text) {
        for (int i = text.getSiblings().size() - 1; i >= 0; i--) {
            final Component sibling = text.getSiblings().get(i);
            if (sibling.getStyle() != Style.EMPTY) {
                return sibling.getStyle();
            }
        }

        return text.getStyle();
    }

    @Unique
    private Component viaFabricPlus$fillStyle(final Component text, final Style style) {
        if (text.getStyle() != Style.EMPTY) {
            return text;
        } else {
            return text.copy().withStyle(style);
        }
    }

}
