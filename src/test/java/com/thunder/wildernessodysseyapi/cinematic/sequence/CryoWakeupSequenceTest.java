package com.thunder.wildernessodysseyapi.cinematic.sequence;

import com.thunder.wildernessodysseyapi.cinematic.CinematicControlPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CryoWakeupSequenceTest {
    @Test
    void timelineMatchesTheRevivalAndRecoveryCueSheet() {
        assertEquals(0, CryoWakeupSequence.startTick(CryoWakeupSequence.BLACK_SCREEN));
        assertEquals(20, CryoWakeupSequence.startTick(CryoWakeupSequence.EXTERIOR_REVEAL));
        assertEquals(100, CryoWakeupSequence.startTick(CryoWakeupSequence.MEDICAL_DIAGNOSTIC));
        assertEquals(250, CryoWakeupSequence.startTick(CryoWakeupSequence.REVIVAL_PROTOCOL));
        assertEquals(470, CryoWakeupSequence.startTick(CryoWakeupSequence.CARDIAC_PACING));
        assertEquals(550, CryoWakeupSequence.startTick(CryoWakeupSequence.SUSPENSION_DRAIN));
        assertEquals(650, CryoWakeupSequence.startTick(CryoWakeupSequence.BLACKOUT_TRANSITION));
        assertEquals(670, CryoWakeupSequence.startTick(CryoWakeupSequence.EYES_REOPENING));
        assertEquals(750, CryoWakeupSequence.startTick(CryoWakeupSequence.MASK_RELEASE));
        assertEquals(790, CryoWakeupSequence.startTick(CryoWakeupSequence.CRYO_OPENING));
        assertEquals(850, CryoWakeupSequence.startTick(CryoWakeupSequence.BALANCE_CHECK));
        assertEquals(890, CryoWakeupSequence.startTick(CryoWakeupSequence.RECOVERY_WALK));
        assertEquals(1_290, CryoWakeupSequence.totalDurationTicks());
    }

    @Test
    void movementReturnsForTheFinalTwentySecondBriefing() {
        var stages = new CryoWakeupSequence().stages();
        for (int i = 0; i < stages.size() - 1; i++) {
            assertEquals(CinematicControlPolicy.LOCKED, stages.get(i).controlPolicy());
        }
        assertEquals(CinematicControlPolicy.PRESENTATION_ONLY, stages.getLast().controlPolicy());
        assertEquals(400, stages.getLast().durationTicks());
    }
}
