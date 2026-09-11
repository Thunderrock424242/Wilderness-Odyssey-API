package com.thunder.wildernessodysseyapi.weather.client;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies that vertical-profile phases keep their authoritative presentation. */
class PrecipitationBlendTest {

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
