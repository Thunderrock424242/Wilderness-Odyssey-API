package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies natural snow needs both freezing air and a valid biome or winter climate. */
class PrecipitationPhaseModelTest {

    @Test
    void coldSnapInOrdinaryNonWinterBiomeRemainsRain() {
        AtmosphereEnvironment ordinaryAutumn = climate(12.0, 0.0, true);

        assertFalse(PrecipitationPhaseModel.supportsNaturalSnow(ordinaryAutumn));
        assertEquals(
                PrecipitationType.RAIN,
                PrecipitationPhaseModel.classify(0.8, -4.0, 0.95, ordinaryAutumn)
        );
    }

    @Test
    void freezingAirProducesSnowInColdBiomesOrTemperateWinter() {
        AtmosphereEnvironment coldBiome = climate(-3.0, 0.0, false);
        AtmosphereEnvironment temperateWinter = climate(12.0, 1.0, true);

        assertTrue(PrecipitationPhaseModel.supportsNaturalSnow(coldBiome));
        assertTrue(PrecipitationPhaseModel.supportsNaturalSnow(temperateWinter));
        assertEquals(
                PrecipitationType.SNOW,
                PrecipitationPhaseModel.classify(0.8, -4.0, 0.95, coldBiome)
        );
        assertEquals(
                PrecipitationType.SNOW,
                PrecipitationPhaseModel.classify(0.8, -4.0, 0.95, temperateWinter)
        );
    }

    @Test
    void warmWinterPrecipitationStillFallsAsRain() {
        AtmosphereEnvironment temperateWinter = climate(12.0, 1.0, true);

        assertEquals(
                PrecipitationType.RAIN,
                PrecipitationPhaseModel.classify(0.8, 8.0, 0.95, temperateWinter)
        );
    }

    private static AtmosphereEnvironment climate(
            double biomeTemperature,
            double snowSeasonFactor,
            boolean calendarAvailable
    ) {
        return new AtmosphereEnvironment(
                biomeTemperature,
                0.8,
                64.0,
                0.0,
                0.5,
                0.0,
                0.0,
                0.0,
                0.0,
                1.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                snowSeasonFactor,
                calendarAvailable
        );
    }
}
