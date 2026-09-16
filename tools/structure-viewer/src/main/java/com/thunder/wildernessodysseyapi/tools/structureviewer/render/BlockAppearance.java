package com.thunder.wildernessodysseyapi.tools.structureviewer.render;

import com.thunder.wildernessodysseyapi.tools.structureviewer.model.StructureData.BlockState;
import java.awt.Color;

/** Phase 1 material adapter. Replace with asset/model resolution without changing imported data. */
public final class BlockAppearance {
    private BlockAppearance() {}

    /** All materials are explicitly approximate; custom/missing IDs use a conspicuous magenta cube. */
    public static int color(BlockState state) {
        if (!state.id().startsWith("minecraft:")) return 0xe76be5;
        if (state.isAir()) return 0x72909b;
        String id = state.id();
        if (id.contains("glass") || id.contains("ice")) return 0x8bc9d8;
        if (id.contains("water")) return 0x386eae;
        if (id.contains("lava")) return 0xed8035;
        if (id.contains("light") || id.contains("lamp") || id.contains("glow")) return 0xf6d990;
        if (id.contains("grass") || id.contains("leaves") || id.contains("moss")) return 0x648352;
        if (id.contains("wood") || id.contains("planks") || id.contains("log")) return 0xa48055;
        if (id.contains("dirt") || id.contains("mud")) return 0x806553;
        if (id.contains("sand")) return 0xcabc8f;
        if (id.contains("jigsaw") || id.contains("structure")) return 0xd26ead;
        return Color.HSBtoRGB((id.hashCode() & 255) / 255f, 0.08f, 0.52f + ((id.hashCode() >>> 8) & 63) / 255f) & 0xffffff;
    }
}

