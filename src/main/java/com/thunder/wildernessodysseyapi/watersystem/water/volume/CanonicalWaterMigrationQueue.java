package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Defers vanilla-to-Wilderness water migration away from chunk-load callbacks.
 *
 * <p>Chunk load can happen while Minecraft is preparing initial spawn chunks.
 * Rewriting many ocean blocks from that callback can stall world creation, so
 * loaded chunks are only queued there. Server ticks later process small slices
 * with explicit budgets and never force unloaded chunks back into memory.</p>
 */
public final class CanonicalWaterMigrationQueue {

    private static final Queue<MigrationTask> QUEUE = new ArrayDeque<>();
    private static final Set<String> QUEUED_KEYS = new HashSet<>();

    private static long touchedChunks;
    private static long completedChunks;
    private static long importedCells;
    private static long convertedBlocks;
    private static long skippedUnloadedChunks;
    private static long droppedChunks;
    private static TickResult lastTick = TickResult.EMPTY;

    private CanonicalWaterMigrationQueue() {
    }

    /**
     * Queues a loaded chunk for later water import/migration.
     *
     * <p>This method is intentionally cheap enough to call from chunk-load
     * events. It records only a dimension and chunk position.</p>
     */
    public static synchronized void enqueue(ServerLevel level, ChunkPos pos) {
        if (!WaterSimulationConfig.ENABLE_CANONICAL_WORLD_SEEDING.get()) {
            return;
        }
        MigrationTask task = new MigrationTask(level.dimension(), pos, 0);
        if (QUEUED_KEYS.contains(task.key())) {
            return;
        }
        if (QUEUE.size() >= WaterSimulationConfig.automaticMigrationMaxQueuedChunks()) {
            droppedChunks++;
            return;
        }

        QUEUED_KEYS.add(task.key());
        QUEUE.offer(task);
    }

    /**
     * Processes queued chunks under the configured tick budgets.
     *
     * <p>Processing waits until at least one player exists, which avoids doing
     * water migration work while a new world is still stuck on the loading
     * screen preparing spawn chunks.</p>
     */
    public static synchronized TickResult runTick(MinecraftServer server) {
        if (!WaterSimulationConfig.ENABLE_CANONICAL_WORLD_SEEDING.get()
                || QUEUE.isEmpty()
                || server.getPlayerList().getPlayerCount() <= 0) {
            lastTick = TickResult.EMPTY.withQueuedChunks(QUEUE.size());
            return lastTick;
        }

        boolean allowBlockConversion = WaterSimulationConfig.automaticWaterMigrationEnabled()
                && WaterSimulationConfig.convertSeededWorldWaterToWilderness();
        int chunksRemaining = WaterSimulationConfig.automaticMigrationChunksPerTick();
        int columnsRemaining = WaterSimulationConfig.automaticMigrationColumnsPerTick();
        int conversionsRemaining = allowBlockConversion
                ? WaterSimulationConfig.automaticMigrationConvertedBlocksPerTick()
                : Integer.MAX_VALUE;

        int attemptsRemaining = QUEUE.size();
        int tickTouched = 0;
        int tickCompleted = 0;
        int tickSkippedUnloaded = 0;
        CanonicalWaterSeeder.SeedStats tickStats = CanonicalWaterSeeder.SeedStats.EMPTY;

        while (!QUEUE.isEmpty()
                && attemptsRemaining > 0
                && chunksRemaining > 0
                && columnsRemaining > 0
                && (!allowBlockConversion || conversionsRemaining > 0)) {
            attemptsRemaining--;
            MigrationTask task = QUEUE.poll();
            if (task == null) {
                break;
            }

            ServerLevel level = server.getLevel(task.dimension());
            if (level == null) {
                QUEUED_KEYS.remove(task.key());
                continue;
            }
            if (level.players().isEmpty()) {
                QUEUE.offer(task);
                continue;
            }

            LevelChunk chunk = level.getChunkSource().getChunkNow(task.pos().x, task.pos().z);
            if (chunk == null) {
                QUEUED_KEYS.remove(task.key());
                skippedUnloadedChunks++;
                tickSkippedUnloaded++;
                continue;
            }

            CanonicalWaterSeeder.SeedSlice slice = CanonicalWaterSeeder.seedChunkSlice(
                    level,
                    chunk,
                    WaterSimulationConfig.worldSeedMaxColumnDepth(),
                    task.nextColumnIndex(),
                    columnsRemaining,
                    conversionsRemaining,
                    allowBlockConversion
            );
            CanonicalWaterSeeder.SeedStats sliceStats = slice.stats();
            tickStats = tickStats.plus(sliceStats);
            columnsRemaining -= sliceStats.scannedColumns();
            if (allowBlockConversion) {
                conversionsRemaining -= sliceStats.convertedBlocks();
            }
            chunksRemaining--;
            tickTouched++;

            if (slice.complete()) {
                QUEUED_KEYS.remove(task.key());
                tickCompleted++;
            } else {
                QUEUE.offer(task.withNextColumnIndex(slice.nextColumnIndex()));
                if (sliceStats.scannedColumns() <= 0) {
                    break;
                }
            }
        }

        touchedChunks += tickTouched;
        completedChunks += tickCompleted;
        importedCells += tickStats.importedCells();
        convertedBlocks += tickStats.convertedBlocks();
        lastTick = new TickResult(
                tickTouched,
                tickCompleted,
                tickStats.scannedColumns(),
                tickStats.importedCells(),
                tickStats.convertedBlocks(),
                tickSkippedUnloaded,
                QUEUE.size()
        );
        return lastTick;
    }

    /** Clears queued work for one unloading level. */
    public static synchronized void clearLevel(ServerLevel level) {
        String prefix = level.dimension().location() + ":";
        QUEUE.removeIf(task -> {
            if (task.key().startsWith(prefix)) {
                QUEUED_KEYS.remove(task.key());
                return true;
            }
            return false;
        });
    }

    /** Clears all queued migration work, typically during server shutdown. */
    public static synchronized void clearAll() {
        QUEUE.clear();
        QUEUED_KEYS.clear();
        touchedChunks = 0L;
        completedChunks = 0L;
        importedCells = 0L;
        convertedBlocks = 0L;
        skippedUnloadedChunks = 0L;
        droppedChunks = 0L;
        lastTick = TickResult.EMPTY;
    }

    /** Returns a snapshot used by diagnostics commands. */
    public static synchronized MigrationStatus status() {
        return new MigrationStatus(
                WaterSimulationConfig.ENABLE_CANONICAL_WORLD_SEEDING.get(),
                WaterSimulationConfig.automaticWaterMigrationEnabled()
                        && WaterSimulationConfig.convertSeededWorldWaterToWilderness(),
                QUEUE.size(),
                touchedChunks,
                completedChunks,
                importedCells,
                convertedBlocks,
                skippedUnloadedChunks,
                droppedChunks,
                lastTick
        );
    }

    private record MigrationTask(ResourceKey<Level> dimension, ChunkPos pos, int nextColumnIndex) {
        private String key() {
            return dimension.location() + ":" + pos.x + ":" + pos.z;
        }

        private MigrationTask withNextColumnIndex(int nextColumnIndex) {
            return new MigrationTask(dimension, pos, nextColumnIndex);
        }
    }

    /** Per-tick migration counters. */
    public record TickResult(
            int touchedChunks,
            int completedChunks,
            int scannedColumns,
            int importedCells,
            int convertedBlocks,
            int skippedUnloadedChunks,
            int queuedChunks
    ) {
        public static final TickResult EMPTY = new TickResult(0, 0, 0, 0, 0, 0, 0);

        private TickResult withQueuedChunks(int queuedChunks) {
            return new TickResult(touchedChunks, completedChunks, scannedColumns, importedCells, convertedBlocks,
                    skippedUnloadedChunks, queuedChunks);
        }
    }

    /** Long-lived migration queue counters exposed by {@code /wowater migration}. */
    public record MigrationStatus(
            boolean seedingEnabled,
            boolean blockConversionEnabled,
            int queuedChunks,
            long touchedChunks,
            long completedChunks,
            long importedCells,
            long convertedBlocks,
            long skippedUnloadedChunks,
            long droppedChunks,
            TickResult lastTick
    ) {
    }
}
