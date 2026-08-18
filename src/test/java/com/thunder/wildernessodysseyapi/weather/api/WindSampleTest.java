package com.thunder.wildernessodysseyapi.weather.api;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the public wind value objects retain safe units and invariants. */
class WindSampleTest {

    @Test
    void sampleNormalizesDirectionAndExposesEffectiveVelocity() {
        WindSample sample = new WindSample(
                new Vec3(3.0, 0.0, 4.0),
                6.0F,
                2.0F,
                3.0F,
                0.5F,
                0.25F,
                7L,
                new AtmosphereCellKey(2, -3)
        );

        assertEquals(1.0D, sample.direction().length(), 1.0E-8D);
        assertEquals(8.0F, sample.effectiveSpeed());
        assertEquals(8.0D, sample.velocity().length(), 1.0E-6D);
        assertEquals(0.4D, sample.velocityPerTick().length(), 1.0E-6D);
    }

    @Test
    void settingsClampUnsafeNetworkAndConfigValues() {
        WindSettings settings = new WindSettings(
                true,
                Float.POSITIVE_INFINITY,
                -1.0F,
                100.0F,
                Float.NaN,
                12.0F
        );

        assertEquals(2.5F, settings.baseWindStrength());
        assertEquals(0.0F, settings.gustFrequency());
        assertEquals(12.0F, settings.gustStrength());
        assertEquals(1.8F, settings.stormWindMultiplier());
        assertEquals(12.0F, settings.maxWindSpeed());
    }

    @Test
    void calmSampleHasNoDirectionOrVelocity() {
        WindSample calm = WindSample.calm(new AtmosphereCellKey(-4, 5));

        assertEquals(Vec3.ZERO, calm.direction());
        assertEquals(Vec3.ZERO, calm.velocity());
        assertTrue(calm.effectiveSpeed() == 0.0F);
        assertEquals(new AtmosphereCellKey(-4, 5), calm.region());
    }
}
