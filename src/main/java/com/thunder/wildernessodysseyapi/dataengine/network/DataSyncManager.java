package com.thunder.wildernessodysseyapi.dataengine.network;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.dataengine.config.DataEngineConfig;
import com.thunder.wildernessodysseyapi.dataengine.interest.InterestManager;
import com.thunder.wildernessodysseyapi.dataengine.interest.InterestProfile;
import com.thunder.wildernessodysseyapi.dataengine.interest.InterestRegion;
import com.thunder.wildernessodysseyapi.dataengine.metrics.DataEngineMetrics;
import com.thunder.wildernessodysseyapi.dataengine.queue.UpdatePriority;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * SERVER THREAD ONLY. Interest-filtered, coalescing delta batcher.
 *
 * <p>Pending state is bounded by entries and encoded bytes. Exact system/target/
 * field-mask duplicates replace older bodies. Critical deltas and explicitly
 * disabled batching bypass delay but still obey the packet byte limit.</p>
 */
public final class DataSyncManager {
    private static final long WARNING_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);

    private final InterestManager interestManager;
    private final DataEngineMetrics metrics;
    private final Map<UUID, PlayerBatchState> pendingByPlayer = new LinkedHashMap<>();

    private DataEngineConfig.Values config;
    private int pendingEntries;
    private long pendingBytes;
    private long lastBackpressureWarningNanos;

    public DataSyncManager(
            InterestManager interestManager,
            DataEngineMetrics metrics,
            DataEngineConfig.Values config
    ) {
        this.interestManager = Objects.requireNonNull(interestManager, "Interest manager is required");
        this.metrics = Objects.requireNonNull(metrics, "Metrics are required");
        this.config = Objects.requireNonNull(config, "Data Engine config is required");
    }

    /** Applies live non-structural batching settings after config reload. */
    public void updateConfig(DataEngineConfig.Values config) {
        this.config = Objects.requireNonNull(config, "Data Engine config is required");
    }

    /** Queues one delta for a known player, or sends it immediately when required. */
    public boolean sendToPlayer(ServerPlayer player, DataDelta delta, long currentTick) {
        Objects.requireNonNull(player, "Server player is required");
        Objects.requireNonNull(delta, "Data delta is required");
        int bytes = delta.approximateEncodedBytes();
        if (bytes > config.maxBatchBytes()) {
            metrics.recordDroppedOrSuperseded();
            warnBackpressure("delta exceeds maxBatchBytes", delta.systemId().toString());
            return false;
        }
        if (delta.priority() == UpdatePriority.CRITICAL || !config.networkBatching()) {
            return sendNow(player, delta);
        }

        PlayerBatchState state = pendingByPlayer.computeIfAbsent(player.getUUID(), ignored -> new PlayerBatchState());
        DataDelta.DeltaIdentity identity = delta.identity();
        PendingDelta existing = state.entries.get(identity);
        if (existing != null) {
            long byteDifference = bytes - existing.estimatedBytes;
            if (byteDifference > 0L && pendingBytes + byteDifference > config.maxPendingNetworkBytes()) {
                metrics.recordDroppedOrSuperseded();
                warnBackpressure("replacement exceeds pending byte bound", delta.systemId().toString());
                return false;
            }
            state.entries.put(identity, new PendingDelta(delta, currentTick, bytes));
            pendingBytes += byteDifference;
            metrics.recordNetworkCoalesced();
            return true;
        }

        while (pendingEntries >= config.maxQueueSize()
                || pendingBytes + bytes > config.maxPendingNetworkBytes()) {
            if (!evictLowerPriorityAnywhere(state, delta.priority())) {
                metrics.recordDroppedOrSuperseded();
                warnBackpressure("pending network queue is full", delta.systemId().toString());
                return false;
            }
        }

        state.entries.put(identity, new PendingDelta(delta, currentTick, bytes));
        state.firstQueuedTick = Math.min(state.firstQueuedTick, currentTick);
        pendingEntries++;
        pendingBytes += bytes;
        return true;
    }

    /** Filters recipients through chunk buckets before adding per-player deltas. */
    public int sendToRegion(
            MinecraftServer server,
            InterestRegion region,
            InterestProfile profile,
            DataDelta delta,
            long currentTick
    ) {
        Objects.requireNonNull(server, "Minecraft server is required");
        if (!config.interestManagement()) {
            int sent = 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.level().dimension().location().equals(region.dimension())
                        && sendToPlayer(player, delta, currentTick)) {
                    sent++;
                }
            }
            return sent;
        }

        int[] queued = {0};
        InterestManager.InterestDispatchResult result = interestManager.forEachInterested(
                region,
                profile,
                (player, tier) -> {
                    if (sendToPlayer(player, delta, currentTick)) {
                        queued[0]++;
                    }
                }
        );
        metrics.recordInterestFiltered(result.filteredPlayers());
        return queued[0];
    }

    /** Queues a delta only for players explicitly subscribed to a feature. */
    public int sendToFeature(ResourceLocation featureId, DataDelta delta, long currentTick) {
        int[] queued = {0};
        InterestManager.InterestDispatchResult result = interestManager.forEachFeatureInterested(
                featureId,
                player -> {
                    if (sendToPlayer(player, delta, currentTick)) {
                        queued[0]++;
                    }
                }
        );
        metrics.recordInterestFiltered(result.filteredPlayers());
        return queued[0];
    }

    /**
     * Flushes due player batches while both the Data Engine deadline and
     * Minecraft's live spare-time allowance remain. Pending final state stays
     * queued when either limit expires.
     */
    public int flush(
            MinecraftServer server,
            long currentTick,
            long deadlineNanos,
            BooleanSupplier serverHasTime
    ) {
        Objects.requireNonNull(serverHasTime, "Server time allowance is required");
        int batches = 0;
        Iterator<Map.Entry<UUID, PlayerBatchState>> states = pendingByPlayer.entrySet().iterator();
        while (states.hasNext()
                && serverHasTime.getAsBoolean()
                && System.nanoTime() < deadlineNanos) {
            Map.Entry<UUID, PlayerBatchState> stateEntry = states.next();
            PlayerBatchState state = stateEntry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(stateEntry.getKey());
            if (player == null) {
                removeState(state);
                states.remove();
                continue;
            }
            if (state.entries.isEmpty()) {
                states.remove();
                continue;
            }
            if (currentTick - state.firstQueuedTick < config.maxBatchDelayTicks()) {
                continue;
            }
            while (!state.entries.isEmpty()
                    && serverHasTime.getAsBoolean()
                    && System.nanoTime() < deadlineNanos) {
                List<DataDelta> entries = takeBatch(state);
                if (entries.isEmpty()) {
                    break;
                }
                DataPacketBatch batch = new DataPacketBatch(entries);
                try {
                    PacketDistributor.sendToPlayer(player, batch);
                    metrics.recordNetworkBatch(entries, batch.approximateEncodedBytes());
                } catch (RuntimeException exception) {
                    metrics.recordFailure(entries.getFirst().systemId());
                    ModConstants.LOGGER.error(
                            "[Data Engine] Failed sending {} batched deltas to {}",
                            entries.size(),
                            player.getGameProfile().getName(),
                            exception
                    );
                }
                batches++;
            }
            state.firstQueuedTick = state.entries.isEmpty()
                    ? Long.MAX_VALUE
                    : state.entries.values().iterator().next().queuedTick;
            if (state.entries.isEmpty()) {
                states.remove();
            }
        }
        return batches;
    }

    public int pendingEntries() {
        return pendingEntries;
    }

    public long pendingBytes() {
        return pendingBytes;
    }

    public void clear() {
        pendingByPlayer.clear();
        pendingEntries = 0;
        pendingBytes = 0L;
    }

    private boolean sendNow(ServerPlayer player, DataDelta delta) {
        DataPacketBatch batch = new DataPacketBatch(List.of(delta));
        try {
            PacketDistributor.sendToPlayer(player, batch);
            metrics.recordNetworkBatch(List.of(delta), batch.approximateEncodedBytes());
            return true;
        } catch (RuntimeException exception) {
            metrics.recordFailure(delta.systemId());
            ModConstants.LOGGER.error(
                    "[Data Engine] Failed sending critical/immediate delta {} to {}",
                    delta.systemId(),
                    player.getGameProfile().getName(),
                    exception
            );
            return false;
        }
    }

    private List<DataDelta> takeBatch(PlayerBatchState state) {
        List<DataDelta> batch = new ArrayList<>(Math.min(config.maxBatchEntries(), state.entries.size()));
        int batchBytes = 5;
        Iterator<Map.Entry<DataDelta.DeltaIdentity, PendingDelta>> iterator = state.entries.entrySet().iterator();
        while (iterator.hasNext() && batch.size() < config.maxBatchEntries()) {
            PendingDelta pending = iterator.next().getValue();
            if (!batch.isEmpty() && batchBytes + pending.estimatedBytes > config.maxBatchBytes()) {
                break;
            }
            batch.add(pending.delta);
            batchBytes += pending.estimatedBytes;
            iterator.remove();
            pendingEntries--;
            pendingBytes -= pending.estimatedBytes;
        }
        return batch;
    }

    private boolean evictLowerPriority(PlayerBatchState state, UpdatePriority incomingPriority) {
        Iterator<Map.Entry<DataDelta.DeltaIdentity, PendingDelta>> iterator = state.entries.entrySet().iterator();
        while (iterator.hasNext()) {
            PendingDelta pending = iterator.next().getValue();
            if ((pending.delta.priority() == UpdatePriority.BACKGROUND
                    || pending.delta.priority() == UpdatePriority.LOW)
                    && incomingPriority.isMoreUrgentThan(pending.delta.priority())) {
                iterator.remove();
                pendingEntries--;
                pendingBytes -= pending.estimatedBytes;
                metrics.recordDroppedOrSuperseded();
                return true;
            }
        }
        return false;
    }

    private boolean evictLowerPriorityAnywhere(PlayerBatchState preferred, UpdatePriority incomingPriority) {
        if (evictLowerPriority(preferred, incomingPriority)) {
            return true;
        }
        for (PlayerBatchState state : pendingByPlayer.values()) {
            if (state != preferred && evictLowerPriority(state, incomingPriority)) {
                return true;
            }
        }
        return false;
    }

    private void removeState(PlayerBatchState state) {
        pendingEntries -= state.entries.size();
        for (PendingDelta pending : state.entries.values()) {
            pendingBytes -= pending.estimatedBytes;
        }
        state.entries.clear();
    }

    private void warnBackpressure(String reason, String systemId) {
        long now = System.nanoTime();
        if (!config.debugLogging() || now - lastBackpressureWarningNanos < WARNING_INTERVAL_NANOS) {
            return;
        }
        lastBackpressureWarningNanos = now;
        ModConstants.LOGGER.warn(
                "[Data Engine] Network backpressure: {} for {} ({} entries, {} bytes pending)",
                reason,
                systemId,
                pendingEntries,
                pendingBytes
        );
    }

    private static final class PlayerBatchState {
        private final LinkedHashMap<DataDelta.DeltaIdentity, PendingDelta> entries = new LinkedHashMap<>();
        private long firstQueuedTick = Long.MAX_VALUE;
    }

    private record PendingDelta(DataDelta delta, long queuedTick, int estimatedBytes) {
    }
}
