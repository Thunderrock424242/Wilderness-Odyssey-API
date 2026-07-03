package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
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

    private static final Deque<MigrationTask> QUEUE = new ArrayDeque<>();
    private static final Set<String> QUEUED_KEYS = new HashSet<>();

    private static long touchedChunks;
    private static long completedChunks;
    private static long importedCells;
    private static long hostedWaterCells;
    private static long convertedBlocks;
    private static long playerScanCheckedChunks;
    private static long playerScanQueuedChunks;
    private static long playerScanPromotedChunks;
    private static long skippedUnloadedChunks;
    private static long droppedChunks;
    private static int lastPlayerScanCheckedChunks;
    private static int lastPlayerScanQueuedChunks;
    private static int lastPlayerScanPromotedChunks;
    private static int lastPlayerScanRadius;
    private static int lastPlayerScanServerViewDistance;
    private static int lastPlayerScanRequestedViewDistance;
    private static int nextPlayerScanTick;
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
        enqueue(level, pos, false);
    }

    private static boolean enqueue(ServerLevel level, ChunkPos pos, boolean priority) {
        if (!WaterSimulationConfig.ENABLE_CANONICAL_WORLD_SEEDING.get()) {
            return false;
        }
        MigrationTask task = new MigrationTask(level.dimension(), pos, 0, priority);
        if (QUEUED_KEYS.contains(task.key())) {
            if (priority) {
                promoteQueuedTask(task.key());
            }
            return false;
        }
        if (QUEUE.size() >= WaterSimulationConfig.automaticMigrationMaxQueuedChunks()) {
            if (!priority) {
                droppedChunks++;
                return false;
            }
            MigrationTask dropped = QUEUE.pollLast();
            if (dropped != null) {
                QUEUED_KEYS.remove(dropped.key());
                droppedChunks++;
            }
        }

        QUEUED_KEYS.add(task.key());
        if (priority) {
            QUEUE.offerFirst(task);
        } else {
            QUEUE.offerLast(task);
        }
        return true;
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
                || server.getPlayerList().getPlayerCount() <= 0) {
            lastTick = TickResult.EMPTY.withQueuedChunks(QUEUE.size());
            return lastTick;
        }
        enqueueLoadedPlayerChunks(server);
        if (QUEUE.isEmpty()) {
            lastTick = TickResult.EMPTY;
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
                && columnsRemaining > 0) {
            attemptsRemaining--;
            MigrationTask task = QUEUE.pollFirst();
            if (task == null) {
                break;
            }

            ServerLevel level = server.getLevel(task.dimension());
            if (level == null) {
                QUEUED_KEYS.remove(task.key());
                continue;
            }
            if (level.players().isEmpty()) {
                QUEUE.offerLast(task);
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
                conversionsRemaining = Math.max(0, conversionsRemaining - sliceStats.convertedBlocks());
            }
            chunksRemaining--;
            tickTouched++;

            if (slice.complete()) {
                QUEUED_KEYS.remove(task.key());
                tickCompleted++;
            } else {
                requeue(task.withNextColumnIndex(slice.nextColumnIndex()));
                if (sliceStats.scannedColumns() <= 0) {
                    break;
                }
            }
        }

        touchedChunks += tickTouched;
        completedChunks += tickCompleted;
        importedCells += tickStats.importedCells();
        hostedWaterCells += tickStats.hostedWaterCells();
        convertedBlocks += tickStats.convertedBlocks();
        lastTick = new TickResult(
                tickTouched,
                tickCompleted,
                tickStats.scannedColumns(),
                tickStats.importedCells(),
                tickStats.hostedWaterCells(),
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
        hostedWaterCells = 0L;
        convertedBlocks = 0L;
        playerScanCheckedChunks = 0L;
        playerScanQueuedChunks = 0L;
        playerScanPromotedChunks = 0L;
        skippedUnloadedChunks = 0L;
        droppedChunks = 0L;
        lastPlayerScanCheckedChunks = 0;
        lastPlayerScanQueuedChunks = 0;
        lastPlayerScanPromotedChunks = 0;
        lastPlayerScanRadius = 0;
        lastPlayerScanServerViewDistance = 0;
        lastPlayerScanRequestedViewDistance = 0;
        nextPlayerScanTick = 0;
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
                hostedWaterCells,
                convertedBlocks,
                WaterSimulationConfig.automaticMigrationPlayerChunkRadius(),
                WaterSimulationConfig.automaticMigrationPlayerScanIntervalTicks(),
                lastPlayerScanCheckedChunks,
                lastPlayerScanQueuedChunks,
                lastPlayerScanPromotedChunks,
                lastPlayerScanRadius,
                lastPlayerScanServerViewDistance,
                lastPlayerScanRequestedViewDistance,
                playerScanCheckedChunks,
                playerScanQueuedChunks,
                playerScanPromotedChunks,
                skippedUnloadedChunks,
                droppedChunks,
                lastTick
        );
    }

    private static void enqueueLoadedPlayerChunks(MinecraftServer server) {
        int playerRadius = WaterSimulationConfig.automaticMigrationPlayerChunkRadius();
        if (playerRadius <= 0) {
            lastPlayerScanCheckedChunks = 0;
            lastPlayerScanQueuedChunks = 0;
            lastPlayerScanPromotedChunks = 0;
            lastPlayerScanRadius = 0;
            lastPlayerScanServerViewDistance = 0;
            lastPlayerScanRequestedViewDistance = 0;
            return;
        }

        int currentTick = server.getTickCount();
        if (currentTick < nextPlayerScanTick) {
            return;
        }
        nextPlayerScanTick = currentTick + WaterSimulationConfig.automaticMigrationPlayerScanIntervalTicks();

        int checked = 0;
        int queued = 0;
        int promoted = 0;
        int maxRadius = 0;
        int maxServerViewDistance = Math.max(1, server.getPlayerList().getViewDistance());
        int maxRequestedViewDistance = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            ChunkPos center = player.chunkPosition();
            int requestedViewDistance = Math.max(1, player.requestedViewDistance());
            int radius = effectivePlayerScanRadius(server, requestedViewDistance, playerRadius);
            maxRadius = Math.max(maxRadius, radius);
            maxRequestedViewDistance = Math.max(maxRequestedViewDistance, requestedViewDistance);
            for (int chunkX = center.x - radius; chunkX <= center.x + radius; chunkX++) {
                for (int chunkZ = center.z - radius; chunkZ <= center.z + radius; chunkZ++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    if (chunk == null) {
                        continue;
                    }
                    checked++;
                    if (enqueue(level, chunk.getPos(), true)) {
                        queued++;
                    } else {
                        promoted++;
                    }
                }
            }
        }

        lastPlayerScanCheckedChunks = checked;
        lastPlayerScanQueuedChunks = queued;
        lastPlayerScanPromotedChunks = promoted;
        lastPlayerScanRadius = maxRadius;
        lastPlayerScanServerViewDistance = maxServerViewDistance;
        lastPlayerScanRequestedViewDistance = maxRequestedViewDistance;
        playerScanCheckedChunks += checked;
        playerScanQueuedChunks += queued;
        playerScanPromotedChunks += promoted;
    }

    private static int effectivePlayerScanRadius(
            MinecraftServer server,
            int requestedViewDistance,
            int configuredRadius
    ) {
        int serverViewDistance = Math.max(1, server.getPlayerList().getViewDistance());
        int visibleRadius = WaterSimulationConfig.automaticMigrationFollowsPlayerViewDistance()
                ? requestedViewDistance + WaterSimulationConfig.automaticMigrationViewDistancePaddingChunks()
                : configuredRadius;
        return Math.max(1, Math.min(serverViewDistance, Math.max(configuredRadius, visibleRadius)));
    }

    private static void promoteQueuedTask(String key) {
        Iterator<MigrationTask> iterator = QUEUE.iterator();
        while (iterator.hasNext()) {
            MigrationTask queued = iterator.next();
            if (queued.key().equals(key)) {
                iterator.remove();
                QUEUE.offerFirst(queued.asPriority());
                return;
            }
        }
    }

    private static void requeue(MigrationTask task) {
        if (task.priority()) {
            QUEUE.offerFirst(task);
        } else {
            QUEUE.offerLast(task);
        }
    }

    private record MigrationTask(ResourceKey<Level> dimension, ChunkPos pos, int nextColumnIndex, boolean priority) {
        private String key() {
            return dimension.location() + ":" + pos.x + ":" + pos.z;
        }

        private MigrationTask withNextColumnIndex(int nextColumnIndex) {
            return new MigrationTask(dimension, pos, nextColumnIndex, priority);
        }

        private MigrationTask asPriority() {
            return priority ? this : new MigrationTask(dimension, pos, nextColumnIndex, true);
        }
    }

    /** Per-tick migration counters. */
    public record TickResult(
            int touchedChunks,
            int completedChunks,
            int scannedColumns,
            int importedCells,
            int hostedWaterCells,
            int convertedBlocks,
            int skippedUnloadedChunks,
            int queuedChunks
    ) {
        public static final TickResult EMPTY = new TickResult(0, 0, 0, 0, 0, 0, 0, 0);

        private TickResult withQueuedChunks(int queuedChunks) {
            return new TickResult(touchedChunks, completedChunks, scannedColumns, importedCells, hostedWaterCells,
                    convertedBlocks, skippedUnloadedChunks, queuedChunks);
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
            long hostedWaterCells,
            long convertedBlocks,
            int playerScanRadius,
            int playerScanIntervalTicks,
            int lastPlayerScanCheckedChunks,
            int lastPlayerScanQueuedChunks,
            int lastPlayerScanPromotedChunks,
            int lastPlayerScanRadius,
            int lastPlayerScanServerViewDistance,
            int lastPlayerScanRequestedViewDistance,
            long playerScanCheckedChunks,
            long playerScanQueuedChunks,
            long playerScanPromotedChunks,
            long skippedUnloadedChunks,
            long droppedChunks,
            TickResult lastTick
    ) {
    }
}
