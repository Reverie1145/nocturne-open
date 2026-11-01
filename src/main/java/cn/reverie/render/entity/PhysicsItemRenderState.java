package cn.reverie.render.entity;

public interface PhysicsItemRenderState {
    boolean nocturne$isOnGround();
    void nocturne$setOnGround(boolean onGround);

    boolean nocturne$isInWater();
    void nocturne$setInWater(boolean inWater);

    boolean nocturne$isInLava();
    void nocturne$setInLava(boolean inLava);

    double nocturne$getMotionX();
    double nocturne$getMotionY();
    double nocturne$getMotionZ();
    void nocturne$setMotion(double x, double y, double z);

    float nocturne$getYRot();
    void nocturne$setYRot(float yRot);

    int nocturne$getEntityId();
    void nocturne$setEntityId(int entityId);
}
