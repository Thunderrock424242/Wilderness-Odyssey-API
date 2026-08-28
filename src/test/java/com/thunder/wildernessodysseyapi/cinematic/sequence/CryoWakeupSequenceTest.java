package com.thunder.wildernessodysseyapi.cinematic.sequence;

import com.thunder.wildernessodysseyapi.cinematic.CinematicControlPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryoWakeupSequenceTest {
    @Test
    void timelineMatchesTheRevivalAndRecoveryCueSheet() {
        assertEquals(0, CryoWakeupSequence.startTick(CryoWakeupSequence.BLACK_SCREEN));
        assertEquals(20, CryoWakeupSequence.startTick(CryoWakeupSequence.EXTERIOR_REVEAL));
        assertEquals(190, CryoWakeupSequence.startTick(CryoWakeupSequence.MEDICAL_DIAGNOSTIC));
        assertEquals(560, CryoWakeupSequence.startTick(CryoWakeupSequence.REVIVAL_PROTOCOL));
        assertEquals(950, CryoWakeupSequence.startTick(CryoWakeupSequence.CARDIAC_PACING));
        assertEquals(1_130, CryoWakeupSequence.startTick(CryoWakeupSequence.SUSPENSION_DRAIN));
        assertEquals(1_350, CryoWakeupSequence.startTick(CryoWakeupSequence.BLACKOUT_TRANSITION));
        assertEquals(1_370, CryoWakeupSequence.startTick(CryoWakeupSequence.EYES_REOPENING));
        assertEquals(1_500, CryoWakeupSequence.startTick(CryoWakeupSequence.MASK_RELEASE));
        assertEquals(1_630, CryoWakeupSequence.startTick(CryoWakeupSequence.CRYO_OPENING));
        assertEquals(1_690, CryoWakeupSequence.startTick(CryoWakeupSequence.BALANCE_CHECK));
        assertEquals(1_810, CryoWakeupSequence.startTick(CryoWakeupSequence.RECOVERY_WALK));
        assertEquals(2_310, CryoWakeupSequence.totalDurationTicks());
    }

    @Test
    void movementReturnsForTheFinalTwentySecondBriefing() {
        var stages = new CryoWakeupSequence().stages();
        for (int i = 0; i < stages.size() - 1; i++) {
            assertEquals(CinematicControlPolicy.LOCKED, stages.get(i).controlPolicy());
        }
        assertEquals(CinematicControlPolicy.PRESENTATION_ONLY, stages.getLast().controlPolicy());
        assertEquals(500, stages.getLast().durationTicks());
    }

    @Test
    void measuredAuthoredClipsNeverOverlapInsideLockedStages() {
        for (var stage : new CryoWakeupSequence().stages()) {
            int previousEnd = 0;
            for (CryoWakeupSequence.NarrationCue cue : CryoWakeupSequence.narrationCues(stage.id())) {
                assertTrue(cue.tick() >= previousEnd, () -> "overlap before " + cue.id());
                previousEnd = cue.tick() + CryoWakeupSequence.narrationDurationTicks(cue.id());
            }
            assertTrue(previousEnd <= stage.durationTicks(), () -> "narration exceeds " + stage.id());
        }
        assertEquals(191, CryoWakeupSequence.narrationDurationTicks(
                CryoWakeupSequence.NARRATION_CONTAMINATION
        ));
        assertEquals(158, CryoWakeupSequence.narrationDurationTicks(
                CryoWakeupSequence.NARRATION_PACING
        ));
    }

    @Test
    void recoveryBriefingPreservesCaretakerPausesAtTheLatestFallbackTicks() {
        int identityEnd = 40 + CryoWakeupSequence.narrationDurationTicks(
                CryoWakeupSequence.NARRATION_AETHER_IDENTITY
        );
        int limitsEnd = 190 + CryoWakeupSequence.narrationDurationTicks(
                CryoWakeupSequence.NARRATION_AETHER_LIMITS
        );
        int exitEnd = 350 + CryoWakeupSequence.narrationDurationTicks(
                CryoWakeupSequence.NARRATION_FIND_EXIT
        );

        assertTrue(identityEnd <= 180);
        assertTrue(limitsEnd <= 345);
        assertEquals(500, exitEnd);
    }
}
