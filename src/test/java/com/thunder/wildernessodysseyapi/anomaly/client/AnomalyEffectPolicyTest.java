package com.thunder.wildernessodysseyapi.anomaly.client;

import net.minecraft.client.renderer.FogRenderer.FogMode;
import net.minecraft.world.level.material.FogType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnomalyEffectPolicyTest {
    @Test
    void clampsNonFiniteAndOutOfRangeStrengths() {
        assertEquals(0.0F, AnomalyEffectPolicy.clampUnit(Float.NaN));
        assertEquals(0.0F, AnomalyEffectPolicy.clampUnit(Float.POSITIVE_INFINITY));
        assertEquals(0.0F, AnomalyEffectPolicy.clampUnit(-1.0F));
        assertEquals(1.0F, AnomalyEffectPolicy.clampUnit(2.0F));
    }

    @Test
    void accessibilityScaleControlsScreenStrength() {
        assertEquals(0.0F, AnomalyEffectPolicy.screenStrength(1.0F, 1.0F, 1.0F, 0.0F));
        assertEquals(0.25F, AnomalyEffectPolicy.screenStrength(1.0F, 1.0F, 0.5F, 0.5F));
        assertEquals(1.0F, AnomalyEffectPolicy.screenStrength(8.0F, 8.0F, 8.0F, 8.0F));
    }

    @Test
    void motionAndPulsesMustBothBeEnabled() {
        assertEquals(0.0F, AnomalyEffectPolicy.motionAmount(1.0F, 1.0F, false, true, 3.0F));
        assertEquals(0.0F, AnomalyEffectPolicy.motionAmount(1.0F, 1.0F, true, false, 3.0F));
        assertEquals(0.0F, AnomalyEffectPolicy.motionAmount(0.0F, 1.0F, true, true, 3.0F));
        float pulse = AnomalyEffectPolicy.motionAmount(1.0F, 1.0F, true, true, 3.0F);
        assertTrue(pulse > 0.0F && pulse <= 0.0015F);
    }

    @Test
    void riftInfluenceLayersWithoutExceedingTheVisualBudget() {
        float quiet = AnomalyEffectPolicy.combinedStrength(0.2F, 0.0F);
        float rift = AnomalyEffectPolicy.combinedStrength(0.2F, 1.0F);
        assertTrue(rift > quiet);
        assertTrue(rift <= 1.0F);
        assertTrue(AnomalyEffectPolicy.fogBlend(1.0F, 1.0F) <= 0.26F);
    }

    @Test
    void higherBiomeStrengthAndRealRiftsProgressTowardViolet() {
        assertEquals(0.0F, AnomalyEffectPolicy.violetStrength(0.2F, 0.0F));
        assertTrue(AnomalyEffectPolicy.violetStrength(0.86F, 0.0F) > 0.7F);
        assertEquals(1.0F, AnomalyEffectPolicy.violetStrength(0.2F, 1.0F));
    }

    @Test
    void fogDistanceRetainsVisibilityAndNeverExtendsExistingFog() {
        assertEquals(88.0F, AnomalyEffectPolicy.fogFarPlane(100.0F, 1.0F), 0.001F);
        assertTrue(AnomalyEffectPolicy.fogFarPlane(12.0F, 1.0F) <= 12.0F);
        assertEquals(100.0F, AnomalyEffectPolicy.fogFarPlane(100.0F, 0.0F));
    }

    @Test
    void anomalyHazeOnlyAdjustsTerrainAirFogAndLeavesTheSkyAndFluidsAlone() {
        assertTrue(AnomalyEffectPolicy.canAdjustFog(FogMode.FOG_TERRAIN, FogType.NONE));
        assertFalse(AnomalyEffectPolicy.canAdjustFog(FogMode.FOG_SKY, FogType.NONE));
        for (FogType medium : FogType.values()) {
            if (medium != FogType.NONE) {
                assertFalse(AnomalyEffectPolicy.canAdjustFog(FogMode.FOG_TERRAIN, medium));
                assertFalse(AnomalyEffectPolicy.canAdjustFog(FogMode.FOG_SKY, medium));
            }
        }
    }

    @Test
    void ambientParticlesAreSparseAndAccessibilityAware() {
        assertEquals(0, AnomalyEffectPolicy.particleCount(false, 1.0F, 12L));
        assertEquals(0, AnomalyEffectPolicy.particleCount(true, 0.0F, 12L));
        assertEquals(0, AnomalyEffectPolicy.particleCount(true, 1.0F, 11L));
        assertTrue(AnomalyEffectPolicy.particleCount(true, 1.0F, 12L) <= 2);
        assertEquals(1, AnomalyEffectPolicy.particleCount(true, 0.2F, 12L));
    }
}
