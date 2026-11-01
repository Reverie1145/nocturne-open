package cn.reverie.mixin.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Player.class)
public interface PlayerAccessor {

    @Mutable
    @Accessor("gameProfile")
    void nocturneSetGameProfile(GameProfile gameProfile);
}
