package cn.reverie.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {

    @Invoker("pick")
    void nocturnePick(float tickDelta);

    @Accessor("user")
    @Mutable
    void nocturneSetUser(User user);

    @Accessor("rightClickDelay")
    @Mutable
    void nocturneSetRightClickDelay(int rightClickDelay);
}
