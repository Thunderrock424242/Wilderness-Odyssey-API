package com.thunder.wildernessodysseyapi.quest.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Owner-thread pacing. Waiting players retain only IDs; at most eight complete buffers are allocated. */
final class QuestViewTransfers {
    record Projection(String hash, long publication, long revision, byte[] bytes) { }
    private record Transfer(Projection projection, UUID id, int index) { }
    private final UUID session;
    private final Function<UUID, Optional<Projection>> projection;
    private final BiConsumer<UUID, QuestViewPayload> sender;
    private final BiConsumer<UUID, Projection> completed;
    private final LinkedHashMap<UUID, Transfer> active = new LinkedHashMap<>();
    private final LinkedHashSet<UUID> waiting = new LinkedHashSet<>();
    private final HashSet<UUID> refresh = new HashSet<>();

    QuestViewTransfers(UUID session, Function<UUID, Optional<Projection>> projection,
                       BiConsumer<UUID, QuestViewPayload> sender, BiConsumer<UUID, Projection> completed) {
        this.session = session; this.projection = projection; this.sender = sender; this.completed = completed;
    }
    boolean request(UUID player) {
        if (active.containsKey(player)) { refresh.add(player); return true; }
        if (waiting.contains(player)) return true;
        if (waiting.size() >= 4096) return false;
        return waiting.add(player);
    }
    boolean pending(UUID player) { return active.containsKey(player) || waiting.contains(player); }
    int activeCount() { return active.size(); }

    void pump() {
        while (active.size() < 8 && !waiting.isEmpty()) {
            UUID player = waiting.iterator().next(); waiting.remove(player);
            var view = projection.apply(player);
            if (view.isEmpty()) continue;
            var value = view.get();
            if (value.bytes().length < 1 || value.bytes().length > QuestWire.MAX_VIEW_BYTES) throw new IllegalStateException("Quest view exceeds the transfer limit.");
            active.put(player, new Transfer(value, UUID.randomUUID(), 0));
        }
        int budget = 4;
        for (var entry : new ArrayList<>(active.entrySet())) {
            if (budget-- <= 0) break;
            var transfer = entry.getValue(); var value = transfer.projection();
            int chunks = (value.bytes().length + QuestWire.CHUNK_BYTES - 1) / QuestWire.CHUNK_BYTES;
            sender.accept(entry.getKey(), new QuestViewPayload(session, value.hash(), value.publication(), value.revision(), transfer.id(),
                    transfer.index(), chunks, value.bytes().length, Arrays.copyOfRange(value.bytes(), transfer.index() * QuestWire.CHUNK_BYTES,
                    Math.min(value.bytes().length, (transfer.index() + 1) * QuestWire.CHUNK_BYTES))));
            active.remove(entry.getKey());
            if (transfer.index() + 1 == chunks) {
                completed.accept(entry.getKey(), value);
                if (refresh.remove(entry.getKey())) request(entry.getKey());
            } else active.put(entry.getKey(), new Transfer(value, transfer.id(), transfer.index() + 1));
        }
    }
    void clear(UUID player) { waiting.remove(player); active.remove(player); refresh.remove(player); }
    void clear() { waiting.clear(); active.clear(); refresh.clear(); }
}
