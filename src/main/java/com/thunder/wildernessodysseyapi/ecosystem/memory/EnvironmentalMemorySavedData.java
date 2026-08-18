package com.thunder.wildernessodysseyapi.ecosystem.memory;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Per-dimension persistent storage for chunk-sized environmental-memory cells.
 *
 * <p>Decay is calculated from elapsed game time only when a cell is read or
 * changed. A one-cell maintenance rotation amortizes cleanup across ordinary
 * accesses, avoiding a global tick or full-map scan.</p>
 */
final class EnvironmentalMemorySavedData extends SavedData {

    static final String DATA_NAME = ModConstants.MOD_ID + "_environmental_memory";
    private static final int FORMAT_VERSION = 1;
    private static final long TICKS_PER_DAY = 24_000L;

    private final Map<Long, MemoryCell> cells = new HashMap<>();
    private final ArrayDeque<Long> maintenanceQueue = new ArrayDeque<>();
    private final Set<Long> queuedKeys = new HashSet<>();

    /** Returns the level-owned ledger, naturally separating Minecraft dimensions. */
    static EnvironmentalMemorySavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(EnvironmentalMemorySavedData::new, EnvironmentalMemorySavedData::load),
                DATA_NAME
        );
    }

    static EnvironmentalMemorySavedData load(CompoundTag root, HolderLookup.Provider registries) {
        EnvironmentalMemorySavedData data = new EnvironmentalMemorySavedData();
        ListTag encodedCells = root.getList("cells", Tag.TAG_COMPOUND);
        for (int index = 0; index < encodedCells.size(); index++) {
            CompoundTag encoded = encodedCells.getCompound(index);
            int chunkX = encoded.getInt("chunk_x");
            int chunkZ = encoded.getInt("chunk_z");
            long key = ChunkPos.asLong(chunkX, chunkZ);
            MemoryCell cell = MemoryCell.load(encoded, chunkX, chunkZ);
            if (cell.strongestStoredActivity() > 0.0) {
                data.cells.put(key, cell);
                data.enqueue(key);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        root.putInt("format_version", FORMAT_VERSION);
        ListTag encodedCells = new ListTag();
        for (Map.Entry<Long, MemoryCell> entry : cells.entrySet()) {
            ChunkPos cellPos = new ChunkPos(entry.getKey());
            encodedCells.add(entry.getValue().save(cellPos));
        }
        root.put("cells", encodedCells);
        return root;
    }

    Optional<EnvironmentalMemory> memory(long key, long gameTime, Settings settings) {
        MemoryCell stored = cells.get(key);
        if (stored == null) {
            return Optional.empty();
        }
        DecayedCell decayed = stored.decay(gameTime, settings.decayPerDay());
        if (decayed.strongestActivity() <= settings.cleanupThreshold()) {
            cells.remove(key);
            setDirty();
            return Optional.empty();
        }
        return Optional.of(decayed.snapshot(new ChunkPos(key), stored));
    }

    EnvironmentalMemory add(
            long key,
            BlockPos position,
            double amount,
            DisturbanceSource source,
            UUID sourceId,
            long gameTime,
            Settings settings
    ) {
        MemoryCell previous = cells.get(key);
        DecayedCell decayed = previous == null
                ? DecayedCell.EMPTY
                : previous.decay(gameTime, settings.decayPerDay());
        double maximum = settings.maximumDisturbance();
        double disturbance = cap(decayed.disturbance() + amount, maximum);
        double fire = cap(decayed.fireActivity()
                + (source == DisturbanceSource.FIRE ? amount : 0.0), maximum);
        double combat = cap(decayed.combatActivity()
                + (source == DisturbanceSource.COMBAT ? amount : 0.0), maximum);
        double traffic = cap(decayed.playerTraffic()
                + (isPlayerActivity(source) ? amount : 0.0), maximum);

        MemoryCell updated = new MemoryCell(
                disturbance,
                fire,
                combat,
                traffic,
                gameTime,
                source,
                position.asLong(),
                sourceId
        );
        cells.put(key, updated);
        enqueue(key);
        setDirty();
        return updated.decay(gameTime, settings.decayPerDay()).snapshot(new ChunkPos(key), updated);
    }

    /** Checks at most one rotating entry, keeping cleanup cost constant per access. */
    void pruneOne(long gameTime, Settings settings) {
        Long key = maintenanceQueue.pollFirst();
        if (key == null) {
            return;
        }
        queuedKeys.remove(key);
        MemoryCell cell = cells.get(key);
        if (cell == null) {
            return;
        }
        if (cell.decay(gameTime, settings.decayPerDay()).strongestActivity()
                <= settings.cleanupThreshold()) {
            cells.remove(key);
            setDirty();
            return;
        }
        enqueue(key);
    }

    boolean clear(long key) {
        if (cells.remove(key) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    int size() {
        return cells.size();
    }

    private void enqueue(long key) {
        if (queuedKeys.add(key)) {
            maintenanceQueue.addLast(key);
        }
    }

    private static boolean isPlayerActivity(DisturbanceSource source) {
        return source == DisturbanceSource.PLAYER_MOVEMENT
                || source == DisturbanceSource.PLAYER_ACTIVITY;
    }

    private static double cap(double value, double maximum) {
        return Math.max(0.0, Math.min(maximum, Double.isFinite(value) ? value : 0.0));
    }

    /** Runtime settings are passed in so persistence stays independent of config loading. */
    record Settings(double decayPerDay, double cleanupThreshold, double maximumDisturbance) {
        Settings {
            decayPerDay = Math.max(0.0, finite(decayPerDay));
            cleanupThreshold = Math.max(0.0, Math.min(1.0, finite(cleanupThreshold)));
            maximumDisturbance = Math.max(cleanupThreshold, Math.min(1.0, finite(maximumDisturbance)));
        }
    }

    private record MemoryCell(
            double disturbance,
            double fireActivity,
            double combatActivity,
            double playerTraffic,
            long lastUpdatedGameTime,
            DisturbanceSource lastSource,
            long lastSourcePosition,
            UUID lastSourceId
    ) {
        private DecayedCell decay(long gameTime, double decayPerDay) {
            long elapsed = Math.max(0L, gameTime - lastUpdatedGameTime);
            double decay = decayPerDay * elapsed / TICKS_PER_DAY;
            return new DecayedCell(
                    Math.max(0.0, disturbance - decay),
                    Math.max(0.0, fireActivity - decay),
                    Math.max(0.0, combatActivity - decay),
                    Math.max(0.0, playerTraffic - decay),
                    Math.max(0.0, disturbance - Math.max(0.0, disturbance - decay)),
                    gameTime
            );
        }

        private double strongestStoredActivity() {
            return Math.max(Math.max(disturbance, fireActivity), Math.max(combatActivity, playerTraffic));
        }

        private CompoundTag save(ChunkPos cell) {
            CompoundTag encoded = new CompoundTag();
            encoded.putInt("chunk_x", cell.x);
            encoded.putInt("chunk_z", cell.z);
            encoded.putDouble("disturbance", disturbance);
            encoded.putDouble("fire_activity", fireActivity);
            encoded.putDouble("combat_activity", combatActivity);
            encoded.putDouble("player_traffic", playerTraffic);
            encoded.putLong("last_updated", lastUpdatedGameTime);
            encoded.putString("last_source", lastSource.serializedName());
            encoded.putLong("last_source_pos", lastSourcePosition);
            if (lastSourceId != null) {
                encoded.putUUID("last_source_id", lastSourceId);
            }
            return encoded;
        }

        private static MemoryCell load(CompoundTag encoded, int chunkX, int chunkZ) {
            ChunkPos cell = new ChunkPos(chunkX, chunkZ);
            BlockPos fallbackPosition = new BlockPos(cell.getMiddleBlockX(), 0, cell.getMiddleBlockZ());
            return new MemoryCell(
                    unit(encoded.getDouble("disturbance")),
                    unit(encoded.getDouble("fire_activity")),
                    unit(encoded.getDouble("combat_activity")),
                    unit(encoded.getDouble("player_traffic")),
                    Math.max(0L, encoded.getLong("last_updated")),
                    DisturbanceSource.fromSerializedName(encoded.getString("last_source")),
                    encoded.contains("last_source_pos", Tag.TAG_LONG)
                            ? encoded.getLong("last_source_pos")
                            : fallbackPosition.asLong(),
                    encoded.hasUUID("last_source_id") ? encoded.getUUID("last_source_id") : null
            );
        }
    }

    private record DecayedCell(
            double disturbance,
            double fireActivity,
            double combatActivity,
            double playerTraffic,
            double disturbanceDecayApplied,
            long observedGameTime
    ) {
        private static final DecayedCell EMPTY = new DecayedCell(0.0, 0.0, 0.0, 0.0, 0.0, 0L);

        private double strongestActivity() {
            return Math.max(Math.max(disturbance, fireActivity), Math.max(combatActivity, playerTraffic));
        }

        private EnvironmentalMemory snapshot(ChunkPos cell, MemoryCell stored) {
            return new EnvironmentalMemory(
                    cell,
                    disturbance,
                    fireActivity,
                    combatActivity,
                    playerTraffic,
                    stored.lastUpdatedGameTime(),
                    observedGameTime,
                    disturbanceDecayApplied,
                    stored.lastSource(),
                    BlockPos.of(stored.lastSourcePosition()),
                    stored.lastSourceId()
            );
        }
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, finite(value)));
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }
}
