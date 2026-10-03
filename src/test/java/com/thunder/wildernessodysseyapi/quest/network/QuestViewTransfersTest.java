package com.thunder.wildernessodysseyapi.quest.network;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class QuestViewTransfersTest {
    @Test
    void ninthPlayerWaitsWithoutAllocatingAnExtraTransferAndEventuallyReceivesAView() {
        var received = new HashMap<UUID, List<QuestViewPayload>>(); var completed = new ArrayList<UUID>();
        var queue = new QuestViewTransfers(UUID.randomUUID(), player -> Optional.of(
                new QuestViewTransfers.Projection("a".repeat(64), 1, 0, new byte[QuestWire.CHUNK_BYTES * 2 + 10])),
                (player, payload) -> received.computeIfAbsent(player, ignored -> new ArrayList<>()).add(payload),
                (player, projection) -> completed.add(player));
        var players = new ArrayList<UUID>();
        for (int index = 0; index < 9; index++) { var player = UUID.randomUUID(); players.add(player); queue.request(player); }
        for (int tick = 0; tick < 30 && completed.size() < 9; tick++) {
            int before = received.values().stream().mapToInt(List::size).sum(); queue.pump();
            assertTrue(received.values().stream().mapToInt(List::size).sum() - before <= 4);
            assertTrue(queue.activeCount() <= 8);
        }
        assertEquals(9, completed.size());
        for (UUID player : players) assertEquals(List.of(0, 1, 2), received.get(player).stream().map(QuestViewPayload::index).toList());
    }

    @Test
    void disconnectRemovesBothWaitingAndBufferedWorkAndRefreshUsesTheLatestProjection() {
        var delivered = new ArrayList<QuestViewPayload>(); var revision = new long[]{1}; var player = UUID.randomUUID();
        var queue = new QuestViewTransfers(UUID.randomUUID(), ignored -> Optional.of(
                new QuestViewTransfers.Projection("a".repeat(64), 1, revision[0], new byte[40000])),
                (ignored, payload) -> delivered.add(payload), (ignored, projection) -> { });
        queue.request(player); queue.pump(); revision[0] = 2; queue.request(player);
        for (int tick = 0; tick < 5; tick++) queue.pump();
        assertEquals(2, delivered.getLast().playerRevision());
        queue.request(player); queue.clear(player); queue.pump();
        assertEquals(0, queue.activeCount()); assertFalse(queue.pending(player));
    }
}
