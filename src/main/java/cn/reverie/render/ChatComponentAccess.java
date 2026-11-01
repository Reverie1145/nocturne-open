package cn.reverie.render;

import net.minecraft.client.multiplayer.chat.GuiMessage;
import java.util.List;

/** Interface added to the vanilla chat component by a mixin. */
public interface ChatComponentAccess {
    List<GuiMessage.Line> nocturne$getTrimmedMessages();
    int nocturne$getChatScrollbarPos();
}
