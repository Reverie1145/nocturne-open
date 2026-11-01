package cn.reverie.mixin.world;

import cn.reverie.manager.WorldHookManager;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Scoreboard.class)
public abstract class MixinScoreboard {

    @Shadow @Final private Object2ObjectMap<String, PlayerTeam> teamsByPlayer;
    @Shadow @Final private static Logger LOGGER;
    @Shadow public abstract PlayerTeam getPlayersTeam(String name);

    @Inject(method = "removePlayerFromTeam(Ljava/lang/String;Lnet/minecraft/world/scores/PlayerTeam;)V", at = @At("HEAD"), cancellable = true)
    private void nocturne$removePlayerFromTeam(String player, PlayerTeam team, CallbackInfo ci) {
        if (WorldHookManager.useHeypixelTeamRules()) {
            if (this.getPlayersTeam(player) != team) {
                LOGGER.error("Player is either on another team or not on any team. Cannot remove from team '{}'.", team.getName());
            } else {
                this.teamsByPlayer.remove(player);
                team.getPlayers().remove(player);
            }
            ci.cancel();
        }
    }
}
