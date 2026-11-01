package com.viaversion.viafabricplus.screen.impl.serverlist;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.features.global.ClassiCubeAccount;
import com.viaversion.viafabricplus.injection.access.core.IEditBox;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import de.florianreuth.classic4j.api.LoginProcessHandler;
import de.florianreuth.classic4j.model.classicube.account.CCAccount;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public final class ClassiCubeLoginScreen extends VFPScreen {

    public ClassiCubeLoginScreen() {
        super(Component.translatable("screen.viafabricplus.classicube_login"), true);
    }

    private EditBox nameField;
    private EditBox passwordField;

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(nameField = new EditBox(font, width / 2 - 150, 70 + 10, 300, 20, Component.empty()));
        this.addRenderableWidget(passwordField = new EditBox(font, width / 2 - 150, nameField.getY() + 20 + 5, 300, 20, Component.empty()));
        passwordField.addFormatter((s, integer) -> Component.nullToEmpty("*".repeat(s.length())).getVisualOrderText());

        nameField.setHint(Component.translatable("base.viafabricplus.name"));
        passwordField.setHint(Component.translatable("base.viafabricplus.password"));

        nameField.setMaxLength(Integer.MAX_VALUE);
        passwordField.setMaxLength(Integer.MAX_VALUE);

        ((IEditBox) nameField).viaFabricPlus$unlockForbiddenCharacters();
        ((IEditBox) passwordField).viaFabricPlus$unlockForbiddenCharacters();

        if (ClassiCubeAccount.get() != null) {
            nameField.setValue(ClassiCubeAccount.get().username());
            passwordField.setValue(ClassiCubeAccount.get().password());
        }

        this.addRenderableWidget(Button.builder(Component.translatable("base.viafabricplus.login"), _ -> {
            ClassiCubeAccount.set(new CCAccount(nameField.getValue(), passwordField.getValue()));

            ClassiCubeAccount.authenticate(null, new LoginProcessHandler() {

                @Override
                public void handleMfa(CCAccount account) {
                    showToast(Component.translatable("classic4j_library.viafabricplus.error.logincode"));
                    ViaFabricPlusImpl.impl().screens().classiCubeMFAScreen().open(prevScreen);
                }

                @Override
                public void handleSuccessfulLogin(CCAccount account) {
                    ViaFabricPlusImpl.impl().screens().classiCubeServerListScreen().open(prevScreen);
                }

                @Override
                public void handleException(Throwable throwable) {
                    ViaFabricPlusImpl.impl().logger().error("Error while logging in to ClassiCube!", throwable);
                    showToast(Component.nullToEmpty(throwable.getMessage()));
                }
            });
        }).pos(width / 2 - 75, passwordField.getY() + (20 * 4) + 5).size(150, 20).build());
    }

    @Override
    public void onClose() {

        ClassiCubeAccount.set(null);
        super.onClose();
    }

    @Override
    public void extractRenderState(final @NonNull GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        this.renderScreenTitle(graphics);
    }

}
