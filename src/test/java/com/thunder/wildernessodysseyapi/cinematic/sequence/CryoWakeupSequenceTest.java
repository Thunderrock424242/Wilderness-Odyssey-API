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
        assertEquals(136, CryoWakeupSequence.startTick(CryoWakeupSequence.MEDICAL_DIAGNOSTIC));
        assertEquals(406, CryoWakeupSequence.startTick(CryoWakeupSequence.REVIVAL_PROTOCOL));
        assertEquals(706, CryoWakeupSequence.startTick(CryoWakeupSequence.CARDIAC_PACING));
        assertEquals(874, CryoWakeupSequence.startTick(CryoWakeupSequence.SUSPENSION_DRAIN));
        assertEquals(1_076, CryoWakeupSequence.startTick(CryoWakeupSequence.BLACKOUT_TRANSITION));
        assertEquals(1_096, CryoWakeupSequence.startTick(CryoWakeupSequence.EYES_REOPENING));
        assertEquals(1_196, CryoWakeupSequence.startTick(CryoWakeupSequence.MASK_RELEASE));
        assertEquals(1_286, CryoWakeupSequence.startTick(CryoWakeupSequence.CRYO_OPENING));
        assertEquals(1_346, CryoWakeupSequence.startTick(CryoWakeupSequence.BALANCE_CHECK));
        assertEquals(1_426, CryoWakeupSequence.startTick(CryoWakeupSequence.RECOVERY_WALK));
        assertEquals(1_776, CryoWakeupSequence.totalDurationTicks());
    }

    @Test
    void movementReturnsForTheFinalGroundedBriefing() {
        var stages = new CryoWakeupSequence().stages();
        for (int i = 0; i < stages.size() - 1; i++) {
            assertEquals(CinematicControlPolicy.LOCKED, stages.get(i).controlPolicy());
        }
        assertEquals(CinematicControlPolicy.PRESENTATION_ONLY, stages.getLast().controlPolicy());
        assertEquals(350, stages.getLast().durationTicks());
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
        assertEquals(133, CryoWakeupSequence.narrationDurationTicks(
                CryoWakeupSequence.NARRATION_CONTAMINATION
        ));
        assertEquals(99, CryoWakeupSequence.narrationDurationTicks(
                CryoWakeupSequence.NARRATION_PACING
        ));
    }

    @Test
    void recoveryBriefingPreservesConversationalPausesAtTheLatestFallbackTicks() {
        int identityEnd = 30 + CryoWakeupSequence.narrationDurationTicks(
                CryoWakeupSequence.NARRATION_AETHER_IDENTITY
        );
        int limitsEnd = 130 + CryoWakeupSequence.narrationDurationTicks(
                CryoWakeupSequence.NARRATION_AETHER_LIMITS
        );
        int exitEnd = 245 + CryoWakeupSequence.narrationDurationTicks(
                CryoWakeupSequence.NARRATION_FIND_EXIT
        );

        assertTrue(identityEnd <= 125);
        assertTrue(limitsEnd <= 240);
        assertTrue(exitEnd <= 350);
    }
}
