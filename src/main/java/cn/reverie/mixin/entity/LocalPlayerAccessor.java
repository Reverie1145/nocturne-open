package cn.reverie.mixin.entity;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {

    @Accessor("wasSprinting")
    boolean nocturneWasSprinting();

    @Accessor("wasSprinting")
    void nocturneSetWasSprinting(boolean sprinting);

    @Accessor("lastSentInput")
    Input nocturneLastSentInput();

    @Accessor("lastSentInput")
    void nocturneSetLastSentInput(Input input);

    @Invoker("itemUseSpeedMultiplier")
    float nocturne$itemUseSpeedMultiplier();
}
