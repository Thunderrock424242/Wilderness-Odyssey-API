package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regresses the removed season permission gate: thermal structure owns precipitation phase. */
class PrecipitationPhaseModelTest {

    @Test
    void coldSnapInOrdinaryNonWinterBiomeProducesSnow() {
        AtmosphereEnvironment ordinaryAutumn = climate(12.0, 0.0, true);

        assertFalse(PrecipitationPhaseModel.supportsNaturalSnow(ordinaryAutumn));
        assertEquals(
                PrecipitationType.SNOW,
                PrecipitationPhaseModel.classify(0.8, -4.0, 0.95, ordinaryAutumn)
        );
    }

    @Test
    void freezingAirProducesSnowInColdBiomesOrTemperateWinter() {
        AtmosphereEnvironment coldBiome = climate(-3.0, 0.0, false);
        AtmosphereEnvironment temperateWinter = climate(12.0, 1.0, true);

        assertTrue(PrecipitationPhaseModel.supportsNaturalSnow(coldBiome));
        assertFalse(PrecipitationPhaseModel.supportsNaturalSnow(temperateWinter),
                "calendar winter alone does not supply a freezing environmental temperature");
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
