package com.thunder.wildernessodysseyapi.vegetation.api;

import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VegetationParticipationTest {
    @Test
    void theBeforeRemainsInertEvenWhenVegetationUpdatesAreEnabled() {
        assertFalse(ReactiveVegetationServices.updatesEnabled(TemporalRiftDimensions.THE_BEFORE_KEY, true));
    }

    @Test
    void livingDimensionsStillRequireTheServerMasterToggle() {
        assertTrue(ReactiveVegetationServices.updatesEnabled(Level.OVERWORLD, true));
        assertTrue(ReactiveVegetationServices.updatesEnabled(TemporalRiftDimensions.THE_ECHO_KEY, true));
        assertFalse(ReactiveVegetationServices.updatesEnabled(Level.OVERWORLD, false));
        assertFalse(ReactiveVegetationServices.updatesEnabled(TemporalRiftDimensions.THE_ECHO_KEY, false));
    }
}
