package com.viaversion.viafabricplus.injection.mixin.compat.classic4j;

import com.viaversion.viafabricplus.injection.access.core.IEditBox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.util.StringUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EditBox.class)
public abstract class MixinEditBox implements IEditBox {

    @Unique
    private boolean viaFabricPlus$forbiddenCharactersUnlocked = false;

    @Redirect(method = "charTyped", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/CharacterEvent;isAllowedChatCharacter()Z"))
    private boolean allowForbiddenCharacters(CharacterEvent instance) {
        return this.viaFabricPlus$forbiddenCharactersUnlocked || instance.isAllowedChatCharacter();
    }

    @Redirect(method = "insertText", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StringUtil;filterText(Ljava/lang/String;)Ljava/lang/String;"))
    private String allowForbiddenCharacters(String input) {
        if (this.viaFabricPlus$forbiddenCharactersUnlocked) {
            return input;
        } else {
            return StringUtil.filterText(input);
        }
    }

    @Override
    public void viaFabricPlus$unlockForbiddenCharacters() {
        this.viaFabricPlus$forbiddenCharactersUnlocked = true;
    }

}
