package com.thunder.wildernessodysseyapi.ecosystem.api;

import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EcosystemParticipationTest {
    @Test
    void theBeforeRemainsInertWhenServerEcosystemIsEnabled() {
        assertFalse(EcosystemParticipation.isEnabled(TemporalRiftDimensions.THE_BEFORE_KEY, true));
    }

    @Test
    void livingDimensionsStillRequireServerEnablement() {
        assertTrue(EcosystemParticipation.isEnabled(Level.OVERWORLD, true));
        assertTrue(EcosystemParticipation.isEnabled(TemporalRiftDimensions.THE_ECHO_KEY, true));
        assertFalse(EcosystemParticipation.isEnabled(Level.OVERWORLD, false));
        assertFalse(EcosystemParticipation.isEnabled(TemporalRiftDimensions.THE_ECHO_KEY, false));
    }
}
