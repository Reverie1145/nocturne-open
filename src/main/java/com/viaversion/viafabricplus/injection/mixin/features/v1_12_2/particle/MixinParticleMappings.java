package com.viaversion.viafabricplus.injection.mixin.features.v1_12_2.particle;

import com.viaversion.viafabricplus.features.v1_12_2.FootStepParticle1_12_2;
import com.viaversion.viaversion.api.data.FullMappingsBase;
import com.viaversion.viaversion.api.data.MappingDataLoader;
import com.viaversion.viaversion.api.data.Mappings;
import com.viaversion.viaversion.api.data.ParticleMappings;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ParticleMappings.class)
public abstract class MixinParticleMappings extends FullMappingsBase {

    public MixinParticleMappings(final MappingDataLoader.IdentifiersPair identifiersPair, final Mappings mappings) {
        super(identifiersPair, mappings);
    }

    @Override
    public int getNewId(int id) {
        if (id == FootStepParticle1_12_2.RAW_ID) {
            return id;
        } else {
            return super.getNewId(id);
        }
    }

    @Override
    public String mappedIdentifier(int mappedId) {
        if (mappedId == FootStepParticle1_12_2.RAW_ID) {
            return "";
        } else {
            return super.mappedIdentifier(mappedId);
        }
    }

}
