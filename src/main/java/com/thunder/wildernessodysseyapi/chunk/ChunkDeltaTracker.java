package com.thunder.wildernessodysseyapi.chunk;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Tracks dirty chunk data and decides when to stream deltas versus full sync payloads.
 */
public final class ChunkDeltaTracker {
    private static final ConcurrentMap<ChunkPos, ChunkDeltaAccumulator> DIRTY_CHUNKS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, PlayerLightCache> PLAYER_LIGHT_CACHE = new ConcurrentHashMap<>();

    private static ChunkStreamingConfig.ChunkConfigValues config = ChunkStreamingConfig.values();

    private ChunkDeltaTracker() {
    }

    public static synchronized void configure(ChunkStreamingConfig.ChunkConfigValues values) {
        config = Objects.requireNonNull(values, "values");
        DIRTY_CHUNKS.clear();
        PLAYER_LIGHT_CACHE.clear();
    }

    public static void shutdown() {
        DIRTY_CHUNKS.clear();
        PLAYER_LIGHT_CACHE.clear();
    }

    public static void trackBlockChange(ChunkPos pos, BlockPos blockPos, BlockState state) {
        if (!config.enabled() || pos == null || blockPos == null || state == null) {
            return;
        }
        ChunkDeltaAccumulator accumulator = DIRTY_CHUNKS.computeIfAbsent(pos, ignored -> new ChunkDeltaAccumulator());
        accumulator.recordBlock(blockPos, state);
    }

    public static void trackLightChange(ChunkPos pos, LightLayer layer, int sectionY, byte[] lightData) {
        if (!config.enabled() || pos == null || layer == null || lightData == null) {
            return;
        }
        ChunkDeltaAccumulator accumulator = DIRTY_CHUNKS.computeIfAbsent(pos, ignored -> new ChunkDeltaAccumulator());
        accumulator.recordLight(layer, sectionY, lightData);
    }

    public static Optional<ChunkDeltaPayload> buildSyncPayload(ServerPlayer player,
                                                               ChunkPos pos,
                                                               Supplier<CompoundTag> fullChunkSupplier,
                                                               ChunkLightSnapshot lightSnapshot) {
        if (!config.enabled() || pos == null || player == null) {
            return Optional.empty();
        }
        ChunkDeltaAccumulator accumulator = DIRTY_CHUNKS.get(pos);
        if (accumulator == null) {
            return Optional.empty();
        }
        int pendingChanges = accumulator.changeCost();
        if (pendingChanges > config.deltaChangeBudget()) {
            CompoundTag payload = fullChunkSupplier == null ? null : fullChunkSupplier.get();
            DIRTY_CHUNKS.remove(pos);
            resetLightCache(player, pos, lightSnapshot);
            return Optional.of(ChunkDeltaPayload.full(pos, payload, pendingChanges, true));
        }

        ChunkDeltaSnapshot snapshot = accumulator.drain();
        Map<LightLayer, Map<Integer, LightBandDelta>> lightDeltas = buildLightDeltas(player, pos, snapshot.lightBands(), lightSnapshot);
        int payloadCost = snapshot.blockChanges().size() + countLightBands(lightDeltas);
        boolean exceeded = payloadCost > config.deltaChangeBudget();
        if (exceeded) {
            CompoundTag payload = fullChunkSupplier == null ? null : fullChunkSupplier.get();
            resetLightCache(player, pos, lightSnapshot);
            DIRTY_CHUNKS.remove(pos);
            return Optional.of(ChunkDeltaPayload.full(pos, payload, payloadCost, true));
        }
        DIRTY_CHUNKS.remove(pos);
        ChunkDeltaPayload payload = new ChunkDeltaPayload(
                pos,
                false,
                null,
                snapshot.blockChanges(),
                lightDeltas,
                payloadCost,
                false
        );
        if (!payload.hasDeltas()) {
            return Optional.empty();
        }
        return Optional.of(payload);
    }

    public static void dropPlayer(ServerPlayer player) {
        if (player != null) {
            PLAYER_LIGHT_CACHE.remove(player.getUUID());
        }
    }

    public static void dropChunkForPlayer(ServerPlayer player, ChunkPos pos) {
        if (player == null || pos == null) {
            return;
        }
        PLAYER_LIGHT_CACHE.computeIfPresent(player.getUUID(), (uuid, cache) -> {
            cache.dropChunk(pos);
            return cache;
        });
    }

    private static Map<LightLayer, Map<Integer, LightBandDelta>> buildLightDeltas(ServerPlayer player,
                                                                                  ChunkPos pos,
                                                                                  Map<LightLayer, Map<Integer, byte[]>> dirtyBands,
                                                                                  ChunkLightSnapshot snapshot) {
        if (dirtyBands.isEmpty()) {
            return Collections.emptyMap();
        }
        PlayerLightCache cache = PLAYER_LIGHT_CACHE.computeIfAbsent(player.getUUID(), ignored -> new PlayerLightCache());
        if (snapshot != null) {
            cache.seedFromSnapshot(pos, snapshot);
        }
        Map<LightLayer, Map<Integer, LightBandDelta>> deltas = new EnumMap<>(LightLayer.class);
        dirtyBands.forEach((layer, sections) -> {
            Map<Integer, LightBandDelta> perLayer = new HashMap<>();
            Map<Integer, byte[]> lastSent = cache.getLayer(pos, layer);
            sections.forEach((sectionY, latest) -> {
                byte[] previous = lastSent.get(sectionY);
                if (previous != null && java.util.Arrays.equals(previous, latest)) {
                    return;
                }
                byte[] compressed = compress(latest);
                perLayer.put(sectionY, new LightBandDelta(layer, sectionY, compressed, latest.length));
                cache.updateBand(pos, layer, sectionY, latest);
            });
            if (!perLayer.isEmpty()) {
                deltas.put(layer, perLayer);
            }
        });
        return deltas;
    }

    private static byte[] compress(byte[] data) {
        if (data == null || data.length == 0) {
            return new byte[0];
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length);
             DeflaterOutputStream deflater = new DeflaterOutputStream(baos, new Deflater(config.lightCompressionLevel()))) {
            deflater.write(data);
            deflater.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            ModConstants.LOGGER.warn("[ChunkDelta] Failed to compress light band, sending raw", e);
            return data.clone();
        }
    }

    private static void resetLightCache(ServerPlayer player, ChunkPos pos, ChunkLightSnapshot snapshot) {
        PLAYER_LIGHT_CACHE.computeIfPresent(player.getUUID(), (uuid, cache) -> {
            cache.dropChunk(pos);
            if (snapshot != null) {
                cache.seedFromSnapshot(pos, snapshot);
            }
            return cache;
        });
    }

    private static int countLightBands(Map<LightLayer, Map<Integer, LightBandDelta>> deltas) {
        return deltas.values().stream().mapToInt(Map::size).sum();
    }

    private static final class ChunkDeltaAccumulator {
        private final Map<BlockPos, BlockState> blockChanges = new HashMap<>();
        private final Map<LightLayer, Map<Integer, byte[]>> lightBands = new EnumMap<>(LightLayer.class);

        synchronized void recordBlock(BlockPos pos, BlockState state) {
            blockChanges.put(pos.immutable(), state);
        }

        synchronized void recordLight(LightLayer layer, int sectionY, byte[] data) {
            lightBands.computeIfAbsent(layer, ignored -> new HashMap<>()).put(sectionY, data.clone());
        }

        synchronized int changeCost() {
            return blockChanges.size() + lightBands.values().stream().mapToInt(Map::size).sum();
        }

        synchronized ChunkDeltaSnapshot drain() {
            Map<BlockPos, BlockState> blocks = new HashMap<>(blockChanges);
            Map<LightLayer, Map<Integer, byte[]>> lights = new EnumMap<>(LightLayer.class);
            lightBands.forEach((layer, sections) -> {
                Map<Integer, byte[]> copy = new HashMap<>();
                sections.forEach((sectionY, data) -> copy.put(sectionY, data.clone()));
                lights.put(layer, copy);
            });
            blockChanges.clear();
            lightBands.clear();
            return new ChunkDeltaSnapshot(blocks, lights);
        }
    }

    private record ChunkDeltaSnapshot(
            Map<BlockPos, BlockState> blockChanges,
            Map<LightLayer, Map<Integer, byte[]>> lightBands
    ) {
    }
}
