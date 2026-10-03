package com.thunder.wildernessodysseyapi.quest.network;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Classifies authenticated session traffic before any transfer buffers or operator slots change. */
final class QuestWorkshopAdmission {
    enum Decision { ACCEPT, IGNORE, REVOKE, CLOSE, BUSY, INVALID }
    private QuestWorkshopAdmission() { }
    static Decision check(QuestWorkshopPayload frame, UUID server, UUID token, Object incarnation,
                          Object sender, boolean permitted, BooleanSupplier admitWork) {
        if (!frame.server().equals(server) || !frame.editor().equals(token) || incarnation != sender) return Decision.IGNORE;
        if (!permitted) return Decision.REVOKE;
        // Release is an authenticated, empty control message, not queued authoring work. In particular,
        // accepted saves must never consume the capacity needed to release their operator's session.
        if (frame.kind() == QuestWorkshopPayload.Kind.CLOSE) return frame.totalBytes() == 0 ? Decision.CLOSE : Decision.INVALID;
        if (frame.kind() != QuestWorkshopPayload.Kind.EDIT && frame.kind() != QuestWorkshopPayload.Kind.SNAPSHOT
                && frame.kind() != QuestWorkshopPayload.Kind.VALIDATE) return Decision.IGNORE;
        return frame.index() == 0 && !admitWork.getAsBoolean() ? Decision.BUSY : Decision.ACCEPT;
    }
}
