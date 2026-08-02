package com.thunder.wildernessodysseyapi.watersystem.water.network;

import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reassembles paged canonical snapshots and waits for their client chunks.
 *
 * <p>Server chunk tracking, payload delivery, and client chunk installation
 * are separate streams. The newest revision per chunk is retained until every
 * page has arrived and the destination chunk is available.</p>
 */
public final class ClientWaterVolumeSnapshots {

    private static final int MAX_PENDING_CHUNKS = 128;
    private static final int MAX_SCANS_PER_TICK = 128;
    private static final int MAX_APPLIES_PER_TICK = 32;
    private static final int MAX_PENDING_DELTAS_PER_CHUNK = 128;
    private static final Map<Level, LinkedHashMap<Long, PendingSnapshot>> PENDING =
            new IdentityHashMap<>();
    private static final Map<Level, LinkedHashMap<Long, PendingDeltas>> PENDING_DELTAS =
            new IdentityHashMap<>();

    private ClientWaterVolumeSnapshots() {
    }

    /** Accepts one page and applies the complete newest revision when possible. */
    public static void accept(Level level, WaterVolumeChunkPayload payload) {
        LinkedHashMap<Long, PendingSnapshot> pending = PENDING.computeIfAbsent(
                level,
                ignored -> new LinkedHashMap<>()
        );
        long chunkKey = payload.chunkKey();
        PendingSnapshot snapshot = pending.get(chunkKey);
        if (snapshot == null || payload.revision() > snapshot.revision()) {
            snapshot = new PendingSnapshot(payload);
            pending.put(chunkKey, snapshot);
        } else if (payload.revision() < snapshot.revision()) {
            return;
        }

        boolean complete = snapshot.accept(payload);

        // A player only tracks 81 chunks here; the larger bound leaves room
        // for dimension/chunk packet races without permitting unbounded memory.
        while (pending.size() > MAX_PENDING_CHUNKS) {
            Iterator<Long> iterator = pending.keySet().iterator();
            iterator.next();
            iterator.remove();
        }

        if (complete && pending.get(chunkKey) == snapshot && snapshot.applyIfLoaded(level)) {
            pending.remove(chunkKey);
            applyPendingDeltas(level, chunkKey);
        }
        if (pending.isEmpty()) {
            PENDING.remove(level);
        }
    }

    /** Applies a revision delta or retains it until its exact baseline is published. */
    public static void accept(Level level, WaterVolumeDeltaPayload payload) {
        ClientWaterSnapshotStore.DeltaApplyResult result =
                ClientWaterSnapshotStore.publishSparseDelta(level, payload);
        if (result == ClientWaterSnapshotStore.DeltaApplyResult.APPLIED
                || result == ClientWaterSnapshotStore.DeltaApplyResult.STALE) {
            return;
        }

        LinkedHashMap<Long, PendingDeltas> pending = PENDING_DELTAS.computeIfAbsent(
                level,
                ignored -> new LinkedHashMap<>()
        );
        PendingDeltas deltas = pending.computeIfAbsent(payload.chunkKey(), ignored -> new PendingDeltas());
        deltas.accept(payload);
        while (pending.size() > MAX_PENDING_CHUNKS) {
            Iterator<Long> iterator = pending.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    /** Applies complete snapshots after client chunk ticking. */
    public static void tick(Level level) {
        LinkedHashMap<Long, PendingSnapshot> pending = PENDING.get(level);
        if (pending != null) {
            int scanned = 0;
            int applied = 0;
            Iterator<Map.Entry<Long, PendingSnapshot>> iterator = pending.entrySet().iterator();
            while (iterator.hasNext() && scanned++ < MAX_SCANS_PER_TICK && applied < MAX_APPLIES_PER_TICK) {
                Map.Entry<Long, PendingSnapshot> entry = iterator.next();
                if (entry.getValue().applyIfLoaded(level)) {
                    long chunkKey = entry.getKey();
                    iterator.remove();
                    applyPendingDeltas(level, chunkKey);
                    applied++;
                }
            }
            if (pending.isEmpty()) {
                PENDING.remove(level);
            }
        }
        tickPendingDeltas(level);
    }

    /** Releases queued pages when the client leaves a level. */
    public static void clear(Level level) {
        PENDING.remove(level);
        PENDING_DELTAS.remove(level);
    }

    private static void tickPendingDeltas(Level level) {
        LinkedHashMap<Long, PendingDeltas> pending = PENDING_DELTAS.get(level);
        if (pending == null) {
            return;
        }
        int scanned = 0;
        Iterator<Map.Entry<Long, PendingDeltas>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext() && scanned++ < MAX_SCANS_PER_TICK) {
            Map.Entry<Long, PendingDeltas> entry = iterator.next();
            entry.getValue().apply(level);
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
        if (pending.isEmpty()) {
            PENDING_DELTAS.remove(level);
        }
    }

    private static void applyPendingDeltas(Level level, long chunkKey) {
        LinkedHashMap<Long, PendingDeltas> pending = PENDING_DELTAS.get(level);
        if (pending == null) {
            return;
        }
        PendingDeltas deltas = pending.get(chunkKey);
        if (deltas == null) {
            return;
        }
        deltas.apply(level);
        if (deltas.isEmpty()) {
            pending.remove(chunkKey);
        }
        if (pending.isEmpty()) {
            PENDING_DELTAS.remove(level);
        }
    }

    /** Owns one revision while its pages arrive in any order. */
    private static final class PendingSnapshot {
        private final int chunkX;
        private final int chunkZ;
        private final long revision;
        private final int[][] pages;
        private int receivedPages;
        private int[] assembledData;

        private PendingSnapshot(WaterVolumeChunkPayload firstPage) {
            chunkX = firstPage.chunkX();
            chunkZ = firstPage.chunkZ();
            revision = firstPage.revision();
            pages = new int[firstPage.pageCount()][];
        }

        private long revision() {
            return revision;
        }

        private boolean accept(WaterVolumeChunkPayload page) {
            if (page.pageCount() != pages.length || page.pageIndex() >= pages.length) {
                return false;
            }
            if (pages[page.pageIndex()] == null) {
                pages[page.pageIndex()] = page.cellData();
                receivedPages++;
            }
            return receivedPages == pages.length;
        }

        private boolean applyIfLoaded(Level level) {
            if (receivedPages != pages.length) {
                return false;
            }
            if (assembledData == null) {
                int totalLength = 0;
                for (int[] page : pages) {
                    totalLength = Math.addExact(totalLength, page.length);
                }
                assembledData = new int[totalLength];
                int offset = 0;
                for (int[] page : pages) {
                    System.arraycopy(page, 0, assembledData, offset, page.length);
                    offset += page.length;
                }
            }
            return WaterVolumeChunkPayload.applySnapshotIfLoaded(
                    level,
                    chunkX,
                    chunkZ,
                    revision,
                    assembledData
            );
        }
    }

    /** Keeps reliable ordered deltas bounded while a chunk baseline is paged or unloaded. */
    private static final class PendingDeltas {
        private final ArrayDeque<WaterVolumeDeltaPayload> deltas = new ArrayDeque<>();

        private void accept(WaterVolumeDeltaPayload payload) {
            WaterVolumeDeltaPayload last = deltas.peekLast();
            if (last != null
                    && last.fromRevision() == payload.fromRevision()
                    && last.toRevision() == payload.toRevision()) {
                return;
            }
            if (deltas.size() >= MAX_PENDING_DELTAS_PER_CHUNK) {
                deltas.removeFirst();
            }
            deltas.addLast(payload);
        }

        private void apply(Level level) {
            while (!deltas.isEmpty()) {
                ClientWaterSnapshotStore.DeltaApplyResult result =
                        ClientWaterSnapshotStore.publishSparseDelta(level, deltas.peekFirst());
                if (result == ClientWaterSnapshotStore.DeltaApplyResult.APPLIED
                        || result == ClientWaterSnapshotStore.DeltaApplyResult.STALE) {
                    deltas.removeFirst();
                    continue;
                }
                return;
            }
        }

        private boolean isEmpty() {
            return deltas.isEmpty();
        }
    }
}
