package com.thunder.wildernessodysseyapi.cinematic.sequence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryoWakeupPresentationModelTest {
    @Test
    void cameraModesSeparateExteriorRevealFromFirstPersonRecovery() {
        assertTrue(CryoWakeupPresentationModel.isExteriorStage(CryoWakeupSequence.EXTERIOR_REVEAL));
        assertTrue(CryoWakeupPresentationModel.isExteriorStage(CryoWakeupSequence.SUSPENSION_DRAIN));
        assertFalse(CryoWakeupPresentationModel.isExteriorStage(CryoWakeupSequence.EYES_REOPENING));

        assertTrue(CryoWakeupPresentationModel.isFirstPersonStage(CryoWakeupSequence.EYES_REOPENING));
        assertTrue(CryoWakeupPresentationModel.isFirstPersonStage(CryoWakeupSequence.BALANCE_CHECK));
        assertFalse(CryoWakeupPresentationModel.isFirstPersonStage(CryoWakeupSequence.RECOVERY_WALK));
    }

    @Test
    void eyelidsOpenGraduallyAfterTheBlackTransition() {
        assertEquals(0.0F, CryoWakeupPresentationModel.eyeOpenAmount(
                CryoWakeupSequence.BLACKOUT_TRANSITION, 0.5F
        ));
        assertTrue(CryoWakeupPresentationModel.eyeOpenAmount(
                CryoWakeupSequence.EYES_REOPENING, 0.40F
        ) > 0.20F);
        assertTrue(CryoWakeupPresentationModel.eyeOpenAmount(
                CryoWakeupSequence.EYES_REOPENING, 1.0F
        ) >= 0.80F);
        assertEquals(1.0F, CryoWakeupPresentationModel.eyeOpenAmount(
                CryoWakeupSequence.RECOVERY_WALK, 0.5F
        ));
    }

    @Test
    void contaminationWarningAndFluidWashClearAsTheTubeDrains() {
        assertEquals(0.0F, CryoWakeupPresentationModel.warningAlpha(
                CryoWakeupSequence.EXTERIOR_REVEAL, 0.5F, 100.0F
        ));
        assertTrue(CryoWakeupPresentationModel.warningAlpha(
                CryoWakeupSequence.CARDIAC_PACING, 0.5F, 100.0F
        ) > 0.0F);
        assertTrue(CryoWakeupPresentationModel.suspensionAlpha(
                CryoWakeupSequence.SUSPENSION_DRAIN, 0.0F
        ) > CryoWakeupPresentationModel.suspensionAlpha(
                CryoWakeupSequence.SUSPENSION_DRAIN, 1.0F
        ));
    }

    @Test
    void blurFadesDuringFirstPersonRecovery() {
        assertTrue(CryoWakeupPresentationModel.blurStrength(
                CryoWakeupSequence.EYES_REOPENING, 0.0F
        ) > CryoWakeupPresentationModel.blurStrength(
                CryoWakeupSequence.BALANCE_CHECK, 1.0F
        ));
        assertTrue(CryoWakeupPresentationModel.blurStrength(
                CryoWakeupSequence.RECOVERY_WALK, 0.0F
        ) > CryoWakeupPresentationModel.blurStrength(
                CryoWakeupSequence.RECOVERY_WALK, 1.0F
        ));
        assertEquals(0.0F, CryoWakeupPresentationModel.blurStrength(
                CryoWakeupSequence.RECOVERY_WALK, 1.0F
        ));
    }

    @Test
    void medicalTelemetryShowsAConsistentRecoveryTrend() {
        assertTrue(CryoWakeupPresentationModel.showsMedicalTelemetry(
                CryoWakeupSequence.MEDICAL_DIAGNOSTIC
        ));
        assertTrue(CryoWakeupPresentationModel.coreTemperatureCelsius(
                CryoWakeupSequence.SUSPENSION_DRAIN, 1.0F
        ) > CryoWakeupPresentationModel.coreTemperatureCelsius(
                CryoWakeupSequence.EXTERIOR_REVEAL, 0.0F
        ));
        assertTrue(CryoWakeupPresentationModel.heartRateBpm(
                CryoWakeupSequence.CARDIAC_PACING, 1.0F
        ) > CryoWakeupPresentationModel.heartRateBpm(
                CryoWakeupSequence.MEDICAL_DIAGNOSTIC, 0.0F
        ));
        assertTrue(CryoWakeupPresentationModel.oxygenSaturation(
                CryoWakeupSequence.SUSPENSION_DRAIN, 1.0F
        ) > CryoWakeupPresentationModel.oxygenSaturation(
                CryoWakeupSequence.MEDICAL_DIAGNOSTIC, 0.0F
        ));
        assertTrue(CryoWakeupPresentationModel.pacingFlash(
                CryoWakeupSequence.CARDIAC_PACING, 135.0F / 180.0F
        ) > 0.9F);
        assertEquals(0.0F, CryoWakeupPresentationModel.pacingFlash(
                CryoWakeupSequence.REVIVAL_PROTOCOL, 0.60F
        ));
    }
}
