package com.thunder.wildernessodysseyapi.ai.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies deterministic ordering and stale-session rejection without audio hardware. */
class VoicePlaybackQueueTest {
    @Test
    void authoredLinesRemainOrderedWithinOneGeneration() {
        VoicePlaybackQueue queue = new VoicePlaybackQueue();
        VoicePlaybackQueue.ScheduledLine first = queue.enqueue(line("first")).orElseThrow();
        VoicePlaybackQueue.ScheduledLine second = queue.enqueue(line("second")).orElseThrow();

        assertEquals(first, queue.poll().orElseThrow());
        assertEquals(second, queue.poll().orElseThrow());
        assertTrue(queue.isCurrent(first));
    }

    @Test
    void replacementCancelsQueuedAndAlreadyRunningTokens() {
        VoicePlaybackQueue queue = new VoicePlaybackQueue();
        VoicePlaybackQueue.ScheduledLine stale = queue.enqueue(line("stale")).orElseThrow();
        queue.enqueue(line("also stale"));

        VoicePlaybackQueue.ScheduledLine newest = queue.replace(line("newest"));

        assertFalse(queue.isCurrent(stale));
        assertTrue(queue.isCurrent(newest));
        assertEquals("newest", queue.poll().orElseThrow().line().speechText());
        assertTrue(queue.poll().isEmpty());
    }

    @Test
    void authoredQueueCannotGrowWithoutBound() {
        VoicePlaybackQueue queue = new VoicePlaybackQueue();
        for (int index = 0; index < 12; index++) {
            assertTrue(queue.enqueue(line("line " + index)).isPresent());
        }

        assertTrue(queue.enqueue(line("overflow")).isEmpty());
        assertEquals(12, queue.pendingCount());
    }

    private static VoiceLine line(String text) {
        return VoiceLine.authored("Aether", text, text, VoiceEmotion.NORMAL, 0.0F);
    }
}
