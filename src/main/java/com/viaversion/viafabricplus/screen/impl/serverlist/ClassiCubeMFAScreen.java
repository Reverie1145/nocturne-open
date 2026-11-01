package com.viaversion.viafabricplus.screen.impl.serverlist;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.features.global.ClassiCubeAccount;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import de.florianreuth.classic4j.api.LoginProcessHandler;
import de.florianreuth.classic4j.model.classicube.account.CCAccount;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public final class ClassiCubeMFAScreen extends VFPScreen {

    public ClassiCubeMFAScreen() {
        super(Component.translatable("screen.viafabricplus.classicube_mfa"), true);
    }

    private EditBox mfaField;

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(mfaField = new EditBox(font, width / 2 - 150, 70 + 10, 300, 20, Component.empty()));

        mfaField.setHint(Component.nullToEmpty("MFA"));

        this.addRenderableWidget(Button.builder(Component.translatable("base.viafabricplus.login"), _ -> {
            ClassiCubeAccount.authenticate(mfaField.getValue(), new LoginProcessHandler() {
                @Override
                public void handleMfa(CCAccount account) {

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
        }).pos(width / 2 - 75, mfaField.getY() + (20 * 4) + 5).size(150, 20).build());
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
