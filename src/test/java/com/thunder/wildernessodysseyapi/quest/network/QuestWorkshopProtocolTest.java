package com.thunder.wildernessodysseyapi.quest.network;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class QuestWorkshopProtocolTest {
    private final UUID server = UUID.randomUUID(), session = UUID.randomUUID(), request = UUID.randomUUID(), peer = UUID.randomUUID();
    private QuestWorkshopPayload frame(int index, int total, byte[] data) {
        return new QuestWorkshopPayload(server, session, request, QuestWorkshopPayload.Kind.EDIT, 0, index, total, data);
    }
    @Test void assemblesOnlyCompleteOrderedTransfersAndRejectsReplays() {
        var transfers = new QuestWorkshopTransfers(); int total = 16 * 1024 + 3;
        assertTrue(transfers.accept(peer, frame(0, total, new byte[16 * 1024]), 0).isEmpty());
        assertEquals(total, transfers.accept(peer, frame(1, total, new byte[3]), 1).orElseThrow().length);
        assertThrows(IllegalArgumentException.class, () -> transfers.accept(peer, frame(1, total, new byte[3]), 2));
    }
    @Test void rejectsMetadataChangesOutOfOrderAndExcessiveAdvertisedTotals() {
        var transfers = new QuestWorkshopTransfers(); int total = 16 * 1024 + 3;
        transfers.accept(peer, frame(0, total, new byte[16 * 1024]), 0);
        assertThrows(IllegalArgumentException.class, () -> transfers.accept(peer, new QuestWorkshopPayload(server, session, UUID.randomUUID(), QuestWorkshopPayload.Kind.EDIT, 0, 1, total, new byte[3]), 1));
        assertThrows(IllegalArgumentException.class, () -> frame(0, 16 * 1024 * 1024 + 1, new byte[16 * 1024]));
        assertThrows(IllegalArgumentException.class, () -> frame(0, 3, new byte[4]));
    }
    @Test void expiresPartialUploadsAndDisconnectClearsBothDirections() {
        var transfers = new QuestWorkshopTransfers(); int total = 16 * 1024 + 3;
        transfers.accept(peer, frame(0, total, new byte[16 * 1024]), 0); transfers.expire(1201);
        assertThrows(IllegalArgumentException.class, () -> transfers.accept(peer, frame(1, total, new byte[3]), 1202));
        assertTrue(transfers.queue(peer, server, session, request, QuestWorkshopPayload.Kind.SNAPSHOT, 0, new byte[3]));
        transfers.forget(peer); var sent = new java.util.ArrayList<QuestWorkshopPayload>(); transfers.pump((id, payload) -> sent.add(payload)); assertTrue(sent.isEmpty());
    }
    @Test void outboundPacingDoesNotSendMoreThanFourChunksAndCapsSessions() {
        var transfers = new QuestWorkshopTransfers(); var ids = new java.util.ArrayList<UUID>();
        for (int index = 0; index < 8; index++) { var id = UUID.randomUUID(); ids.add(id); assertTrue(transfers.queue(id, server, session, UUID.randomUUID(), QuestWorkshopPayload.Kind.SNAPSHOT, 0, new byte[16 * 1024 + 1])); }
        assertFalse(transfers.queue(UUID.randomUUID(), server, session, request, QuestWorkshopPayload.Kind.SNAPSHOT, 0, new byte[1]));
        var sent = new java.util.ArrayList<UUID>(); transfers.pump((id, payload) -> sent.add(id)); assertEquals(4, sent.size()); assertEquals(4, sent.stream().distinct().count());
        while (sent.size() < 16) transfers.pump((id, payload) -> sent.add(id));
        assertTrue(sent.containsAll(ids));
    }
}
