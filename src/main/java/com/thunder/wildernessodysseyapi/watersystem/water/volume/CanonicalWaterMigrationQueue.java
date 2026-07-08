package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import com.thunder.wildernessodysseyapi.capabilities.ChunkDataCapability;
import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Defers vanilla-to-Wilderness water migration away from unsafe chunk-load callbacks.
 *
 * <p>Chunk load and chunk watch callbacks sit on Minecraft's streaming path.
 * Rewriting ocean blocks there can make chunk loading hitch, so callbacks only
 * enqueue or promote chunk positions. Server ticks later process small slices
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
    private static long visibleFinalizationTouchedChunks;
    private static long visibleFinalizationCompletedChunks;
    private static long visibleFinalizationImportedCells;
    private static long visibleFinalizationHostedWaterCells;
    private static long visibleFinalizationConvertedBlocks;
    private static long visibleFinalizationBudgetMisses;
    private static long visibleFinalizationSkippedFinalizedChunks;
    private static long skippedFinalizedChunks;
    private static long skippedUnloadedChunks;
    private static long droppedChunks;
    private static int lastPlayerScanCheckedChunks;
    private static int lastPlayerScanQueuedChunks;
    private static int lastPlayerScanPromotedChunks;
    private static int lastPlayerScanRadius;
    private static int lastPlayerScanServerViewDistance;
    private static int lastPlayerScanRequestedViewDistance;
    private static int visibleFinalizationTick = Integer.MIN_VALUE;
    private static int visibleFinalizationChunksRemaining;
    private static int visibleFinalizationColumnsRemaining;
    private static int visibleFinalizationConversionsRemaining;
    private static int nextPlayerScanTick;
    private static TickResult lastTick = TickResult.EMPTY;
    private static TickResult lastVisibleFinalization = TickResult.EMPTY;

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

    /**
     * Queues a loaded chunk without mutating it from the load callback.
     *
     * <p>Chunks loaded during spawn preparation are queued normally. Chunks
     * loaded while players are active are promoted to the front of the migration
     * queue, preserving the "take over nearby water first" behavior without
     * blocking the chunk loader.</p>
     */
    public static synchronized void queueLoadedChunkForFinalization(ServerLevel level, LevelChunk chunk) {
        if (!WildernessWaterRules.isEnabled(level)) {
            return;
        }
        if (isChunkWaterFinalized(chunk)) {
            skippedFinalizedChunks++;
            return;
        }
        boolean priority = level.getServer().getPlayerList().getPlayerCount() > 0;
        enqueue(level, chunk.getPos(), priority);
        lastVisibleFinalization = TickResult.EMPTY.withQueuedChunks(QUEUE.size());
    }

    /**
     * Promotes a chunk that is about to be watched by a player.
     *
     * <p>The actual scan and block conversion still happen from the server tick
     * migration queue. This avoids synchronous work during chunk tracking while
     * keeping visible water ahead of older background tasks.</p>
     */
    public static synchronized void queueVisibleChunkForFinalization(ServerLevel level, LevelChunk chunk) {
        if (!WildernessWaterRules.isEnabled(level)) {
            lastVisibleFinalization = TickResult.EMPTY.withQueuedChunks(QUEUE.size());
            return;
        }
        if (isChunkWaterFinalized(chunk)) {
            visibleFinalizationSkippedFinalizedChunks++;
            lastVisibleFinalization = TickResult.EMPTY.withQueuedChunks(QUEUE.size());
            return;
        }
        enqueue(level, chunk.getPos(), true);
        lastVisibleFinalization = TickResult.EMPTY.withQueuedChunks(QUEUE.size());
    }

    private static boolean enqueue(ServerLevel level, ChunkPos pos, boolean priority) {
        if (!WildernessWaterRules.isEnabled(level) || !WaterSimulationConfig.ENABLE_CANONICAL_WORLD_SEEDING.get()) {
            return false;
        }
        LevelChunk loadedChunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (loadedChunk != null && isChunkWaterFinalized(loadedChunk)) {
            skippedFinalizedChunks++;
            return false;
        }
        MigrationTask task = new MigrationTask(level.dimension(), pos, 0, priority);
        if (QUEUED_KEYS.contains(task.key())) {
            if (priority) {
                promoteQueuedTask(task.key());
            }
            return false;
        }
        return queueTask(task);
    }

    /**
     * Performs a bounded finalization pass for a chunk about to be watched by a player.
     *
     * <p>This hook runs after world generation has produced normal terrain, but
     * before the queued background migration would normally get around to the
     * chunk. It rewrites plain {@code minecraft:water} to Wilderness water under
     * per-tick budgets and priority-queues any unfinished columns.</p>
     */
    public static synchronized TickResult finalizeVisibleChunk(ServerLevel level, LevelChunk chunk) {
        if (!WildernessWaterRules.isEnabled(level) || !WaterSimulationConfig.ENABLE_CANONICAL_WORLD_SEEDING.get()) {
            lastVisibleFinalization = TickResult.EMPTY.withQueuedChunks(QUEUE.size());
            return lastVisibleFinalization;
        }

        MigrationTask task = removeQueuedTask(new MigrationTask(level.dimension(), chunk.getPos(), 0, true).key());
        if (isChunkWaterFinalized(chunk)) {
            visibleFinalizationSkippedFinalizedChunks++;
            lastVisibleFinalization = TickResult.EMPTY.withQueuedChunks(QUEUE.size());
            return lastVisibleFinalization;
        }
        if (task == null) {
            task = new MigrationTask(level.dimension(), chunk.getPos(), 0, true);
        } else {
            task = task.asPriority();
        }

        if (!WaterSimulationConfig.visibleChunkWaterFinalizationEnabled()) {
            queueTask(task);
            lastVisibleFinalization = TickResult.EMPTY.withQueuedChunks(QUEUE.size());
            return lastVisibleFinalization;
        }

        refreshVisibleFinalizationBudget(level.getServer());
        boolean allowBlockConversion = WaterSimulationConfig.automaticWaterMigrationEnabled()
                && WaterSimulationConfig.convertSeededWorldWaterToWilderness();
        if (visibleFinalizationChunksRemaining <= 0
                || visibleFinalizationColumnsRemaining <= 0
                || (allowBlockConversion && visibleFinalizationConversionsRemaining <= 0)) {
            queueTask(task);
            visibleFinalizationBudgetMisses++;
            lastVisibleFinalization = TickResult.EMPTY.withQueuedChunks(QUEUE.size());
            return lastVisibleFinalization;
        }

        CanonicalWaterSeeder.SeedSlice slice = CanonicalWaterSeeder.seedChunkSlice(
                level,
                chunk,
                WaterSimulationConfig.worldSeedMaxColumnDepth(),
                task.nextColumnIndex(),
                visibleFinalizationColumnsRemaining,
                allowBlockConversion ? visibleFinalizationConversionsRemaining : Integer.MAX_VALUE,
                allowBlockConversion
        );
        CanonicalWaterSeeder.SeedStats stats = slice.stats();
        visibleFinalizationChunksRemaining--;
        visibleFinalizationColumnsRemaining = Math.max(0,
                visibleFinalizationColumnsRemaining - stats.scannedColumns());
        if (allowBlockConversion) {
            visibleFinalizationConversionsRemaining = Math.max(0,
                    visibleFinalizationConversionsRemaining - stats.convertedBlocks());
        }

        int completed = slice.complete() ? 1 : 0;
        visibleFinalizationTouchedChunks++;
        visibleFinalizationCompletedChunks += completed;
        visibleFinalizationImportedCells += stats.importedCells();
        visibleFinalizationHostedWaterCells += stats.hostedWaterCells();
        visibleFinalizationConvertedBlocks += stats.convertedBlocks();
        if (!slice.complete()) {
            queueTask(task.withNextColumnIndex(slice.nextColumnIndex()).asPriority());
        } else {
            markChunkWaterFinalized(chunk);
        }

        lastVisibleFinalization = new TickResult(
                1,
                completed,
                stats.scannedColumns(),
                stats.importedCells(),
                stats.hostedWaterCells(),
                stats.convertedBlocks(),
                0,
                QUEUE.size()
        );
        return lastVisibleFinalization;
    }

    /**
     * Finalizes starter-bunker water before the first player sees the world.
     *
     * <p>This is the one deliberately blocking water takeover path. It runs
     * only during initial spawn/bunker creation, may synchronously load a
     * bounded chunk radius, and then uses the same seeder/finalized markers as
     * normal migration. Any chunks not completed before the soft timeout are
     * priority-queued for the regular budgeted tick path.</p>
     *
     * @param level overworld being prepared
     * @param center block center of the starter bunker area
     * @return summary for startup logs and diagnostics
     */
    public static synchronized SpawnPreFinalizationResult preFinalizeSpawnArea(
            ServerLevel level,
            BlockPos center
    ) {
        if (!WildernessWaterRules.isEnabled(level) || !WaterSimulationConfig.spawnWaterPreFinalizationEnabled()) {
            return SpawnPreFinalizationResult.disabled(QUEUE.size());
        }

        int radius = WaterSimulationConfig.spawnWaterPreFinalizationRadiusChunks();
        int maxChunks = WaterSimulationConfig.spawnWaterPreFinalizationMaxChunks();
        int timeoutMs = WaterSimulationConfig.spawnWaterPreFinalizationTimeoutMs();
        boolean allowBlockConversion = WaterSimulationConfig.convertSeededWorldWaterToWilderness();
        List<ChunkPos> candidates = spawnAreaChunkCandidates(new ChunkPos(center), radius, maxChunks);
        long startNanos = System.nanoTime();
        long timeoutNanos = timeoutMs <= 0 ? Long.MAX_VALUE : timeoutMs * 1_000_000L;

        int touched = 0;
        int completed = 0;
        int skippedFinalized = 0;
        int queuedUnfinished = 0;
        boolean timedOut = false;
        CanonicalWaterSeeder.SeedStats totalStats = CanonicalWaterSeeder.SeedStats.EMPTY;

        for (int index = 0; index < candidates.size(); index++) {
            if (timedOut(startNanos, timeoutNanos, touched)) {
                timedOut = true;
                queuedUnfinished += queueRemainingSpawnChunks(level, candidates, index);
                break;
            }

            ChunkPos chunkPos = candidates.get(index);
            removeQueuedTask(key(level.dimension(), chunkPos));
            LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
            if (isChunkWaterFinalized(chunk)) {
                skippedFinalized++;
                skippedFinalizedChunks++;
                continue;
            }

            CanonicalWaterSeeder.SeedSlice slice = CanonicalWaterSeeder.seedChunkSlice(
                    level,
                    chunk,
                    WaterSimulationConfig.worldSeedMaxColumnDepth(),
                    0,
                    16 * 16,
                    allowBlockConversion ? Integer.MAX_VALUE : 0,
                    allowBlockConversion
            );
            CanonicalWaterSeeder.SeedStats sliceStats = slice.stats().countedChunk();
            totalStats = totalStats.plus(sliceStats);
            touched++;
            if (slice.complete()) {
                markChunkWaterFinalized(chunk);
                completed++;
            } else if (queueTask(new MigrationTask(level.dimension(), chunkPos, slice.nextColumnIndex(), true))) {
                queuedUnfinished++;
            }
        }

        touchedChunks += touched;
        completedChunks += completed;
        importedCells += totalStats.importedCells();
        hostedWaterCells += totalStats.hostedWaterCells();
        convertedBlocks += totalStats.convertedBlocks();
        lastTick = new TickResult(
                touched,
                completed,
                totalStats.scannedColumns(),
                totalStats.importedCells(),
                totalStats.hostedWaterCells(),
                totalStats.convertedBlocks(),
                0,
                QUEUE.size()
        );

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        return new SpawnPreFinalizationResult(
                true,
                radius,
                candidates.size(),
                touched,
                completed,
                skippedFinalized,
                queuedUnfinished,
                totalStats.scannedColumns(),
                totalStats.importedCells(),
                totalStats.hostedWaterCells(),
                totalStats.convertedBlocks(),
                elapsedMs,
                timedOut,
                QUEUE.size()
        );
    }

    private static boolean queueTask(MigrationTask task) {
        if (QUEUED_KEYS.contains(task.key())) {
            if (task.priority()) {
                promoteQueuedTask(task.key());
            }
            return false;
        }
        if (QUEUE.size() >= WaterSimulationConfig.automaticMigrationMaxQueuedChunks()) {
            if (!task.priority()) {
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
        if (task.priority()) {
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
        if (!WaterSimulationConfig.wildernessWaterEnabled()
                || !WaterSimulationConfig.ENABLE_CANONICAL_WORLD_SEEDING.get()
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
        int priorityTouched = 0;
        int priorityCompleted = 0;
        CanonicalWaterSeeder.SeedStats tickStats = CanonicalWaterSeeder.SeedStats.EMPTY;
        CanonicalWaterSeeder.SeedStats priorityStats = CanonicalWaterSeeder.SeedStats.EMPTY;

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
            if (!WildernessWaterRules.isEnabled(level)) {
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
            if (isChunkWaterFinalized(chunk)) {
                QUEUED_KEYS.remove(task.key());
                skippedFinalizedChunks++;
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
            if (task.priority()) {
                priorityTouched++;
                priorityStats = priorityStats.plus(sliceStats);
            }
            columnsRemaining -= sliceStats.scannedColumns();
            if (allowBlockConversion) {
                conversionsRemaining = Math.max(0, conversionsRemaining - sliceStats.convertedBlocks());
            }
            chunksRemaining--;
            tickTouched++;

            if (slice.complete()) {
                QUEUED_KEYS.remove(task.key());
                markChunkWaterFinalized(chunk);
                tickCompleted++;
                if (task.priority()) {
                    priorityCompleted++;
                }
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
        if (priorityTouched > 0) {
            visibleFinalizationTouchedChunks += priorityTouched;
            visibleFinalizationCompletedChunks += priorityCompleted;
            visibleFinalizationImportedCells += priorityStats.importedCells();
            visibleFinalizationHostedWaterCells += priorityStats.hostedWaterCells();
            visibleFinalizationConvertedBlocks += priorityStats.convertedBlocks();
            lastVisibleFinalization = new TickResult(
                    priorityTouched,
                    priorityCompleted,
                    priorityStats.scannedColumns(),
                    priorityStats.importedCells(),
                    priorityStats.hostedWaterCells(),
                    priorityStats.convertedBlocks(),
                    0,
                    QUEUE.size()
            );
        } else {
            lastVisibleFinalization = TickResult.EMPTY.withQueuedChunks(QUEUE.size());
        }
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
        visibleFinalizationTouchedChunks = 0L;
        visibleFinalizationCompletedChunks = 0L;
        visibleFinalizationImportedCells = 0L;
        visibleFinalizationHostedWaterCells = 0L;
        visibleFinalizationConvertedBlocks = 0L;
        visibleFinalizationBudgetMisses = 0L;
        visibleFinalizationSkippedFinalizedChunks = 0L;
        skippedFinalizedChunks = 0L;
        skippedUnloadedChunks = 0L;
        droppedChunks = 0L;
        lastPlayerScanCheckedChunks = 0;
        lastPlayerScanQueuedChunks = 0;
        lastPlayerScanPromotedChunks = 0;
        lastPlayerScanRadius = 0;
        lastPlayerScanServerViewDistance = 0;
        lastPlayerScanRequestedViewDistance = 0;
        visibleFinalizationTick = Integer.MIN_VALUE;
        visibleFinalizationChunksRemaining = 0;
        visibleFinalizationColumnsRemaining = 0;
        visibleFinalizationConversionsRemaining = 0;
        nextPlayerScanTick = 0;
        lastTick = TickResult.EMPTY;
        lastVisibleFinalization = TickResult.EMPTY;
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
                WaterSimulationConfig.visibleChunkWaterFinalizationEnabled(),
                visibleFinalizationTouchedChunks,
                visibleFinalizationCompletedChunks,
                visibleFinalizationImportedCells,
                visibleFinalizationHostedWaterCells,
                visibleFinalizationConvertedBlocks,
                visibleFinalizationBudgetMisses,
                visibleFinalizationSkippedFinalizedChunks,
                lastVisibleFinalization,
                skippedFinalizedChunks,
                skippedUnloadedChunks,
                droppedChunks,
                lastTick
        );
    }

    /**
     * Returns whether a loaded chunk has already completed water finalization.
     *
     * <p>Diagnostics use this to explain why a chunk is no longer queued, and
     * migration scheduling uses it to avoid rescanning chunks whose generated
     * plain water has already been handed to Wilderness authority.</p>
     */
    public static boolean isChunkWaterFinalized(LevelChunk chunk) {
        return chunk.getData(ModAttachments.CHUNK_DATA)
                .isWaterFinalized(WildernessWaterAuthority.CURRENT_WATER_SYSTEM_VERSION);
    }

    /** Returns whether the migration queue currently contains this chunk. */
    public static synchronized boolean isQueued(ServerLevel level, ChunkPos pos) {
        return QUEUED_KEYS.contains(key(level.dimension(), pos));
    }

    private static void markChunkWaterFinalized(LevelChunk chunk) {
        ChunkDataCapability chunkData = chunk.getData(ModAttachments.CHUNK_DATA);
        if (!chunkData.isWaterFinalized(WildernessWaterAuthority.CURRENT_WATER_SYSTEM_VERSION)) {
            chunkData.markWaterFinalized(WildernessWaterAuthority.CURRENT_WATER_SYSTEM_VERSION);
        }
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
            if (!WildernessWaterRules.isEnabled(level)) {
                continue;
            }
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
                    if (isChunkWaterFinalized(chunk)) {
                        continue;
                    }
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

    private static MigrationTask removeQueuedTask(String key) {
        Iterator<MigrationTask> iterator = QUEUE.iterator();
        while (iterator.hasNext()) {
            MigrationTask queued = iterator.next();
            if (queued.key().equals(key)) {
                iterator.remove();
                QUEUED_KEYS.remove(key);
                return queued;
            }
        }
        QUEUED_KEYS.remove(key);
        return null;
    }

    private static void refreshVisibleFinalizationBudget(MinecraftServer server) {
        int currentTick = server.getTickCount();
        if (visibleFinalizationTick == currentTick) {
            return;
        }
        visibleFinalizationTick = currentTick;
        visibleFinalizationChunksRemaining = WaterSimulationConfig.visibleFinalizationChunksPerTick();
        visibleFinalizationColumnsRemaining = WaterSimulationConfig.visibleFinalizationColumnsPerTick();
        visibleFinalizationConversionsRemaining = WaterSimulationConfig.visibleFinalizationConvertedBlocksPerTick();
    }

    private static void requeue(MigrationTask task) {
        if (task.priority()) {
            QUEUE.offerFirst(task);
        } else {
            QUEUE.offerLast(task);
        }
    }

    private static List<ChunkPos> spawnAreaChunkCandidates(ChunkPos center, int radius, int maxChunks) {
        int boundedRadius = Math.max(0, radius);
        int boundedMaxChunks = Math.max(1, maxChunks);
        List<ChunkPos> candidates = new ArrayList<>((boundedRadius * 2 + 1) * (boundedRadius * 2 + 1));
        for (int chunkX = center.x - boundedRadius; chunkX <= center.x + boundedRadius; chunkX++) {
            for (int chunkZ = center.z - boundedRadius; chunkZ <= center.z + boundedRadius; chunkZ++) {
                candidates.add(new ChunkPos(chunkX, chunkZ));
            }
        }
        candidates.sort(Comparator
                .comparingInt((ChunkPos pos) -> distanceSquared(center, pos))
                .thenComparingInt(pos -> Math.abs(pos.x - center.x) + Math.abs(pos.z - center.z))
                .thenComparingInt(pos -> pos.x)
                .thenComparingInt(pos -> pos.z));
        if (candidates.size() <= boundedMaxChunks) {
            return candidates;
        }
        return new ArrayList<>(candidates.subList(0, boundedMaxChunks));
    }

    private static int distanceSquared(ChunkPos center, ChunkPos pos) {
        int deltaX = pos.x - center.x;
        int deltaZ = pos.z - center.z;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    private static boolean timedOut(long startNanos, long timeoutNanos, int touchedChunks) {
        return touchedChunks > 0 && System.nanoTime() - startNanos >= timeoutNanos;
    }

    private static int queueRemainingSpawnChunks(ServerLevel level, List<ChunkPos> candidates, int startIndex) {
        int queued = 0;
        for (int index = startIndex; index < candidates.size(); index++) {
            if (enqueue(level, candidates.get(index), true)) {
                queued++;
            }
        }
        return queued;
    }

    private static String key(ResourceKey<Level> dimension, ChunkPos pos) {
        return dimension.location() + ":" + pos.x + ":" + pos.z;
    }

    private record MigrationTask(ResourceKey<Level> dimension, ChunkPos pos, int nextColumnIndex, boolean priority) {
        private String key() {
            return CanonicalWaterMigrationQueue.key(dimension, pos);
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
            boolean visibleFinalizationEnabled,
            long visibleFinalizationTouchedChunks,
            long visibleFinalizationCompletedChunks,
            long visibleFinalizationImportedCells,
            long visibleFinalizationHostedWaterCells,
            long visibleFinalizationConvertedBlocks,
            long visibleFinalizationBudgetMisses,
            long visibleFinalizationSkippedFinalizedChunks,
            TickResult lastVisibleFinalization,
            long skippedFinalizedChunks,
            long skippedUnloadedChunks,
            long droppedChunks,
            TickResult lastTick
    ) {
    }

    /** Summary returned by the blocking starter-bunker water takeover pass. */
    public record SpawnPreFinalizationResult(
            boolean enabled,
            int radiusChunks,
            int candidateChunks,
            int touchedChunks,
            int completedChunks,
            int skippedFinalizedChunks,
            int queuedUnfinishedChunks,
            int scannedColumns,
            int importedCells,
            int hostedWaterCells,
            int convertedBlocks,
            long elapsedMs,
            boolean timedOut,
            int queuedChunks
    ) {
        private static SpawnPreFinalizationResult disabled(int queuedChunks) {
            return new SpawnPreFinalizationResult(
                    false,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0L,
                    false,
                    queuedChunks
            );
        }
    }
}
