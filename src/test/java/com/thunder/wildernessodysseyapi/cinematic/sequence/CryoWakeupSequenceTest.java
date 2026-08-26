package com.thunder.wildernessodysseyapi.cinematic.sequence;

import com.thunder.wildernessodysseyapi.cinematic.CinematicControlPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CryoWakeupSequenceTest {
    @Test
    void timelineMatchesTheTwentyFourSecondCueSheet() {
        assertEquals(0, CryoWakeupSequence.startTick(CryoWakeupSequence.BLACK_SCREEN));
        assertEquals(20, CryoWakeupSequence.startTick(CryoWakeupSequence.MACHINERY_HUM));
        assertEquals(40, CryoWakeupSequence.startTick(CryoWakeupSequence.HEARTBEAT));
        assertEquals(60, CryoWakeupSequence.startTick(CryoWakeupSequence.EYES_PARTIAL));
        assertEquals(100, CryoWakeupSequence.startTick(CryoWakeupSequence.EYES_CLOSED));
        assertEquals(120, CryoWakeupSequence.startTick(CryoWakeupSequence.EYES_REOPENING));
        assertEquals(140, CryoWakeupSequence.startTick(CryoWakeupSequence.LIGHTS_FLICKER));
        assertEquals(160, CryoWakeupSequence.startTick(CryoWakeupSequence.WARNING_STARTED));
        assertEquals(180, CryoWakeupSequence.startTick(CryoWakeupSequence.WARNING_LIGHTS));
        assertEquals(200, CryoWakeupSequence.startTick(CryoWakeupSequence.ALARM_BEEPS));
        assertEquals(240, CryoWakeupSequence.startTick(CryoWakeupSequence.RELEASE_STARTED));
        assertEquals(260, CryoWakeupSequence.startTick(CryoWakeupSequence.LOCKS_DISENGAGED));
        assertEquals(280, CryoWakeupSequence.startTick(CryoWakeupSequence.MIST_RELEASE));
        assertEquals(300, CryoWakeupSequence.startTick(CryoWakeupSequence.CRYO_OPENING));
        assertEquals(340, CryoWakeupSequence.startTick(CryoWakeupSequence.CAMERA_TURN));
        assertEquals(380, CryoWakeupSequence.startTick(CryoWakeupSequence.CRYO_OPEN));
        assertEquals(400, CryoWakeupSequence.startTick(CryoWakeupSequence.LIGHTS_STABLE));
        assertEquals(440, CryoWakeupSequence.startTick(CryoWakeupSequence.CAMERA_RELEASE));
        assertEquals(460, CryoWakeupSequence.startTick(CryoWakeupSequence.CONTROL_RETURN));
        assertEquals(480, CryoWakeupSequence.totalDurationTicks());
    }

    @Test
    void movementReturnsOneSecondBeforePresentationCompletes() {
        var stages = new CryoWakeupSequence().stages();
        assertEquals(CinematicControlPolicy.LOCKED, stages.get(stages.size() - 2).controlPolicy());
        assertEquals(CinematicControlPolicy.PRESENTATION_ONLY, stages.getLast().controlPolicy());
        assertEquals(20, stages.getLast().durationTicks());
    }
}
