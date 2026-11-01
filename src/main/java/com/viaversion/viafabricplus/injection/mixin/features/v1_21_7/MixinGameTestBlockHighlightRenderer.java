package com.viaversion.viafabricplus.injection.mixin.features.v1_21_7;

import com.viaversion.viafabricplus.injection.access.v1_21_7.IGameTestBlockHighlightRenderer;
import java.util.Map;
import net.minecraft.client.renderer.debug.GameTestBlockHighlightRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GameTestBlockHighlightRenderer.class)
public abstract class MixinGameTestBlockHighlightRenderer implements IGameTestBlockHighlightRenderer {

    @Shadow
    @Final
    private Map<BlockPos, GameTestBlockHighlightRenderer.Marker> markers;

    @Override
    public void viaFabricPlus$addMarker(final BlockPos pos, final int color, final String message, final int duration) {
        this.markers.put(pos, new GameTestBlockHighlightRenderer.Marker(color, message, Util.getMillis() + duration));
    }

}
