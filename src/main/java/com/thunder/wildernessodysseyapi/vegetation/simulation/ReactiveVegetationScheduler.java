package com.thunder.wildernessodysseyapi.vegetation.simulation;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.vegetation.api.ReactiveVegetationServices;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationClimateState;
import com.thunder.wildernessodysseyapi.vegetation.config.VegetationConfig;
import com.thunder.wildernessodysseyapi.vegetation.state.ReactiveVegetationState;
import com.thunder.wildernessodysseyapi.weather.api.SeasonalClimateState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherQuery;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WeatherServices;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Time-slices regional climate and registered-plant work across loaded chunks.
 *
 * <p>Chunk lifecycle events own the due queue. Each tick drains only due entries
 * under a calculated cap, uses {@code getChunkNow}, and reschedules from the
 * current tick. Unloaded chunks have no queue entry and receive no catch-up
 * simulation after loading.</p>
 */
public final class ReactiveVegetationScheduler {

    private static final Map<ServerLevel, LevelRuntime> RUNTIMES = new ConcurrentHashMap<>();

    private ReactiveVegetationScheduler() {
    }

    /** Adds one promoted server chunk to the staggered due queue. */
    public static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        int interval = VegetationConfig.UPDATE_INTERVAL.get();
        RUNTIMES.computeIfAbsent(level, ignored -> new LevelRuntime())
                .add(chunk.getPos().toLong(), level.getGameTime(), interval);
    }

    /** Removes one chunk immediately so an unloaded region cannot simulate. */
    public static void onChunkUnload(ServerLevel level, LevelChunk chunk) {
        LevelRuntime runtime = RUNTIMES.get(level);
        if (runtime != null) {
            runtime.remove(chunk.getPos().toLong());
        }
    }

    /** Advances due loaded chunks under the per-level work cap. */
    public static void tickLevel(ServerLevel level) {
        LevelRuntime runtime = RUNTIMES.computeIfAbsent(level, ignored -> new LevelRuntime());
        long gameTime = level.getGameTime();
        int interval = VegetationConfig.UPDATE_INTERVAL.get();
        runtime.ensureInterval(interval, gameTime);
        if (!VegetationConfig.VEGETATION_UPDATES_ENABLED.get() || level.players().isEmpty()) {
            runtime.publishIdle(gameTime);
            return;
        }

        int chunkBudget = VegetationWorkBudget.maximumChunksPerTick(runtime.loadedCount(), interval);
        int chunksProcessed = 0;
        int attempts = 0;
        int plantsProcessed = 0;
        int blockStateChanges = 0;
        long elapsedNanos = 0L;
        while (chunksProcessed < chunkBudget) {
            Long chunkKey = runtime.pollDue(gameTime);
            if (chunkKey == null) {
                break;
            }
            int chunkX = ChunkPos.getX(chunkKey);
            int chunkZ = ChunkPos.getZ(chunkKey);
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null) {
                runtime.remove(chunkKey);
                continue;
            }
            ChunkResult result = updateChunk(level, chunk, gameTime);
            attempts += result.attempts();
            plantsProcessed += result.plantsProcessed();
            blockStateChanges += result.blockStateChanges();
            elapsedNanos += result.elapsedNanos();
            chunksProcessed++;
            runtime.reschedule(chunkKey, gameTime + interval);
        }
        runtime.publish(
                gameTime,
                chunksProcessed,
                attempts,
                plantsProcessed,
                blockStateChanges,
                elapsedNanos
        );
    }

    /** Releases only one dimension's ephemeral loaded-chunk queue and timings. */
    public static void clearLevel(ServerLevel level) {
        RUNTIMES.remove(level);
    }

    /** Releases all ephemeral queues after server worlds have saved. */
    public static void clearAll() {
        RUNTIMES.clear();
    }

    /** Returns a read-only scheduler snapshot for operator diagnostics. */
    public static Diagnostics diagnostics(ServerLevel level) {
        LevelRuntime runtime = RUNTIMES.get(level);
        return runtime == null ? Diagnostics.EMPTY : runtime.diagnostics;
    }

    private static ChunkResult updateChunk(ServerLevel level, LevelChunk chunk, long gameTime) {
        long started = System.nanoTime();
        ReactiveVegetationState stored = chunk.getData(ModAttachments.REACTIVE_VEGETATION);
        VegetationClimateState previous = stored.snapshot();
        BlockPos samplePosition = new BlockPos(
                chunk.getPos().getMiddleBlockX(),
                level.getSeaLevel(),
                chunk.getPos().getMiddleBlockZ()
        );
        WeatherQuery weather = WeatherServices.query();
        WeatherSample sample = weather.sample(level, samplePosition);
        SeasonalClimateState season = weather.seasonalClimateAt(level, samplePosition);
        VegetationClimateState climate = VegetationClimateModel.advance(
                previous,
                sample,
                season,
                VegetationConfig.DROUGHT_SENSITIVITY.get(),
                VegetationConfig.RAIN_RECOVERY_RATE.get(),
                gameTime
        );
        stored.applyClimate(climate);

        int configuredAttempts = VegetationConfig.UPDATES_PER_CHUNK.get();
        int plantsProcessed = 0;
        int blockStateChanges = 0;
        for (int attempt = 0; attempt < configuredAttempts; attempt++) {
            long randomBits = mix(
                    level.getSeed()
                            ^ chunk.getPos().toLong()
                            ^ gameTime * 0x9E3779B97F4A7C15L
                            ^ attempt * 0xC2B2AE3D27D4EB4FL
            );
            int localX = (int) (randomBits & 15L);
            int localZ = (int) ((randomBits >>> 8) & 15L);
            int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ) - 1;
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                continue;
            }
            BlockPos position = new BlockPos(
                    chunk.getPos().getMinBlockX() + localX,
                    y,
                    chunk.getPos().getMinBlockZ() + localZ
            );
            BlockState state = chunk.getBlockState(position);
            ReactiveVegetationServices.PlantUpdateResult result =
                    ReactiveVegetationServices.processSelectedPlant(
                            level,
                            position,
                            state,
                            climate,
                            randomBits
                    );
            if (result.registered()) {
                plantsProcessed++;
            }
            if (result.stateChanged()) {
                blockStateChanges++;
            }
        }

        long elapsed = System.nanoTime() - started;
        stored.recordProcessing(gameTime, plantsProcessed, elapsed);
        // Mutable attachments require an explicit compact sync after a regional update.
        chunk.syncData(ModAttachments.REACTIVE_VEGETATION);
        return new ChunkResult(configuredAttempts, plantsProcessed, blockStateChanges, elapsed);
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    /** Current per-dimension work and timing values exposed by the debug command. */
    public record Diagnostics(
            long tick,
            int loadedChunks,
            int scheduledChunks,
            int chunksProcessed,
            int sampleAttempts,
            int plantsProcessed,
            int blockStateChanges,
            double averageChunkProcessingMicros
    ) {
        private static final Diagnostics EMPTY = new Diagnostics(0L, 0, 0, 0, 0, 0, 0, 0.0);
    }

    private record ChunkResult(
            int attempts,
            int plantsProcessed,
            int blockStateChanges,
            long elapsedNanos
    ) {
    }

    static final class LevelRuntime {
        private final Set<Long> loaded = new HashSet<>();
        private final PriorityQueue<ScheduledChunk> due = new PriorityQueue<>();
        private final Map<Long, ScheduledChunk> scheduled = new HashMap<>();
        private int interval = -1;
        private double averageChunkMicros;
        private Diagnostics diagnostics = Diagnostics.EMPTY;

        void add(long chunkKey, long gameTime, int configuredInterval) {
            if (!loaded.add(chunkKey)) {
                return;
            }
            ensureInterval(configuredInterval, gameTime);
            schedule(chunkKey, initialDue(chunkKey, gameTime, interval));
        }

        void remove(long chunkKey) {
            loaded.remove(chunkKey);
            ScheduledChunk entry = scheduled.remove(chunkKey);
            if (entry != null) {
                due.remove(entry);
            }
        }

        private void ensureInterval(int configuredInterval, long gameTime) {
            int safeInterval = Math.max(1, configuredInterval);
            if (interval == safeInterval) {
                return;
            }
            interval = safeInterval;
            due.clear();
            scheduled.clear();
            for (long chunkKey : loaded) {
                schedule(chunkKey, initialDue(chunkKey, gameTime, interval));
            }
        }

        Long pollDue(long gameTime) {
            ScheduledChunk entry = due.peek();
            if (entry == null || entry.dueTick() > gameTime) {
                return null;
            }
            due.remove();
            scheduled.remove(entry.chunkKey());
            return loaded.contains(entry.chunkKey()) ? entry.chunkKey() : pollDue(gameTime);
        }

        private void reschedule(long chunkKey, long dueTick) {
            if (loaded.contains(chunkKey)) {
                schedule(chunkKey, dueTick);
            }
        }

        private void schedule(long chunkKey, long dueTick) {
            ScheduledChunk entry = new ScheduledChunk(chunkKey, Math.max(0L, dueTick));
            ScheduledChunk previous = scheduled.put(chunkKey, entry);
            if (previous != null) {
                due.remove(previous);
            }
            due.add(entry);
        }

        int loadedCount() {
            return loaded.size();
        }

        private void publish(
                long gameTime,
                int chunksProcessed,
                int attempts,
                int plantsProcessed,
                int blockStateChanges,
                long elapsedNanos
        ) {
            if (chunksProcessed > 0) {
                double currentMicros = elapsedNanos / 1_000.0 / chunksProcessed;
                averageChunkMicros = averageChunkMicros <= 0.0
                        ? currentMicros
                        : averageChunkMicros * 0.90 + currentMicros * 0.10;
            }
            diagnostics = new Diagnostics(
                    gameTime,
                    loaded.size(),
                    scheduled.size(),
                    chunksProcessed,
                    attempts,
                    plantsProcessed,
                    blockStateChanges,
                    averageChunkMicros
            );
        }

        private void publishIdle(long gameTime) {
            publish(gameTime, 0, 0, 0, 0, 0L);
        }

        private static long initialDue(long chunkKey, long gameTime, int interval) {
            return gameTime + 1L + Math.floorMod(mix(chunkKey), interval);
        }
    }

    private record ScheduledChunk(long chunkKey, long dueTick) implements Comparable<ScheduledChunk> {
        @Override
        public int compareTo(ScheduledChunk other) {
            int byTick = Long.compare(dueTick, other.dueTick);
            return byTick != 0 ? byTick : Long.compare(chunkKey, other.chunkKey);
        }
    }
}
