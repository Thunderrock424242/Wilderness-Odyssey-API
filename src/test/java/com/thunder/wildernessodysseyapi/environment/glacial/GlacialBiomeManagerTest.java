package com.thunder.wildernessodysseyapi.environment.glacial;

import com.thunder.wildernessodysseyapi.worldgen.biome.ModBiomes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlacialBiomeManagerTest {

    @Test
    void glacialBeachUsesCoastalEnvironmentWithoutExpandingStableFamily() {
        assertTrue(GlacialBiomeManager.family(ModBiomes.GLACIAL_BEACH_KEY).isEmpty());
        assertEquals(
                GlacialBiomeManager.Family.ICEBERG_COAST,
                GlacialBiomeManager.environmentalFamily(ModBiomes.GLACIAL_BEACH_KEY)
                        .orElseThrow()
        );
        assertEquals(5, GlacialBiomeManager.coastToInterior().size());
    }
}
