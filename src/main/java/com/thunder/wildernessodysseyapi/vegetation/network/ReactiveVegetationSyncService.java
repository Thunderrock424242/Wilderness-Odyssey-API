package com.thunder.wildernessodysseyapi.vegetation.network;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationClimateState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns explicit tracking-safe publication of persistent vegetation climate.
 *
 * <p>All methods run on the logical server thread. Chunks are never retained;
 * only a per-level sequence and aggregate diagnostics survive between sends.</p>
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class ReactiveVegetationSyncService {
    private static final long DIAGNOSTIC_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final Map<ServerLevel, AtomicLong> REVISIONS = new ConcurrentHashMap<>();
    private static final AtomicLong INITIAL_SNAPSHOTS = new AtomicLong();
    private static final AtomicLong CHANGED_SNAPSHOTS = new AtomicLong();
    private static final AtomicLong UNCHANGED_SKIPPED = new AtomicLong();
    private static final AtomicLong LAST_DIAGNOSTIC_NANOS = new AtomicLong();

    private ReactiveVegetationSyncService() {
    }

    /** Sends the current state only after NeoForge reports that vanilla sent the chunk. */
    @SubscribeEvent
    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        var existing = event.getChunk().getExistingData(ModAttachments.REACTIVE_VEGETATION);
        if (existing.isEmpty()) {
            // Absence already renders as the neutral climate on the client, so
            // do not create a login-time packet for every untouched chunk.
            return;
        }
        VegetationClimateState state = existing.get().snapshot();
        PacketDistributor.sendToPlayer(
                event.getPlayer(),
                payload(event.getLevel(), event.getChunk(), state)
        );
        INITIAL_SNAPSHOTS.incrementAndGet();
        logDiagnosticsIfDue();
    }

    /** Publishes one update only when the client-visible tint signature changed. */
    public static void publishIfChanged(
            ServerLevel level,
            LevelChunk chunk,
            VegetationClimateState previous,
            VegetationClimateState current
    ) {
        if (!shouldSynchronize(previous, current)) {
            UNCHANGED_SKIPPED.incrementAndGet();
            logDiagnosticsIfDue();
            return;
        }
        PacketDistributor.sendToPlayersTrackingChunk(level, chunk.getPos(), payload(level, chunk, current));
        CHANGED_SNAPSHOTS.incrementAndGet();
        logDiagnosticsIfDue();
    }

    static boolean shouldSynchronize(VegetationClimateState previous, VegetationClimateState current) {
        VegetationClimateState before = previous == null ? VegetationClimateState.DEFAULT : previous;
        VegetationClimateState after = current == null ? VegetationClimateState.DEFAULT : current;
        return before.visualSignature() != after.visualSignature();
    }

    /** Releases the sequence without retaining an unloading server level. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            REVISIONS.remove(level);
        }
    }

    /** Clears process-scoped publication state after the server stops. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        REVISIONS.clear();
    }

    private static ReactiveVegetationSyncPayload payload(
            ServerLevel level,
            LevelChunk chunk,
            VegetationClimateState state
    ) {
        return ReactiveVegetationSyncPayload.from(
                level.dimension().location(),
                chunk.getPos().x,
                chunk.getPos().z,
                nextRevision(level),
                state
        );
    }

    private static long nextRevision(ServerLevel level) {
        AtomicLong revision = REVISIONS.computeIfAbsent(
                level,
                ignored -> new AtomicLong(Math.max(0L, level.getGameTime()))
        );
        return revision.updateAndGet(previous -> Math.max(previous + 1L, level.getGameTime()));
    }

    private static void logDiagnosticsIfDue() {
        long nowNanos = System.nanoTime();
        long previous = LAST_DIAGNOSTIC_NANOS.get();
        if (nowNanos - previous < DIAGNOSTIC_INTERVAL_NANOS
                || !LAST_DIAGNOSTIC_NANOS.compareAndSet(previous, nowNanos)) {
            return;
        }
        ModConstants.LOGGER.debug(
                "[WO ChunkData] Vegetation sync totals: initial={}, visualChanges={}, unchangedSkipped={}.",
                INITIAL_SNAPSHOTS.get(),
                CHANGED_SNAPSHOTS.get(),
                UNCHANGED_SKIPPED.get()
        );
    }
}
