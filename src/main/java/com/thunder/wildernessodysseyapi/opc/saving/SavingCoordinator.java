package com.thunder.wildernessodysseyapi.opc.saving;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/**
 * Optional, loaded-only native save adapter. All world access stays on the
 * server thread; IO completion touches a detached attempt's volatile outcome.
 * No reference pins a chunk, no extra IO queue or serializer is introduced.
 * Partial tracking is diagnostic metadata, never the authoritative dirty set.
 */
public final class SavingCoordinator {
    private static final int MAXIMUM_PENDING_WRITES = 32;
    private static final ThreadLocal<SaveContext> ACTIVE_SAVE = new ThreadLocal<>();
    private static volatile Session current;
    private static String lastDescription = "disabled";
    private static Map<String, Long> lastMetrics = Map.of();

    private SavingCoordinator() { }

    public static void start(MinecraftServer server, boolean tracking, boolean drain, int capacity,
                             int dirtyAge, int quietAge, String reason) {
        current = tracking ? new Session(server, drain, capacity, dirtyAge, quietAge, reason) : null;
        lastDescription = reason;
        lastMetrics = Map.of();
    }

    /** Constant-time disabled gate precedes any level lookup in the dirty-mark hot path. */
    public static void dirtyStateChanged(ChunkAccess chunk, boolean dirty) {
        Session session = current;
        if (session == null || !(chunk instanceof LevelChunk) || !(chunk.getLevel() instanceof ServerLevel level)
                || level.getServer() != session.server || !session.server.isSameThread()) return;
        var key = key(level, chunk);
        if (dirty) session.index.markDirty(key, session.server.getTickCount());
        else session.index.markClean(key);
    }

    public static void loaded(ChunkAccess chunk) {
        if (current == null) return;
        if (chunk.isUnsaved()) dirtyStateChanged(chunk, true);
    }

    public static void unloaded(ChunkAccess chunk) {
        Session session = current;
        if (session == null || !(chunk.getLevel() instanceof ServerLevel level) || level.getServer() != session.server
                || !session.server.isSameThread()) return;
        var key = key(level, chunk);
        // An unfinished optional write cannot make the native unload save skip dirty state.
        if (session.pending.remove(key) != null) chunk.setUnsaved(true);
        session.index.forget(key);
    }

    public static void unloadLevel(ServerLevel level) {
        Session session = current;
        if (session == null || session.server != level.getServer() || !session.server.isSameThread()) return;
        String dimension = level.dimension().location().toString();
        for (Attempt attempt : session.pending.values()) {
            if (attempt.key.dimension().equals(dimension)) remark(session, attempt.key);
        }
        session.pending.keySet().removeIf(key -> key.dimension().equals(dimension));
        session.index.forgetDimension(dimension);
    }

    /** The original write future is returned untouched. This callback retains no world/chunk/server. */
    public static void observeWrite(Object owner, long position, CompletableFuture<Void> original) {
        SaveContext context = ACTIVE_SAVE.get();
        if (context == null || context.owner() != owner || context.attempt().key.position() != position) return;
        Attempt attempt = context.attempt();
        try {
            attempt.observed = true;
            original.whenComplete((ignored, failure) -> attempt.outcome = failure == null ? Outcome.SUCCESS : Outcome.FAILURE);
        } catch (RuntimeException unsupported) { attempt.outcome = Outcome.FAILURE; }
    }

    /** Bounded completions are applied before optional work, including when its allowance is zero. */
    public static void poll(MinecraftServer server) {
        Session session = owned(server);
        if (session == null) return;
        Iterator<Attempt> iterator = session.pending.values().iterator();
        while (iterator.hasNext()) {
            Attempt attempt = iterator.next();
            if (attempt.outcome == null) continue;
            iterator.remove();
            boolean failed = session.index.complete(attempt.key, attempt.generation, server.getTickCount(),
                    attempt.outcome == Outcome.SUCCESS, attempt.cost);
            if (failed) {
                remark(session, attempt.key);
                disable(session, "native write failed; drain disabled and dirty state restored");
            } else session.completed++;
        }
    }

    /** Returns actual non-preemptible server-thread cost; admission is time-based, never a chunk quota. */
    public static long drain(MinecraftServer server, long allowance, BooleanSupplier hasTime) {
        Session session = owned(server);
        if (session == null || !session.drain || allowance <= 0 || server.isCurrentlySaving()) return 0;
        long started = System.nanoTime();
        while (session.drain && hasTime.getAsBoolean() && session.pending.size() < MAXIMUM_PENDING_WRITES) {
            long remaining = Math.max(0, allowance - (System.nanoTime() - started));
            DirtyChunkIndex.Key key = session.index.candidate(server.getTickCount(), remaining);
            if (key == null) break;
            ServerLevel level = level(session, key);
            if (level == null || level.noSave) { session.index.forget(key); continue; }
            if (!(level.getChunkSource().chunkMap instanceof NativeChunkSaveAccess access)) {
                disable(session, "native save accessor unavailable; normal saving retained"); break;
            }
            var holder = access.wildernessodysseyapi$visibleChunk(key.position());
            if (holder == null) { session.index.forget(key); continue; }
            // A loaded full chunk can still have generation/promotion dependencies.
            // Preserve the same readiness barrier as native incremental/unload saves.
            if (holder.currentlyLoading != null || !holder.isReadyForSaving()) break;
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key.position()), ChunkPos.getZ(key.position()));
            if (chunk == null) { session.index.forget(key); continue; }
            if (!chunk.isUnsaved()) { session.index.markClean(key); continue; }
            if (!hasTime.getAsBoolean()) break;
            long generation = session.index.begin(key);
            if (generation == 0) break;
            Attempt attempt = new Attempt(key, generation);
            session.pending.put(key, attempt);
            ACTIVE_SAVE.set(new SaveContext(level.getChunkSource().chunkMap, attempt));
            long beforeSave = System.nanoTime();
            try {
                boolean submitted = access.wildernessodysseyapi$saveChunk(chunk);
                if (!submitted || !attempt.observed) {
                    attempt.outcome = Outcome.FAILURE;
                    chunk.setUnsaved(true);
                    disable(session, "native save/write observation unavailable or failed; normal saving retained");
                }
            } catch (RuntimeException | LinkageError failure) {
                attempt.outcome = Outcome.FAILURE;
                chunk.setUnsaved(true);
                disable(session, "optional native save failed (" + failure.getClass().getSimpleName() + "); normal saving retained");
            } finally {
                attempt.cost = Math.max(0, System.nanoTime() - beforeSave);
                ACTIVE_SAVE.remove();
            }
        }
        return Math.max(0, System.nanoTime() - started);
    }

    /** Stop before Minecraft's authoritative final save; all unfinished optional writes are re-marked. */
    public static void stopping(MinecraftServer server) {
        Session session = owned(server);
        if (session == null) return;
        session.drain = false;
        for (Attempt attempt : session.pending.values()) remark(session, attempt.key);
        session.reason = "stopping; pending writes re-marked for native final save";
        lastMetrics = metrics();
        lastDescription = describe();
    }

    public static void clear() { current = null; ACTIVE_SAVE.remove(); }

    public static Map<String, Long> metrics() {
        Session session = current;
        if (session == null) return lastMetrics;
        var snapshot = session.index.snapshot(session.server.getTickCount());
        return Map.of("saveTracked", (long) snapshot.tracked(), "saveObservedDirty", (long) snapshot.dirty(),
                "saveObservedHot", (long) snapshot.hot(), "saveEligibleCold", (long) snapshot.cold(),
                "savePendingWrites", (long) session.pending.size(), "saveOmittedDirtyMarks", snapshot.omitted(),
                "saveCompletedWrites", session.completed, "saveDrainEnabled", session.drain ? 1L : 0L);
    }

    public static String describe() {
        Session session = current;
        if (session == null) return lastDescription;
        var snapshot = session.index.snapshot(session.server.getTickCount());
        return snapshot.dirty() + " observed dirty, " + snapshot.cold() + " eligible cold, " + snapshot.hot()
                + " hot/young/pending; " + snapshot.omitted() + " omitted marks. Partial loaded-chunk coverage. " + session.reason;
    }

    private static void disable(Session session, String reason) {
        session.drain = false;
        if (!session.failed) ModConstants.LOGGER.warn("[WO Performance] Save coordinator: {}", reason);
        session.failed = true;
        session.reason = reason;
    }

    private static void remark(Session session, DirtyChunkIndex.Key key) {
        ServerLevel level = level(session, key);
        if (level == null) return;
        LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key.position()), ChunkPos.getZ(key.position()));
        if (chunk != null) chunk.setUnsaved(true);
    }

    private static ServerLevel level(Session session, DirtyChunkIndex.Key key) {
        return session.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(key.dimension())));
    }
    private static DirtyChunkIndex.Key key(ServerLevel level, ChunkAccess chunk) {
        return new DirtyChunkIndex.Key(level.dimension().location().toString(), chunk.getPos().toLong());
    }
    private static Session owned(MinecraftServer server) {
        Session session = current;
        return session != null && session.server == server && server.isSameThread() ? session : null;
    }
    private enum Outcome { SUCCESS, FAILURE }
    private record SaveContext(Object owner, Attempt attempt) { }
    private static final class Attempt {
        private final DirtyChunkIndex.Key key;
        private final long generation;
        private volatile Outcome outcome;
        private boolean observed;
        private long cost;
        private Attempt(DirtyChunkIndex.Key key, long generation) { this.key = key; this.generation = generation; }
    }
    private static final class Session {
        private final MinecraftServer server;
        private final DirtyChunkIndex index;
        private final Map<DirtyChunkIndex.Key, Attempt> pending = new HashMap<>();
        private boolean drain;
        private boolean failed;
        private String reason;
        private long completed;
        private Session(MinecraftServer server, boolean drain, int capacity, int dirtyAge, int quietAge, String reason) {
            this.server = server;
            this.drain = drain;
            this.reason = reason;
            index = new DirtyChunkIndex(capacity, dirtyAge, quietAge);
        }
    }
}
