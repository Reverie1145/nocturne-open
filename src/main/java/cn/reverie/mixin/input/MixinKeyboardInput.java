package cn.reverie.mixin.input;

import cn.reverie.app.event.player.MoveInputEvent;
import cn.reverie.manager.InputHookManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class MixinKeyboardInput extends ClientInput {

    @Inject(method = "tick", at = @At("TAIL"))
    private void nocturne$moveInput(CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.input != this) {
            return;
        }
        float forward = this.moveVector.y;
        float strafe = this.moveVector.x;
        boolean jump = this.keyPresses.jump();
        boolean sneak = this.keyPresses.shift();
        boolean sprint = this.keyPresses.sprint();

        MoveInputEvent event = new MoveInputEvent(forward, strafe, jump, sneak, sprint);
        InputHookManager.onMoveInput(player, event);

        float newForward = event.getForward();
        float newStrafe = event.getStrafe();
        boolean newJump = event.getJump();
        boolean newSneak = event.getSneak();
        boolean newSprint = event.getSprint();
        boolean movementChanged = newForward != forward || newStrafe != strafe;

        if (movementChanged || newJump != jump || newSneak != sneak || newSprint != sprint) {
            if (movementChanged) {
                this.moveVector = new Vec2(newStrafe, newForward).normalized();
            }
            this.keyPresses = new Input(
                    movementChanged ? newForward > 0f : this.keyPresses.forward(),
                    movementChanged ? newForward < 0f : this.keyPresses.backward(),
                    movementChanged ? newStrafe > 0f : this.keyPresses.left(),
                    movementChanged ? newStrafe < 0f : this.keyPresses.right(),
                    newJump,
                    newSneak,
                    newSprint
            );
        }
    }
}
