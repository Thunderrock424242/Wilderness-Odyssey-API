package com.thunder.wildernessodysseyapi.ModPackPatches.worldupgrade;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.capabilities.ChunkDataCapability;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import static com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER;
import static com.thunder.wildernessodysseyapi.core.ModConstants.VERSION;

/**
 * Central queue + executor for chunk migration tasks.
 */
public final class WorldUpgradeManager {
    public static final int TARGET_VERSION = 1;
    private static final int TASKS_PER_TICK = 1;

    private static final Queue<ChunkTask> QUEUE = new ArrayDeque<>();
    private static final Set<String> QUEUED_KEYS = new HashSet<>();
    private static final List<WorldMigration> MIGRATIONS = List.of(new LegacyBlockReplacementMigration());
    private static final Map<Integer, WorldMigration> MIGRATION_CHAIN = MIGRATIONS.stream()
            .collect(Collectors.toMap(WorldMigration::fromVersion, migration -> migration));

    private WorldUpgradeManager() {
    }

    public static void onServerStarting(MinecraftServer server) {
        QUEUE.clear();
        QUEUED_KEYS.clear();

        WorldUpgradeSavedData state = WorldUpgradeSavedData.get(server);
        if (state.shouldRunForPackVersion(VERSION)) {
            LOGGER.info("Detected modpack version change ({} -> {}). Starting world upgrade queue.", state.getLastPackVersion(), VERSION);
            state.resetCounters();
            state.markPackVersionProcessed(VERSION);
            state.setRunning(true);
        } else {
            state.setRunning(false);
        }
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

    public static WorldUpgradeStatus status(MinecraftServer server) {
        WorldUpgradeSavedData state = WorldUpgradeSavedData.get(server);
        return new WorldUpgradeStatus(
                state.isRunning(),
                state.getTargetVersion(),
                state.getLastPackVersion(),
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
                LevelChunk chunk = level.getChunk(task.pos().x, task.pos().z);
                migrated = migrateChunk(level, chunk, state.getTargetVersion());
            } catch (Exception exception) {
                failed = true;
                LOGGER.error("World upgrade failed at {} {}", task.dimension().location(), task.pos(), exception);
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
                LOGGER.warn("No migration found from version {} in chunk {}", currentVersion, chunk.getPos());
                return migrated;
            }
            if (!migration.apply(new MigrationContext(level, chunk, chunkData))) {
                return migrated;
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
                                     String trackedPackVersion,
                                     int queuedChunks,
                                     long processedChunks,
                                     long migratedChunks,
                                     long failedChunks) {
    }
}
