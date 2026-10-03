package com.thunder.wildernessodysseyapi.quest.network;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.function.BiConsumer;

/** Bounded ordered assembly and fair outbound pacing, owned by one server or client thread. */
public final class QuestWorkshopTransfers {
    private static final int MAX_BUFFERED = 32 * 1024 * 1024;
    private static final class Incoming {
        final QuestWorkshopPayload first;
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int index; long tick;
        Incoming(QuestWorkshopPayload first, long tick) { this.first = first; this.tick = tick; }
    }
    private static final class Outgoing {
        final UUID server, editor, request;
        final QuestWorkshopPayload.Kind kind;
        final long revision;
        final byte[] bytes;
        int index;
        Outgoing(UUID server, UUID editor, UUID request, QuestWorkshopPayload.Kind kind, long revision, byte[] bytes) {
            this.server = server; this.editor = editor; this.request = request; this.kind = kind; this.revision = revision; this.bytes = bytes.clone();
        }
        QuestWorkshopPayload next() {
            int start = index * QuestWorkshopPayload.CHUNK;
            return new QuestWorkshopPayload(server, editor, request, kind, revision, index++, bytes.length,
                    Arrays.copyOfRange(bytes, start, Math.min(bytes.length, start + QuestWorkshopPayload.CHUNK)));
        }
        boolean done() { return index >= Math.max(1, (bytes.length + QuestWorkshopPayload.CHUNK - 1) / QuestWorkshopPayload.CHUNK); }
    }
    private final Map<UUID, Incoming> incoming = new HashMap<>();
    private final LinkedHashMap<UUID, Outgoing> outgoing = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, LinkedHashSet<UUID>> completed = new LinkedHashMap<>();
    private int incomingBytes, outgoingBytes;

    public Optional<byte[]> accept(UUID peer, QuestWorkshopPayload frame, long tick) {
        expire(tick);
        if (completed.getOrDefault(peer, new LinkedHashSet<>()).contains(frame.request())) throw new IllegalArgumentException("Replayed Workshop transfer.");
        if (frame.index() == 0 && frame.totalBytes() <= QuestWorkshopPayload.CHUNK) { remember(peer, frame.request()); return Optional.of(frame.bytes()); }
        if (frame.index() == 0) {
            removeIncoming(peer);
            if (incoming.size() >= 8) throw new IllegalArgumentException("Too many partial Workshop transfers.");
            incoming.put(peer, new Incoming(frame, tick));
        }
        var state = incoming.get(peer);
        if (state == null || frame.index() != state.index || !same(state.first, frame)) { removeIncoming(peer); throw new IllegalArgumentException("Out-of-order or mismatched Workshop transfer."); }
        if (incomingBytes + frame.bytes().length > MAX_BUFFERED) { removeIncoming(peer); throw new IllegalArgumentException("Workshop transfer budget exceeded."); }
        byte[] bytes = frame.bytes(); state.bytes.writeBytes(bytes); state.index++; state.tick = tick; incomingBytes += bytes.length;
        if (state.bytes.size() == frame.totalBytes()) {
            byte[] result = state.bytes.toByteArray(); removeIncoming(peer); remember(peer, frame.request()); return Optional.of(result);
        }
        return Optional.empty();
    }
    private static boolean same(QuestWorkshopPayload first, QuestWorkshopPayload next) {
        return first.server().equals(next.server()) && first.editor().equals(next.editor()) && first.request().equals(next.request())
                && first.kind() == next.kind() && first.revision() == next.revision() && first.totalBytes() == next.totalBytes();
    }
    private void remember(UUID peer, UUID request) {
        var history = completed.computeIfAbsent(peer, ignored -> new LinkedHashSet<>()); history.add(request);
        while (history.size() > 32) history.remove(history.iterator().next());
        while (completed.size() > 8) completed.remove(completed.keySet().iterator().next());
    }
    public boolean queue(UUID peer, UUID server, UUID editor, UUID request, QuestWorkshopPayload.Kind kind, long revision, byte[] bytes) {
        if (bytes.length > QuestWorkshopPayload.MAX_BYTES || revision < 0) return false;
        var old = outgoing.get(peer); int replaced = old == null ? 0 : old.bytes.length;
        if (old == null && outgoing.size() >= 8 || outgoingBytes - replaced + bytes.length > MAX_BUFFERED) return false;
        if (old != null) { outgoing.remove(peer); outgoingBytes -= replaced; }
        outgoing.put(peer, new Outgoing(server, editor, request, kind, revision, bytes)); outgoingBytes += bytes.length; return true;
    }
    public void pump(BiConsumer<UUID, QuestWorkshopPayload> sender) {
        for (int count = 0; count < 4 && !outgoing.isEmpty(); count++) {
            var first = outgoing.entrySet().iterator().next(); UUID peer = first.getKey(); var transfer = first.getValue(); outgoing.remove(peer);
            sender.accept(peer, transfer.next());
            if (transfer.done()) outgoingBytes -= transfer.bytes.length; else outgoing.put(peer, transfer);
        }
    }
    public void expire(long tick) {
        for (var peer : List.copyOf(incoming.keySet())) if (tick - incoming.get(peer).tick > 1200) removeIncoming(peer);
    }
    private void removeIncoming(UUID peer) { var removed = incoming.remove(peer); if (removed != null) incomingBytes -= removed.bytes.size(); }
    public void forget(UUID peer) {
        removeIncoming(peer); var removed = outgoing.remove(peer); if (removed != null) outgoingBytes -= removed.bytes.length; completed.remove(peer);
    }
    public void clear() { incoming.clear(); outgoing.clear(); completed.clear(); incomingBytes = 0; outgoingBytes = 0; }
}
