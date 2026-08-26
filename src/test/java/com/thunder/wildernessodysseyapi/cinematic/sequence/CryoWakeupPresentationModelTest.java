package com.thunder.wildernessodysseyapi.cinematic.sequence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryoWakeupPresentationModelTest {
    @Test
    void eyelidsFollowCloseReopenAndFullyOpenPhases() {
        assertEquals(0.0F, CryoWakeupPresentationModel.eyeOpenAmount(
                CryoWakeupSequence.BLACK_SCREEN, 0.5F
        ));
        assertTrue(CryoWakeupPresentationModel.eyeOpenAmount(
                CryoWakeupSequence.EYES_PARTIAL, 0.55F
        ) > 0.20F);
        assertEquals(0.0F, CryoWakeupPresentationModel.eyeOpenAmount(
                CryoWakeupSequence.EYES_CLOSED, 0.5F
        ));
        assertTrue(CryoWakeupPresentationModel.eyeOpenAmount(
                CryoWakeupSequence.EYES_REOPENING, 1.0F
        ) >= 0.60F);
        assertEquals(1.0F, CryoWakeupPresentationModel.eyeOpenAmount(
                CryoWakeupSequence.LIGHTS_STABLE, 0.5F
        ));
    }

    @Test
    void warningWashIsAbsentBeforeTheFailureCue() {
        assertEquals(0.0F, CryoWakeupPresentationModel.warningAlpha(
                CryoWakeupSequence.BLACK_SCREEN, 0.5F, 100.0F
        ));
        assertTrue(CryoWakeupPresentationModel.warningAlpha(
                CryoWakeupSequence.WARNING_LIGHTS, 0.5F, 100.0F
        ) > 0.0F);
    }
}
