package com.thunder.wildernessodysseyapi.cinematic.sequence;

import com.thunder.wildernessodysseyapi.ai.voice.VoiceEmotion;
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
    void authoredVoiceDeliveryEscalatesWithoutInventingNewModes() {
        assertEquals(VoiceEmotion.NORMAL, CryoWakeupClientPresentation.narrationEmotion(
                CryoWakeupSequence.NARRATION_MEDICAL_ONLINE
        ));
        assertEquals(VoiceEmotion.CONCERNED, CryoWakeupClientPresentation.narrationEmotion(
                CryoWakeupSequence.NARRATION_CONTAMINATION
        ));
        assertEquals(VoiceEmotion.URGENT, CryoWakeupClientPresentation.narrationEmotion(
                CryoWakeupSequence.NARRATION_PACING
        ));
        assertEquals(VoiceEmotion.DAMAGED, CryoWakeupClientPresentation.narrationEmotion(
                CryoWakeupSequence.NARRATION_AETHER_IDENTITY
        ));
        assertTrue(CryoWakeupClientPresentation.narrationRadioEffect(
                CryoWakeupSequence.NARRATION_AETHER_IDENTITY
        ) > CryoWakeupClientPresentation.narrationRadioEffect(
                CryoWakeupSequence.NARRATION_MEDICAL_ONLINE
        ));
    }
}
