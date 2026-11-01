package cn.reverie.mixin.client;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {

    @Accessor("carriedIndex")
    int nocturneGetCarriedIndex();

    @Accessor("carriedIndex")
    void nocturneSetCarriedIndex(int carriedIndex);

    @Accessor("destroyProgress")
    float nocturneGetDestroyProgress();

    @Invoker("ensureHasSentCarriedItem")
    void nocturneEnsureHasSentCarriedItem();
}
