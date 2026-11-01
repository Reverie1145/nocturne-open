package cn.reverie.mixin.client;

import cn.reverie.client.nocturneClient;
import cn.reverie.manager.ClientLifecycleManager;
import cn.reverie.manager.ClientLanguageManager;
import cn.reverie.manager.PlayerHookManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Shadow public abstract DeltaTracker getDeltaTracker();

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwShowWindow(J)V"))
    private void nocturne$beforeShowWindow(CallbackInfo ci) {
        // Holds the game window back until the protection splash has reported
        // real completion and faded out (see ProtectionSplashWindow.awaitSplash).
        ClientLifecycleManager.awaitStartupSplash();
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void nocturne$onInit(CallbackInfo ci) {
        ClientLifecycleManager.initializeClient();
    }

    @Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
    private void nocturne$versionedWindowTitle(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue("Nocturne " + nocturneClient.RELEASE_VERSION);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void nocturne$onTick(CallbackInfo ci) {
        ClientLanguageManager.ensureEnglish();
        ClientLifecycleManager.tickClient();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void nocturne$onPostTick(CallbackInfo ci) {
        ClientLifecycleManager.postTickClient();
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void nocturne$queueHumanClicks(CallbackInfo ci) {
        ClientLifecycleManager.queueHumanClicks();
    }

    @Inject(method = "getTickTargetMillis", at = @At("RETURN"), cancellable = true)
    private void nocturne$timerScaledTickTarget(float defaultTickTargetMillis, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(ClientLifecycleManager.applyTickTargetMillis(cir.getReturnValue()));
    }

    @Inject(method = "handleKeybinds", at = @At("TAIL"))
    private void nocturne$restoreHumanCrosshair(CallbackInfo ci) {
        ClientLifecycleManager.restoreHumanCrosshair();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void nocturne$autoblockUseItem(CallbackInfo ci) {
        if (ClientLifecycleManager.shouldSuppressAuraUseItem()) {
            ci.cancel();
        }
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void nocturne$autoblockAttackInput(CallbackInfoReturnable<Boolean> cir) {
        if (ClientLifecycleManager.shouldSuppressAuraAttackInput()) {
            cir.setReturnValue(false);
        }
    }

    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;releaseUsingItem(Lnet/minecraft/world/entity/player/Player;)V"))
    private void nocturne$autoblockReleaseItem(MultiPlayerGameMode gameMode, Player player) {
        if (!ClientLifecycleManager.auraOwnsUseItem()) {
            gameMode.releaseUsingItem(player);
        }
    }

    @Redirect(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult nocturne$scaffoldFastUseItemOn(MultiPlayerGameMode gameMode, LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
        return PlayerHookManager.useScaffoldItemOn(gameMode, player, hand, hit);
    }

    @Inject(method = "renderFrame", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuSurface;present()V"))
    private void nocturne$onRenderFrameBeforePresent(boolean advanceGameTime, CallbackInfo ci) {
        ClientLifecycleManager.renderFrame(getDeltaTracker());
    }

    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void nocturne$shouldEntityAppearGlowing(net.minecraft.world.entity.Entity entity, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (ClientLifecycleManager.shouldEntityGlow(entity)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void nocturne$onSetLevel(net.minecraft.client.multiplayer.ClientLevel level, CallbackInfo ci) {
        ClientLifecycleManager.onWorldChanged();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void nocturne$onClose(CallbackInfo ci) {
        ClientLifecycleManager.shutdownClient();
    }
}
