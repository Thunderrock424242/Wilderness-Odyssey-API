package com.thunder.wildernessodysseyapi.environment.api;

import com.thunder.wildernessodysseyapi.anomaly.registry.AnomalyDimensions;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies dimension identity before runtime configuration is layered onto it. */
class EnvironmentDimensionProfileTest {

    @Test
    void beforeRemainsEnvironmentallyInert() {
        EnvironmentDimensionProfile profile = EnvironmentDimensionProfile.forDimension(
                TemporalRiftDimensions.THE_BEFORE_KEY);

        assertFalse(profile.atmosphere());
        assertFalse(profile.dynamicWater());
        assertFalse(profile.ecosystem());
        assertFalse(profile.reactiveVegetation());
        assertFalse(profile.naturalMeteors());
        assertFalse(profile.radiation());
        assertFalse(profile.riftfall());
        assertFalse(profile.participates());
    }

    @Test
    void overworldOwnsOrdinaryNaturalWorldFeatures() {
        EnvironmentDimensionProfile profile = EnvironmentDimensionProfile.forDimension(Level.OVERWORLD);

        assertTrue(profile.atmosphere());
        assertTrue(profile.dynamicWater());
        assertTrue(profile.ecosystem());
        assertTrue(profile.reactiveVegetation());
        assertTrue(profile.naturalMeteors());
        assertTrue(profile.radiation());
        assertFalse(profile.riftfall());
        assertTrue(profile.participates());
    }

    @Test
    void echoAllowsRiftfallButNotNaturalMeteorScheduling() {
        EnvironmentDimensionProfile profile = EnvironmentDimensionProfile.forDimension(
                TemporalRiftDimensions.THE_ECHO_KEY);

        assertTrue(profile.atmosphere());
        assertTrue(profile.dynamicWater());
        assertTrue(profile.ecosystem());
        assertTrue(profile.reactiveVegetation());
        assertFalse(profile.naturalMeteors());
        assertTrue(profile.radiation());
        assertTrue(profile.riftfall());
    }

    @Test
    void anomalyDimensionParticipatesAsLivingWorldWithoutRiftfall() {
        EnvironmentDimensionProfile profile = EnvironmentDimensionProfile.forDimension(
                AnomalyDimensions.ANOMALY_DIMENSION_KEY);

        assertTrue(profile.atmosphere());
        assertTrue(profile.dynamicWater());
        assertTrue(profile.ecosystem());
        assertTrue(profile.reactiveVegetation());
        assertFalse(profile.naturalMeteors());
        assertTrue(profile.radiation());
        assertFalse(profile.riftfall());
    }
}
