package com.viaversion.viafabricplus.injection.mixin.features.v1_12_2;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.features.v1_12_2.BuiltinEmptyGlyph1_12_2;
import com.viaversion.viafabricplus.features.v1_12_2.RenderableGlyphDiff;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FontSet.class)
public abstract class MixinFontSet {

    @Shadow
    @Final
    private GlyphStitcher stitcher;

    @Shadow
    @Final
    private FontSet.SelectedGlyphs missingSelectedGlyphs;

    @Unique
    private BakedGlyph viaFabricPlus$blankBakedGlyph1_12_2;

    @Unique
    private FontSet.SelectedGlyphs viaFabricPlus$blankBakedGlyphPair1_12_2;

    @Unique
    private boolean viaFabricPlus$obfuscatedLookup;

    @Inject(method = "resetTextures", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/font/glyphs/SpecialGlyphs;bake(Lnet/minecraft/client/gui/font/GlyphStitcher;)Lnet/minecraft/client/gui/font/glyphs/BakedSheetGlyph;", ordinal = 0))
    private void bakeBlankGlyph1_12_2(CallbackInfo ci) {
        this.viaFabricPlus$blankBakedGlyph1_12_2 = BuiltinEmptyGlyph1_12_2.INSTANCE.bake(this.stitcher);
        this.viaFabricPlus$blankBakedGlyphPair1_12_2 = new FontSet.SelectedGlyphs(() -> this.viaFabricPlus$blankBakedGlyph1_12_2, () -> this.viaFabricPlus$blankBakedGlyph1_12_2);
    }

    @Inject(method = "getGlyph", at = @At("RETURN"), cancellable = true)
    private void filterBakedGlyph(int codepoint, CallbackInfoReturnable<FontSet.SelectedGlyphs> cir) {
        if (this.viaFabricPlus$shouldBeInvisible(codepoint)) {
            if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
                cir.setReturnValue(viaFabricPlus$blankBakedGlyphPair1_12_2);
            } else {
                cir.setReturnValue(missingSelectedGlyphs);
            }
        }
    }

    @Inject(method = "computeGlyphInfo", at = @At("RETURN"), cancellable = true)
    private void fixBlankGlyph1_12_2(int codepoint, CallbackInfoReturnable<FontSet.SelectedGlyphs> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            final FontSet.SelectedGlyphs glyphPair = cir.getReturnValue();
            cir.setReturnValue(glyphPair == this.missingSelectedGlyphs ? this.viaFabricPlus$blankBakedGlyphPair1_12_2 : glyphPair);
        }
    }

    @Inject(method = "getRandomGlyph", at = @At("HEAD"))
    private void pauseCharacterFiltering(RandomSource random, int width, CallbackInfoReturnable<BakedGlyph> cir) {
        viaFabricPlus$obfuscatedLookup = true;
    }

    @Inject(method = "getGlyph", at = @At("RETURN"))
    private void resumeCharacterFiltering(int codepoint, CallbackInfoReturnable<BakedGlyph> cir) {
        viaFabricPlus$obfuscatedLookup = false;
    }

    @Unique
    private boolean viaFabricPlus$shouldBeInvisible(final int codePoint) {
        if (!viaFabricPlus$obfuscatedLookup && ViaFabricPlusImpl.impl().advanced().filterNonExistingGlyphs().isActive()) {
            return (stitcher.texturePrefix.equals(Minecraft.DEFAULT_FONT)) && !RenderableGlyphDiff.isGlyphRenderable(codePoint);
        } else {
            return false;
        }
    }

}
