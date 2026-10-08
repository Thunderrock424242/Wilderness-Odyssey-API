package com.thunder.wildernessodysseyapi.weather.client;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies that vertical-profile phases keep their authoritative presentation. */
class PrecipitationBlendTest {

    @Test
    void packetTransitionBlendsCanonicalPhasesWithTheirIntensities() {
        PrecipitationBlend rain = new PrecipitationBlend(1, 0, 0);
        PrecipitationBlend snow = new PrecipitationBlend(0, 1, 0);
        PrecipitationBlend blend = PrecipitationBlend.temporal(rain, 0.2, snow, 0.8, 0.5);
        assertEquals(0.2F, blend.rain(), 1.0E-6F);
        assertEquals(0.8F, blend.snow(), 1.0E-6F);
        assertEquals(snow, PrecipitationBlend.temporal(rain, 0.2, snow, 0.8, 1.35));
        assertEquals(rain, PrecipitationBlend.temporal(PrecipitationBlend.NONE, 0, rain, 1, 0.5));
    }

    @Test
    void rainDoesNotBecomeSnowFromSurfaceTemperature() {
        assertEquals(new PrecipitationBlend(1.0F, 0.0F, 0.0F),
                PrecipitationBlend.fromPhase(PrecipitationType.RAIN, -8.0, 0.3, 0.1));
    }

    @Test
    void snowSurvivesAThinWarmSurfaceLayer() {
        assertEquals(new PrecipitationBlend(0.0F, 1.0F, 0.0F),
                PrecipitationBlend.fromPhase(PrecipitationType.SNOW, 8.0, 0.3, 0.1));
    }

    @Test
    void hailIsNotReclassifiedBySurfaceAirOrInstability() {
        assertEquals(new PrecipitationBlend(0.0F, 0.0F, 1.0F),
                PrecipitationBlend.fromPhase(PrecipitationType.HAIL, 18.0, 0.0, 0.0));
    }

    @Test
    void freezingRainRemainsLiquidEvenWithSubzeroSurfaceAir() {
        assertEquals(new PrecipitationBlend(1.0F, 0.0F, 0.0F),
                PrecipitationBlend.fromPhase(PrecipitationType.FREEZING_RAIN, -8.0, 0.3, 0.1));
    }

    @Test
    void sleetUsesPelletsWithoutRequiringSevereHailInstability() {
        assertEquals(new PrecipitationBlend(0.0F, 0.0F, 1.0F),
                PrecipitationBlend.fromPhase(PrecipitationType.SLEET, -5.0, 0.1, 0.0));
    }
}
