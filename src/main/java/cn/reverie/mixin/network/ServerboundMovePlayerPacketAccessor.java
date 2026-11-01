package cn.reverie.mixin.network;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundMovePlayerPacket.class)
public interface ServerboundMovePlayerPacketAccessor {

    @Accessor("yRot")
    @Mutable
    void nocturneSetYRot(float yRot);

    @Accessor("xRot")
    @Mutable
    void nocturneSetXRot(float xRot);

    @Accessor("y")
    @Mutable
    void nocturneSetY(double y);

    @Accessor("onGround")
    @Mutable
    void nocturneSetOnGround(boolean onGround);
}
