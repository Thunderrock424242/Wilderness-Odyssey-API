package com.thunder.wildernessodysseyapi.riftfall;

import com.thunder.wildernessodysseyapi.anomaly.registry.AnomalyDimensions;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiftfallDimensionRulesTest {
    @Test
    void onlyEchoDimensionAllowsRiftfallWeather() {
        assertTrue(RiftfallDimensionRules.isEligible(TemporalRiftDimensions.THE_ECHO_KEY));
        assertFalse(RiftfallDimensionRules.isEligible(Level.OVERWORLD));
        assertFalse(RiftfallDimensionRules.isEligible(Level.NETHER));
        assertFalse(RiftfallDimensionRules.isEligible(Level.END));
        assertFalse(RiftfallDimensionRules.isEligible(TemporalRiftDimensions.THE_BEFORE_KEY));
        assertFalse(RiftfallDimensionRules.isEligible(AnomalyDimensions.ANOMALY_DIMENSION_KEY));
    }

    @Test
    void purpleStormVisualsRequireEchoPrecipitationAndThunder() {
        assertTrue(RiftfallDimensionRules.permitsStormVisuals(
                TemporalRiftDimensions.THE_ECHO_KEY,
                true,
                true
        ));
        assertFalse(RiftfallDimensionRules.permitsStormVisuals(Level.OVERWORLD, true, true));
        assertFalse(RiftfallDimensionRules.permitsStormVisuals(
                TemporalRiftDimensions.THE_ECHO_KEY,
                false,
                true
        ));
        assertFalse(RiftfallDimensionRules.permitsStormVisuals(
                TemporalRiftDimensions.THE_ECHO_KEY,
                true,
                false
        ));
    }
}
