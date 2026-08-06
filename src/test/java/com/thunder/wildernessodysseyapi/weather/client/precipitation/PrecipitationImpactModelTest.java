package com.thunder.wildernessodysseyapi.weather.client.precipitation;

import com.thunder.wildernessodysseyapi.weather.client.precipitation.PrecipitationImpactModel.ImpactSurface;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrecipitationImpactModelTest {

    @Test
    void impactProbabilityRequiresRainDensityAndParticles() {
        assertEquals(0.0, PrecipitationImpactModel.spawnProbability(0.0, 1.0, 1.0), 1.0E-12);
        assertEquals(0.0, PrecipitationImpactModel.spawnProbability(1.0, 0.0, 1.0), 1.0E-12);
        assertEquals(0.0, PrecipitationImpactModel.spawnProbability(1.0, 1.0, 0.0), 1.0E-12);
        assertTrue(PrecipitationImpactModel.spawnProbability(0.8, 0.5, 1.0) > 0.0);
    }

    @Test
    void waterRingsGrowLargerAndLastLongerThanHardImpacts() {
        float water = PrecipitationImpactModel.radius(0.8F, ImpactSurface.WATER, 1.0F);
        float hard = PrecipitationImpactModel.radius(0.8F, ImpactSurface.HARD, 1.0F);

        assertTrue(water > hard);
        assertTrue(PrecipitationImpactModel.lifetimeTicks(ImpactSurface.WATER)
                > PrecipitationImpactModel.lifetimeTicks(ImpactSurface.HARD));
    }

    @Test
    void impactsFadeInsteadOfFlashingWhite() {
        float early = PrecipitationImpactModel.alpha(0.1F, ImpactSurface.WATER, 1.0F);
        float late = PrecipitationImpactModel.alpha(0.9F, ImpactSurface.WATER, 1.0F);

        assertTrue(early > late);
        assertTrue(early <= 0.34F);
    }
}
