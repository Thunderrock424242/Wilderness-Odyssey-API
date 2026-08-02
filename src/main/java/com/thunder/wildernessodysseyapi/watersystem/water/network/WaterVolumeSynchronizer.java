package com.thunder.wildernessodysseyapi.watersystem.water.network;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Publishes sparse canonical water with bounded baselines and revision deltas.
 *
 * <p>A player first receives the existing paged full snapshot. Later writes use
 * contiguous upsert/tombstone deltas from the chunk's bounded journal. Every
 * pass has both payload and changed-cell budgets, preventing one dense chunk or
 * player from monopolizing the server network tick.</p>
 */
public final class WaterVolumeSynchronizer {

    private static final int TRACKING_RADIUS_CHUNKS = 4;
    private static final int MAX_PAYLOADS_PER_PLAYER_PER_PASS = 8;
    private static final int MAX_CELLS_PER_PLAYER_PER_PASS = 4_096;
    private static final int MAX_DELTA_CHANGES_PER_PAYLOAD = 512;
    private static final Map<ServerPlayer, PlayerSyncState> PLAYER_REVISIONS = new WeakHashMap<>();

    private WaterVolumeSynchronizer() {
    }

    /** Sends loaded canonical chunks within the bounded tracking radius. */
    public static void syncLevel(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            PlayerSyncState syncState = PLAYER_REVISIONS.computeIfAbsent(
                    player,
                    ignored -> new PlayerSyncState(level.dimension())
            );
            if (!syncState.dimension.equals(level.dimension())) {
                syncState = new PlayerSyncState(level.dimension());
                PLAYER_REVISIONS.put(player, syncState);
            }

            List<TrackedChunk> trackedChunks = collectTrackedChunks(level, player);
            Set<Long> retainedChunks = new HashSet<>(trackedChunks.size());
            for (TrackedChunk tracked : trackedChunks) {
                retainedChunks.add(tracked.chunkKey());
            }
            syncState.retainLoadedChunks(retainedChunks);

            SyncBudget budget = new SyncBudget();
            if (syncState.baseline != null && !sendPendingBaseline(player, syncState, budget)) {
                continue;
            }

            for (TrackedChunk tracked : trackedChunks) {
                if (!budget.canSendAnything()) {
                    break;
                }
                var existingVolume = tracked.chunk().getExistingData(ModAttachments.WATER_VOLUME);
                if (existingVolume.isEmpty()) {
                    continue;
                }
                WaterVolumeChunk volume = existingVolume.get();
                long knownRevision = syncState.revisions.getOrDefault(tracked.chunkKey(), Long.MIN_VALUE);
                if (knownRevision == volume.revision()) {
                    continue;
                }

                if (knownRevision == Long.MIN_VALUE) {
                    if (!startAndSendBaseline(player, syncState, tracked, volume, budget)) {
                        break;
                    }
                    continue;
                }

                int deltaLimit = Math.min(MAX_DELTA_CHANGES_PER_PAYLOAD, budget.remainingCells());
                if (deltaLimit <= 0) {
                    break;
                }
                WaterVolumeChunk.DeltaSnapshot delta = volume.deltaSince(knownRevision, deltaLimit);
                if (!delta.available()) {
                    if (!startAndSendBaseline(player, syncState, tracked, volume, budget)) {
                        break;
                    }
                    continue;
                }
                if (delta.changeCount() <= 0) {
                    syncState.revisions.put(tracked.chunkKey(), delta.toRevision());
                    continue;
                }
                if (!budget.tryConsume(delta.changeCount())) {
                    break;
                }
                PacketDistributor.sendToPlayer(player, WaterVolumeDeltaPayload.from(
                        tracked.chunk().getPos().x,
                        tracked.chunk().getPos().z,
                        delta
                ));
                syncState.revisions.put(tracked.chunkKey(), delta.toRevision());
            }
        }
    }

    /** Forgets all state for a player after logout or an explicit reset. */
    public static void forgetPlayer(ServerPlayer player) {
        PLAYER_REVISIONS.remove(player);
    }

    /**
     * Forgets a chunk revision after Minecraft stops tracking it for a player.
     *
     * <p>The next watch creates a new client chunk attachment, so an unchanged
     * server revision must be sent again instead of being mistaken for state the
     * new client chunk already owns.</p>
     */
    public static void forgetChunk(ServerPlayer player, ChunkPos pos) {
        PlayerSyncState syncState = PLAYER_REVISIONS.get(player);
        if (syncState != null) {
            syncState.forget(pos.toLong());
        }
    }

    private static List<TrackedChunk> collectTrackedChunks(ServerLevel level, ServerPlayer player) {
        int centerChunkX = player.chunkPosition().x;
        int centerChunkZ = player.chunkPosition().z;
        List<TrackedChunk> tracked = new ArrayList<>((TRACKING_RADIUS_CHUNKS * 2 + 1)
                * (TRACKING_RADIUS_CHUNKS * 2 + 1));
        for (int offsetX = -TRACKING_RADIUS_CHUNKS; offsetX <= TRACKING_RADIUS_CHUNKS; offsetX++) {
            for (int offsetZ = -TRACKING_RADIUS_CHUNKS; offsetZ <= TRACKING_RADIUS_CHUNKS; offsetZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(
                        centerChunkX + offsetX,
                        centerChunkZ + offsetZ
                );
                if (chunk != null) {
                    tracked.add(new TrackedChunk(chunk.getPos().toLong(), chunk));
                }
            }
        }
        return tracked;
    }

    private static boolean startAndSendBaseline(
            ServerPlayer player,
            PlayerSyncState syncState,
            TrackedChunk tracked,
            WaterVolumeChunk volume,
            SyncBudget budget
    ) {
        try {
            syncState.baseline = new BaselineTransfer(
                    tracked.chunkKey(),
                    volume.revision(),
                    WaterVolumeChunkPayload.pagesFromChunk(tracked.chunk(), volume)
            );
        } catch (IllegalStateException exception) {
            // Keep the player's known revision unchanged so a later compacted
            // chunk or operator repair automatically retries the baseline.
            ModConstants.LOGGER.error(
                    "Canonical water chunk {} exceeds bounded baseline sync: {}",
                    tracked.chunk().getPos(),
                    exception.getMessage()
            );
            return true;
        }
        return sendPendingBaseline(player, syncState, budget);
    }

    private static boolean sendPendingBaseline(
            ServerPlayer player,
            PlayerSyncState syncState,
            SyncBudget budget
    ) {
        BaselineTransfer transfer = syncState.baseline;
        if (transfer == null) {
            return true;
        }
        while (transfer.nextPage < transfer.pages.size()) {
            WaterVolumeChunkPayload page = transfer.pages.get(transfer.nextPage);
            if (!budget.tryConsume(Math.max(1, page.cellCount()))) {
                return false;
            }
            PacketDistributor.sendToPlayer(player, page);
            transfer.nextPage++;
        }
        syncState.revisions.put(transfer.chunkKey, transfer.revision);
        syncState.baseline = null;
        return true;
    }

    private record TrackedChunk(long chunkKey, LevelChunk chunk) {
    }

    private static final class SyncBudget {
        private int payloads;
        private int cells;

        private boolean canSendAnything() {
            return payloads < MAX_PAYLOADS_PER_PLAYER_PER_PASS
                    && cells < MAX_CELLS_PER_PLAYER_PER_PASS;
        }

        private int remainingCells() {
            return Math.max(0, MAX_CELLS_PER_PLAYER_PER_PASS - cells);
        }

        private boolean tryConsume(int requestedCells) {
            int boundedCells = Math.max(1, requestedCells);
            if (payloads >= MAX_PAYLOADS_PER_PLAYER_PER_PASS
                    || cells + boundedCells > MAX_CELLS_PER_PLAYER_PER_PASS) {
                return false;
            }
            payloads++;
            cells += boundedCells;
            return true;
        }
    }

    private static final class BaselineTransfer {
        private final long chunkKey;
        private final long revision;
        private final List<WaterVolumeChunkPayload> pages;
        private int nextPage;

        private BaselineTransfer(long chunkKey, long revision, List<WaterVolumeChunkPayload> pages) {
            this.chunkKey = chunkKey;
            this.revision = revision;
            this.pages = pages;
        }
    }

    private static final class PlayerSyncState {
        private final ResourceKey<Level> dimension;
        private final Map<Long, Long> revisions = new HashMap<>();
        private BaselineTransfer baseline;

        private PlayerSyncState(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }

        private void retainLoadedChunks(Set<Long> retainedChunks) {
            revisions.keySet().retainAll(retainedChunks);
            if (baseline != null && !retainedChunks.contains(baseline.chunkKey)) {
                baseline = null;
            }
        }

        private void forget(long chunkKey) {
            revisions.remove(chunkKey);
            if (baseline != null && baseline.chunkKey == chunkKey) {
                baseline = null;
            }
        }
    }
}
