package com.thunder.wildernessodysseyapi.environment.glacial.runtime;

import com.thunder.wildernessodysseyapi.environment.glacial.GlacialBiomeManager;
import com.thunder.wildernessodysseyapi.environment.glacial.GlacialSeasonManager;
import com.thunder.wildernessodysseyapi.environment.glacial.GlacialSeasonSnapshot;
import com.thunder.wildernessodysseyapi.environment.glacial.config.GlacialConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies gradual thaw and freeze changes only to loaded glacial chunks near players.
 *
 * <p>The scheduler never obtains chunks through a loading API. Chunk lifecycle events
 * own the candidate set, each lookup uses {@code getChunkNow}, and every inspected
 * surface position consumes the configured per-level budget.</p>
 */
public final class GlacialFreezeManager {

    private static final Map<ServerLevel, LevelRuntime> RUNTIMES = new ConcurrentHashMap<>();

    private GlacialFreezeManager() {
    }

    /** Adds a normally loaded chunk when one of its representative biomes is glacial. */
    public static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        if (isGlacialChunk(chunk)) {
            RUNTIMES.computeIfAbsent(level, ignored -> new LevelRuntime())
                    .add(chunk.getPos().toLong(), level.getGameTime());
        }
    }

    /** Removes an unloaded chunk immediately. */
    public static void onChunkUnload(ServerLevel level, LevelChunk chunk) {
        LevelRuntime runtime = RUNTIMES.get(level);
        if (runtime != null) {
            runtime.remove(chunk.getPos().toLong());
        }
    }

    /** Performs a bounded number of deterministic surface samples for this level tick. */
    public static void tickLevel(ServerLevel level) {
        LevelRuntime runtime = RUNTIMES.computeIfAbsent(level, ignored -> new LevelRuntime());
        long gameTime = level.getGameTime();
        if (!GlacialConfig.ENABLE_SEASONAL_GLACIAL_EFFECTS.get() || level.players().isEmpty()) {
            runtime.publish(gameTime, 0, 0, 0, 0);
            return;
        }

        int budget = GlacialWorkBudget.samplesPerTick(
                GlacialConfig.GLACIAL_SEASON_UPDATE_BUDGET.get(),
                runtime.size()
        );
        int inspected = 0;
        int changed = 0;
        int frozen = 0;
        int thawed = 0;
        while (inspected < budget) {
            Long chunkKey = runtime.nextDue(gameTime, GlacialConfig.GLACIAL_SEASON_UPDATE_INTERVAL.get());
            if (chunkKey == null) {
                break;
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(
                    ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
            if (chunk == null) {
                runtime.remove(chunkKey);
                continue;
            }
            int remaining = budget - inspected;
            ChunkResult result = updateChunk(level, chunk, gameTime, Math.min(4, remaining));
            inspected += result.inspected();
            changed += result.changed();
            frozen += result.frozen();
            thawed += result.thawed();
        }
        runtime.publish(gameTime, inspected, changed, frozen, thawed);
    }

    /** Releases one dimension's ephemeral chunk rotation. */
    public static void clearLevel(ServerLevel level) {
        RUNTIMES.remove(level);
    }

    /** Releases every ephemeral scheduler after server shutdown. */
    public static void clearAll() {
        RUNTIMES.clear();
    }

    /** Returns current operator diagnostics without exposing mutable scheduler state. */
    public static Diagnostics diagnostics(ServerLevel level) {
        LevelRuntime runtime = RUNTIMES.get(level);
        return runtime == null ? Diagnostics.EMPTY : runtime.diagnostics;
    }

    private static ChunkResult updateChunk(ServerLevel level, LevelChunk chunk, long gameTime, int samples) {
        int inspected = 0;
        int frozen = 0;
        int thawed = 0;
        for (int attempt = 0; attempt < samples; attempt++) {
            long bits = mix(level.getSeed() ^ chunk.getPos().toLong()
                    ^ gameTime * 0x9E3779B97F4A7C15L ^ attempt * 0xC2B2AE3D27D4EB4FL);
            int localX = (int) (bits & 15L);
            int localZ = (int) ((bits >>> 8) & 15L);
            int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ) - 1;
            inspected++;
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                continue;
            }
            BlockPos position = new BlockPos(
                    chunk.getPos().getMinBlockX() + localX,
                    y,
                    chunk.getPos().getMinBlockZ() + localZ
            );
            if (!GlacialBiomeManager.isGlacial(chunk.getNoiseBiome(
                    QuartPos.fromBlock(position.getX()),
                    QuartPos.fromBlock(position.getY()),
                    QuartPos.fromBlock(position.getZ())))
                    || !nearPlayer(level, position)
                    || insideStructure(chunk, position)) {
                continue;
            }
            GlacialSeasonSnapshot season = GlacialSeasonManager.sample(level, position);
            double roll = ((bits >>> 11) * 0x1.0p-53);
            BlockState state = chunk.getBlockState(position);
            if (GlacialConfig.ENABLE_SEASONAL_MELTWATER.get()
                    && state.is(Blocks.ICE)
                    && roll < season.meltFraction() * 0.16
                    && thaw(level, position)) {
                thawed++;
                continue;
            }
            if (GlacialConfig.ENABLE_SEASONAL_RIVER_FREEZING.get()
                    && state.getFluidState().is(FluidTags.WATER)
                    && level.canSeeSky(position.above())
                    && roll < season.freezeFraction() * 0.18
                    && freeze(level, position)) {
                frozen++;
            }
        }
        return new ChunkResult(inspected, frozen + thawed, frozen, thawed);
    }

    private static boolean thaw(ServerLevel level, BlockPos position) {
        if (!level.setBlock(position, Blocks.AIR.defaultBlockState(), 3)) {
            return false;
        }
        if (!WildernessWaterAuthority.addWaterVolume(
                level, position, WaterVolumeChunk.UNITS_PER_BLOCK)) {
            level.setBlock(
                    position,
                    WildernessFluidRegistry.WILDERNESS_WATER.get().defaultFluidState().createLegacyBlock(),
                    3
            );
        }
        return true;
    }

    private static boolean freeze(ServerLevel level, BlockPos position) {
        int amount = WildernessWaterAuthority.getWaterAmount(level, position);
        if (amount > 0) {
            WildernessWaterAuthority.removeWaterVolume(level, position, amount);
        }
        return level.setBlock(position, Blocks.ICE.defaultBlockState(), 3);
    }

    private static boolean nearPlayer(ServerLevel level, BlockPos position) {
        double maximumDistanceSquared = Math.pow(GlacialConfig.GLACIAL_SEASON_UPDATE_RADIUS.get(), 2);
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(position) <= maximumDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    private static boolean insideStructure(LevelChunk chunk, BlockPos position) {
        return chunk.getAllStarts().values().stream()
                .anyMatch(start -> start.isValid() && start.getBoundingBox().isInside(position));
    }

    private static boolean isGlacialChunk(LevelChunk chunk) {
        int[][] samples = {{2, 2}, {8, 8}, {13, 2}, {2, 13}, {13, 13}};
        for (int[] sample : samples) {
            int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, sample[0], sample[1]) - 1;
            if (GlacialBiomeManager.isGlacial(chunk.getNoiseBiome(
                    QuartPos.fromBlock(chunk.getPos().getMinBlockX() + sample[0]),
                    QuartPos.fromBlock(y),
                    QuartPos.fromBlock(chunk.getPos().getMinBlockZ() + sample[1])
            ))) {
                return true;
            }
        }
        return false;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    /** Per-level scheduler counters shown by the glacier debug command. */
    public record Diagnostics(long tick, int loadedChunks, int inspected, int changed, int frozen, int thawed) {
        private static final Diagnostics EMPTY = new Diagnostics(0L, 0, 0, 0, 0, 0);
    }

    private record ChunkResult(int inspected, int changed, int frozen, int thawed) {
    }

    private static final class LevelRuntime {
        private final Set<Long> loaded = new HashSet<>();
        private final List<Long> rotation = new ArrayList<>();
        private int cursor;
        private long nextPassTick;
        private Diagnostics diagnostics = Diagnostics.EMPTY;

        void add(long chunkKey, long gameTime) {
            if (loaded.add(chunkKey)) {
                rotation.add(chunkKey);
                nextPassTick = Math.min(nextPassTick == 0L ? gameTime : nextPassTick, gameTime);
            }
        }

        void remove(long chunkKey) {
            if (loaded.remove(chunkKey)) {
                rotation.remove(chunkKey);
                cursor = rotation.isEmpty() ? 0 : Math.floorMod(cursor, rotation.size());
            }
        }

        int size() {
            return loaded.size();
        }

        Long nextDue(long gameTime, int interval) {
            if (rotation.isEmpty() || gameTime < nextPassTick) {
                return null;
            }
            if (cursor >= rotation.size()) {
                cursor = 0;
            }
            Long key = rotation.get(cursor++);
            if (cursor >= rotation.size()) {
                cursor = 0;
                nextPassTick = gameTime + Math.max(1, interval);
            }
            return key;
        }

        void publish(long gameTime, int inspected, int changed, int frozen, int thawed) {
            diagnostics = new Diagnostics(gameTime, loaded.size(), inspected, changed, frozen, thawed);
        }
    }
}
