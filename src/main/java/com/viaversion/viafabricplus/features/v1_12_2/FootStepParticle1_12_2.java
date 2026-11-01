package com.viaversion.viafabricplus.features.v1_12_2;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Quaternionf;

public final class FootStepParticle1_12_2 extends SingleQuadParticle {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("viafabricplus", "footstep");
    public static int RAW_ID;

    private FootStepParticle1_12_2(ClientLevel clientWorld, double x, double y, double z, TextureAtlasSprite sprite) {
        super(clientWorld, x, y, z, sprite);

        this.quadSize = 0.125F;
        this.setLifetime(200);
    }

    public static void init() {
        final SimpleParticleType footStepType = FabricParticleTypes.simple(true);

        Registry.register(BuiltInRegistries.PARTICLE_TYPE, ID, footStepType);
        ParticleProviderRegistry.getInstance().register(footStepType, FootStepParticle1_12_2.Factory::new);

        RAW_ID = BuiltInRegistries.PARTICLE_TYPE.getId(footStepType);
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    protected void extractRotatedQuad(final QuadParticleRenderState submittable, final Camera camera, final Quaternionf rotation, final float tickProgress) {
        final float strength = ((float) this.age + tickProgress) / (float) this.lifetime;
        this.alpha = 2.0F - (strength * strength) * 2.0F;
        if (this.alpha > 1.0F) {
            this.alpha = 0.2F;
        } else {
            this.alpha *= 0.2F;
        }

        super.extractRotatedQuad(submittable, camera, new Quaternionf().rotateX(-Mth.HALF_PI), tickProgress);
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet spriteProvider;

        public Factory(SpriteSet spriteProvider) {
            this.spriteProvider = spriteProvider;
        }

        @Override
        public Particle createParticle(SimpleParticleType parameters, ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, RandomSource random) {
            if (ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_12_2)) {
                throw new UnsupportedOperationException("FootStepParticle is not supported on versions newer than 1.12.2");
            }

            final TextureAtlasSprite sprite = spriteProvider.get(random);
            return new FootStepParticle1_12_2(world, x, y, z, sprite);
        }
    }

}
