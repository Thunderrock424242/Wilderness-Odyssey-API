package com.thunder.wildernessodysseyapi.performance.background;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies deduplication, maximum-size flushing, and maximum-delay flushing. */
class NetworkBatcherTest {

    @Test
    void combinesRecipientUpdatesAndReplacesDuplicateKeys() {
        NetworkBatcher batcher = new NetworkBatcher(new BackgroundMetrics());
        batcher.configure(new NetworkBatcher.Settings(true, 2, 5, 16));
        List<List<Integer>> sent = new ArrayList<>();
        NetworkBatcher.Channel<Integer> channel = batcher.registerChannel(
                "weather", "state", (recipient, updates) -> sent.add(updates));
        UUID player = UUID.randomUUID();

        batcher.queue(channel, player, "humidity", 1, 0L);
        batcher.queue(channel, player, "humidity", 2, 1L);
        assertEquals(0, batcher.flushDue(4L, Long.MAX_VALUE));
        batcher.queue(channel, player, "temperature", 3, 4L);

        assertEquals(1, batcher.flushDue(4L, Long.MAX_VALUE));
        assertEquals(List.of(List.of(2, 3)), sent);
        assertEquals(0, batcher.queuedUpdates());
    }

    @Test
    void flushesPartialBatchAtMaximumDelay() {
        NetworkBatcher batcher = new NetworkBatcher(new BackgroundMetrics());
        batcher.configure(new NetworkBatcher.Settings(true, 8, 5, 16));
        List<List<String>> sent = new ArrayList<>();
        NetworkBatcher.Channel<String> channel = batcher.registerChannel(
                "water", "sea_state", (recipient, updates) -> sent.add(updates));
        batcher.queue(channel, UUID.randomUUID(), "state", "calm", 10L);

        assertEquals(0, batcher.flushDue(14L, Long.MAX_VALUE));
        assertEquals(1, batcher.flushDue(15L, Long.MAX_VALUE));
        assertEquals(List.of(List.of("calm")), sent);
    }
}
