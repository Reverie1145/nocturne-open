package cn.reverie.mixin.input;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {

    @Accessor("xpos")
    double nocturneGetXpos();

    @Accessor("xpos")
    void nocturneSetXpos(double xpos);

    @Accessor("ypos")
    double nocturneGetYpos();

    @Accessor("ypos")
    void nocturneSetYpos(double ypos);

    @Accessor("activeButton")
    MouseButtonInfo nocturneGetActiveButton();

    @Accessor("activeButton")
    void nocturneSetActiveButton(MouseButtonInfo buttonInfo);

    @Accessor("isLeftPressed")
    void nocturneSetLeftPressed(boolean pressed);

    @Accessor("isMiddlePressed")
    void nocturneSetMiddlePressed(boolean pressed);

    @Accessor("isRightPressed")
    void nocturneSetRightPressed(boolean pressed);

    @Invoker("onButton")
    void nocturneInvokeOnButton(long handle, MouseButtonInfo rawButtonInfo, int action);

    @Invoker("onMove")
    void nocturneInvokeOnMove(long handle, double xpos, double ypos);
}
