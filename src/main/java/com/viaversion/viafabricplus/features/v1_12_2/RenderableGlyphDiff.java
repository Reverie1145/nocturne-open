package com.viaversion.viafabricplus.features.v1_12_2;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.IntTag;
import com.viaversion.nbt.tag.MixedListTag;
import com.viaversion.nbt.tag.Tag;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.protocoltranslator.impl.ViaFabricPlusMappingDataLoader;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Map;

import static com.viaversion.viaversion.api.protocol.version.ProtocolVersion.v1_20;

public final class RenderableGlyphDiff {

    private static final Int2ObjectMap<ProtocolVersion> RENDERABLE_GLYPH_DIFF_LEGACY = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<ProtocolVersion> RENDERABLE_GLYPH_DIFF = new Int2ObjectOpenHashMap<>();

    public static void init() {
        final CompoundTag glyphs = ViaFabricPlusMappingDataLoader.INSTANCE.loadNBT("renderable-glyphs.nbt");
        fill(glyphs.getCompoundTag("legacy"), RENDERABLE_GLYPH_DIFF_LEGACY);
        fill(glyphs.getCompoundTag("unihex"), RENDERABLE_GLYPH_DIFF);
    }

    private static void fill(final CompoundTag glyphs, final Int2ObjectMap<ProtocolVersion> map) {
        for (final Map.Entry<String, Tag> entry : glyphs) {
            final ProtocolVersion version = ProtocolVersion.getClosest(entry.getKey());
            if (version == null) {
                throw new IllegalStateException("Unknown protocol version: " + entry.getKey());
            }

            final MixedListTag list = (MixedListTag) entry.getValue();
            for (final Tag i : list) {
                if (i instanceof final IntTag intTag) {
                    map.put(intTag.asInt(), version);
                } else if (i instanceof CompoundTag compoundTag) {
                    final int from = compoundTag.getInt("from");
                    final int to = compoundTag.getInt("to");
                    for (int j = from; j <= to; j++) {
                        map.put(j, version);
                    }
                }
            }
        }
    }

    public static boolean isGlyphRenderable(final int codePoint) {
        if (ViaFabricPlus.api().targetVersion().newerThanOrEqualTo(v1_20)) {
            return !RENDERABLE_GLYPH_DIFF.containsKey(codePoint) || ViaFabricPlus.api().targetVersion().newerThanOrEqualTo(RENDERABLE_GLYPH_DIFF.get(codePoint));
        } else {
            return RENDERABLE_GLYPH_DIFF_LEGACY.containsKey(codePoint) && ViaFabricPlus.api().targetVersion().newerThanOrEqualTo(RENDERABLE_GLYPH_DIFF_LEGACY.get(codePoint));
        }
    }

}
