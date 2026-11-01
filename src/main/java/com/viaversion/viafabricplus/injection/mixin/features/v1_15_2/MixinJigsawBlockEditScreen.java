package com.viaversion.viafabricplus.injection.mixin.features.v1_15_2;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.JigsawBlockEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JigsawBlockEditScreen.class)
public abstract class MixinJigsawBlockEditScreen extends Screen {

    @Shadow
    private EditBox nameEdit;

    @Shadow
    private CycleButton<JigsawBlockEntity.JointType> jointButton;

    @Shadow
    private EditBox targetEdit;

    public MixinJigsawBlockEditScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void disableWidgets(CallbackInfo ci) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_15_2)) {
            nameEdit.active = false;
            jointButton.active = false;
            int index = children().indexOf(jointButton);
            ((AbstractWidget) children().get(index + 1)).active = false;
            ((AbstractWidget) children().get(index + 2)).active = false;
            ((AbstractWidget) children().get(index + 3)).active = false;
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void copyText(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_15_2)) {
            nameEdit.setValue(targetEdit.getValue());
        }
    }

}
