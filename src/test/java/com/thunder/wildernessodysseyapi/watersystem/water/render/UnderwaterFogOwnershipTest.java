package com.thunder.wildernessodysseyapi.watersystem.water.render;

import net.minecraft.world.level.material.FogType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnderwaterFogOwnershipTest {

    @Test
    void externalShaderPackRetainsBothAirAndWaterFog() {
        assertFalse(UnderwaterEffectsRenderer.canOwnWaterFog(FogType.NONE, true));
        assertFalse(UnderwaterEffectsRenderer.canOwnWaterFog(FogType.WATER, true));
    }

    @Test
    void nativeOpticsCanTransitionBetweenAirAndWater() {
        assertTrue(UnderwaterEffectsRenderer.canOwnWaterFog(FogType.NONE, false));
        assertTrue(UnderwaterEffectsRenderer.canOwnWaterFog(FogType.WATER, false));
    }

    @Test
    void neverOverridesLavaOrPowderSnowFog() {
        for (boolean external : new boolean[] {false, true}) {
            assertFalse(UnderwaterEffectsRenderer.canOwnWaterFog(FogType.LAVA, external));
            assertFalse(UnderwaterEffectsRenderer.canOwnWaterFog(FogType.POWDER_SNOW, external));
        }
    }
}
