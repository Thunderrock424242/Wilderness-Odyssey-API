package com.thunder.wildernessodysseyapi.chunk;

import net.minecraft.world.level.LightLayer;

/**
 * Compressed light data for a single vertical band within a chunk.
 */
public record LightBandDelta(
        LightLayer layer,
        int sectionY,
        byte[] compressedData,
        int rawLength
) {
}
