package com.thunder.wildernessodysseyapi.worldupgrade;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.capabilities.ChunkDataCapability;
import com.thunder.wildernessodysseyapi.util.ChunkErrorReporter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER;
import static com.thunder.wildernessodysseyapi.core.ModConstants.currentVersion;

/**
 * Central queue + executor for chunk migration tasks.
 */
public final class WorldUpgradeManager {
    public static final int TARGET_VERSION = 1;
    private static final int TASKS_PER_TICK = 1;

    private static final Queue<ChunkTask> QUEUE = new ArrayDeque<>();
    private static final Set<String> QUEUED_KEYS = new HashSet<>();
    private static final Map<String, ChunkTask> FAILED_TASKS = new LinkedHashMap<>();
    private static final List<WorldMigration> MIGRATIONS = List.of(new LegacyBlockReplacementMigration());
    private static final Map<Integer, WorldMigration> MIGRATION_CHAIN = MIGRATIONS.stream()
            .collect(Collectors.toMap(WorldMigration::fromVersion, migration -> migration));

    private WorldUpgradeManager() {
    }

    public static void onServerStarting(MinecraftServer server) {
        QUEUE.clear();
        QUEUED_KEYS.clear();
        FAILED_TASKS.clear();

        WorldUpgradeSavedData state = WorldUpgradeSavedData.get(server);
        importLegacyWorldLabel(server, state);
        state.advanceTargetVersion(TARGET_VERSION);
        String packVersion = currentVersion();
        if (state.shouldRunForPackVersion(packVersion)) {
            LOGGER.info("Detected modpack version change ({} -> {}). Starting world upgrade queue.",
                    state.getLastPackVersion(), packVersion);
            state.beginPackUpgrade(packVersion);
        } else if (state.hasPendingPackVersion()) {
            LOGGER.info("World upgrade for pack version {} is {} and will resume from per-chunk versions.",
                    state.getPendingPackVersion(),
                    state.isRunning() ? "running" : "paused");
        }
    }

    // Imports the deprecated display label once, but never treats it as proof of a completed migration.
    private static void importLegacyWorldLabel(MinecraftServer server, WorldUpgradeSavedData state) {
        if (state.isLegacyImportComplete()) {
            return;
        }
        Path legacyPath = server.getWorldPath(LevelResource.ROOT).resolve("world_version.json");
        String legacyLabel = "";
        if (Files.exists(legacyPath)) {
            try (var reader = Files.newBufferedReader(legacyPath)) {
                var root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.has("world_version") && root.get("world_version").isJsonPrimitive()) {
                    legacyLabel = root.get("world_version").getAsString();
                }
            } catch (Exception exception) {
                LOGGER.warn("Could not import deprecated world label from {}. The authoritative upgrade state is unaffected.",
                        legacyPath, exception);
            }
        }
        state.recordLegacyWorldLabel(legacyLabel);
        if (!legacyLabel.isBlank()) {
            LOGGER.info("Imported deprecated world label '{}' as non-authoritative compatibility metadata.", legacyLabel);
        }
    }

    /** Releases process-wide task references when the active server stops. */
    public static void onServerStopping() {
        QUEUE.clear();
        QUEUED_KEYS.clear();
        FAILED_TASKS.clear();
    }

    public static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        ChunkDataCapability chunkData = chunk.getData(ModAttachments.CHUNK_DATA);
        WorldUpgradeSavedData state = WorldUpgradeSavedData.get(level.getServer());
        if (!state.isRunning() || chunkData.getUpgradeVersion() >= state.getTargetVersion()) {
            return;
        }
        enqueue(level, chunk.getPos());
    }

    public static void start(MinecraftServer server) {
        WorldUpgradeSavedData.get(server).setRunning(true);
    }

    public static void pause(MinecraftServer server) {
        WorldUpgradeSavedData.get(server).setRunning(false);
    }

    /**
     * Requeues failures retained by the current server session and clears the
     * unresolved-failure counter so successful retries can permit completion.
     *
     * <p>After a restart, task identities are reconstructed from authoritative
     * per-chunk versions as chunks load. Clearing the prior counter therefore
     * acknowledges the old report without marking any chunk as migrated.</p>
     *
     * @return number of current-session tasks requeued
     */
    public static int retryFailed(MinecraftServer server) {
        int requeued = 0;
        for (ChunkTask task : FAILED_TASKS.values()) {
            if (QUEUED_KEYS.add(task.key())) {
                QUEUE.offer(task);
                requeued++;
            }
        }
        FAILED_TASKS.clear();
        WorldUpgradeSavedData state = WorldUpgradeSavedData.get(server);
        state.resetFailedChunks();
        state.setRunning(true);
        return requeued;
    }

    /**
     * Commits a pending pack rollout after every currently queued task
     * succeeded. Per-chunk versions still protect chunks discovered later.
     *
     * @return {@code true} when the pending pack version was committed
     */
    public static boolean complete(MinecraftServer server) {
        WorldUpgradeSavedData state = WorldUpgradeSavedData.get(server);
        if (!canComplete(state.isRunning(), QUEUE.size(), state.getFailedChunks(), state.hasPendingPackVersion())) {
            return false;
        }
        return state.completePendingPackUpgrade();
    }

    /** Keeps a paused or unresolved rollout from being advertised as complete. */
    static boolean canComplete(boolean running, int queuedChunks, long failedChunks, boolean hasPendingVersion) {
        return running && queuedChunks == 0 && failedChunks == 0 && hasPendingVersion;
    }

    public static WorldUpgradeStatus status(MinecraftServer server) {
        WorldUpgradeSavedData state = WorldUpgradeSavedData.get(server);
        return new WorldUpgradeStatus(
                state.isRunning(),
                state.getTargetVersion(),
                state.getLastPackVersion(),
                state.getPendingPackVersion(),
                QUEUE.size(),
                state.getProcessedChunks(),
                state.getMigratedChunks(),
                state.getFailedChunks()
        );
    }

    public static int runTick(MinecraftServer server) {
        WorldUpgradeSavedData state = WorldUpgradeSavedData.get(server);
        if (!state.isRunning()) {
            return 0;
        }

        int processed = 0;
        for (int i = 0; i < TASKS_PER_TICK; i++) {
            ChunkTask task = QUEUE.poll();
            if (task == null) {
                break;
            }
            QUEUED_KEYS.remove(task.key());
            boolean migrated = false;
            boolean failed = false;
            try {
                ServerLevel level = server.getLevel(task.dimension());
                if (level == null) {
                    continue;
                }

                // THE FIX: Use getChunkNow to avoid forcing synchronous I/O loading
                LevelChunk chunk = level.getChunkSource().getChunkNow(task.pos().x, task.pos().z);
                if (chunk == null) {
                    continue; // The chunk unloaded before we could process it. Skip it safely!
                }

                migrated = migrateChunk(level, chunk, state.getTargetVersion());
            } catch (Exception exception) {
                failed = true;
                FAILED_TASKS.put(task.key(), task);
                ServerLevel failedLevel = server.getLevel(task.dimension());
                if (failedLevel != null) {
                    ChunkErrorReporter.reportChunkError("upgrade", failedLevel, task.pos(), exception);
                } else {
                    LOGGER.error("World upgrade failed at {} {}", task.dimension().location(), task.pos(), exception);
                }
            }
            state.onChunkProcessed(migrated, failed);
            processed++;
        }
        return processed;
    }

    private static boolean migrateChunk(ServerLevel level, LevelChunk chunk, int targetVersion) {
        ChunkDataCapability chunkData = chunk.getData(ModAttachments.CHUNK_DATA);
        int currentVersion = chunkData.getUpgradeVersion();
        if (currentVersion >= targetVersion) {
            return false;
        }

        boolean migrated = false;
        while (currentVersion < targetVersion) {
            WorldMigration migration = MIGRATION_CHAIN.get(currentVersion);
            if (migration == null) {
                throw new IllegalStateException(
                        "No migration found from version " + currentVersion + " for chunk " + chunk.getPos()
                );
            }
            if (!migration.apply(new MigrationContext(level, chunk, chunkData))) {
                throw new IllegalStateException(
                        "Migration " + migration.id() + " did not complete for chunk " + chunk.getPos()
                );
            }
            currentVersion = migration.toVersion();
            chunkData.setUpgradeVersion(currentVersion);
            migrated = true;
        }

        return migrated;
    }

    public static void enqueue(ServerLevel level, ChunkPos pos) {
        ChunkTask task = new ChunkTask(level.dimension(), pos);
        if (!QUEUED_KEYS.add(task.key())) {
            return;
        }
        QUEUE.offer(task);
    }

    private record ChunkTask(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                             ChunkPos pos) {
        private String key() {
            return dimension.location() + ":" + pos.x + ":" + pos.z;
        }
    }

    public record WorldUpgradeStatus(boolean running,
                                     int targetVersion,
                                     String completedPackVersion,
                                     String pendingPackVersion,
                                     int queuedChunks,
                                     long processedChunks,
                                     long migratedChunks,
                                     long failedChunks) {
    }
}
