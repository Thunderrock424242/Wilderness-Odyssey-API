package com.thunder.wildernessodysseyapi.quest.network;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class QuestWorkshopAdmissionTest {
    private final UUID server = UUID.randomUUID(), token = UUID.randomUUID();
    private final Object player = new Object();
    private QuestWorkshopPayload frame(UUID owner, UUID editor, QuestWorkshopPayload.Kind kind, byte[] bytes) {
        return new QuestWorkshopPayload(owner, editor, UUID.randomUUID(), kind, 0, 0, bytes.length, bytes);
    }
    @Test void delayedOldEditorServerAndPlayerFramesCannotRevokeTheCurrentSession() {
        var transfer = new QuestWorkshopTransfers(); var peer = UUID.randomUUID();
        assertTrue(transfer.queue(peer, server, token, UUID.randomUUID(), QuestWorkshopPayload.Kind.SNAPSHOT, 0, new byte[QuestWorkshopPayload.CHUNK + 1]));
        var admission = new AtomicInteger();
        for (var old : java.util.List.of(frame(server, UUID.randomUUID(), QuestWorkshopPayload.Kind.EDIT, new byte[0]),
                frame(UUID.randomUUID(), token, QuestWorkshopPayload.Kind.CLOSE, new byte[0]))) {
            assertEquals(QuestWorkshopAdmission.Decision.IGNORE, QuestWorkshopAdmission.check(old, server, token, player, player, false, () -> { admission.incrementAndGet(); return false; }));
        }
        assertEquals(QuestWorkshopAdmission.Decision.IGNORE, QuestWorkshopAdmission.check(frame(server, token, QuestWorkshopPayload.Kind.EDIT, new byte[0]), server, token, player, new Object(), false, () -> false));
        assertEquals(0, admission.get());
        var delivered = new java.util.ArrayList<QuestWorkshopPayload>(); transfer.pump((id, part) -> delivered.add(part));
        assertEquals(2, delivered.size()); assertTrue(delivered.stream().allMatch(part -> part.editor().equals(token)));
        assertEquals(QuestWorkshopAdmission.Decision.REVOKE, QuestWorkshopAdmission.check(frame(server, token, QuestWorkshopPayload.Kind.EDIT, new byte[0]), server, token, player, player, false, () -> true));
    }
    @Test void authenticatedEmptyCloseReleasesEvenWhenTheWorkAdmissionBucketIsExhausted() {
        var calls = new AtomicInteger(); java.util.function.BooleanSupplier exhausted = () -> { calls.incrementAndGet(); return false; };
        assertEquals(QuestWorkshopAdmission.Decision.BUSY, QuestWorkshopAdmission.check(frame(server, token, QuestWorkshopPayload.Kind.EDIT, new byte[0]), server, token, player, player, true, exhausted));
        assertEquals(QuestWorkshopAdmission.Decision.CLOSE, QuestWorkshopAdmission.check(frame(server, token, QuestWorkshopPayload.Kind.CLOSE, new byte[0]), server, token, player, player, true, exhausted));
        assertEquals(1, calls.get(), "Release must not consume work admission or be refused by its bucket.");
        assertEquals(QuestWorkshopAdmission.Decision.INVALID, QuestWorkshopAdmission.check(frame(server, token, QuestWorkshopPayload.Kind.CLOSE, new byte[]{1}), server, token, player, player, true, exhausted));
        assertEquals(1, calls.get());
    }
}
