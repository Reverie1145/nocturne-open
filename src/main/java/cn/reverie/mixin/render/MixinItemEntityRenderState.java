package cn.reverie.mixin.render;

import cn.reverie.render.entity.PhysicsItemRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public class MixinItemEntityRenderState implements PhysicsItemRenderState {

    @Unique
    private boolean nocturne$onGround;

    @Unique
    private boolean nocturne$inWater;

    @Unique
    private boolean nocturne$inLava;

    @Unique
    private double nocturne$motionX;

    @Unique
    private double nocturne$motionY;

    @Unique
    private double nocturne$motionZ;

    @Unique
    private float nocturne$yRot;

    @Unique
    private int nocturne$entityId;

    @Override
    public boolean nocturne$isOnGround() {
        return this.nocturne$onGround;
    }

    @Override
    public void nocturne$setOnGround(boolean onGround) {
        this.nocturne$onGround = onGround;
    }

    @Override
    public boolean nocturne$isInWater() {
        return this.nocturne$inWater;
    }

    @Override
    public void nocturne$setInWater(boolean inWater) {
        this.nocturne$inWater = inWater;
    }

    @Override
    public boolean nocturne$isInLava() {
        return this.nocturne$inLava;
    }

    @Override
    public void nocturne$setInLava(boolean inLava) {
        this.nocturne$inLava = inLava;
    }

    @Override
    public double nocturne$getMotionX() {
        return this.nocturne$motionX;
    }

    @Override
    public double nocturne$getMotionY() {
        return this.nocturne$motionY;
    }

    @Override
    public double nocturne$getMotionZ() {
        return this.nocturne$motionZ;
    }

    @Override
    public void nocturne$setMotion(double x, double y, double z) {
        this.nocturne$motionX = x;
        this.nocturne$motionY = y;
        this.nocturne$motionZ = z;
    }

    @Override
    public float nocturne$getYRot() {
        return this.nocturne$yRot;
    }

    @Override
    public void nocturne$setYRot(float yRot) {
        this.nocturne$yRot = yRot;
    }

    @Override
    public int nocturne$getEntityId() {
        return this.nocturne$entityId;
    }

    @Override
    public void nocturne$setEntityId(int entityId) {
        this.nocturne$entityId = entityId;
    }
}
