package com.thunder.wildernessodysseyapi.environment.glacial.worldgen;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.environment.glacial.GlacialBiomeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Shared bounded worldgen checks; this class never accesses chunks outside the active writable region. */
final class GlacialFeatureSupport {

    private static final Set<String> LOGGED_FAILURES = ConcurrentHashMap.newKeySet();

    private GlacialFeatureSupport() {
    }

    static GlacialBiomeManager.Family family(WorldGenLevel level, BlockPos position) {
        return GlacialBiomeManager.family(level.getBiome(position)).orElse(null);
    }

    static int surfaceY(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
    }

    static int oceanFloorY(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
    }

    static boolean canWrite(WorldGenLevel level, BlockPos position) {
        return position.getY() >= level.getMinBuildHeight()
                && position.getY() < level.getMaxBuildHeight()
                && level.ensureCanWrite(position);
    }

    static StructureGuard structureGuard(WorldGenLevel level) {
        return new StructureGuard(level);
    }

    static boolean naturalTerrain(BlockState state) {
        return state.is(Blocks.STONE)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SAND)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.ICE)
                || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE);
    }

    static boolean carvable(BlockState state) {
        return naturalTerrain(state)
                || state.is(Blocks.ANDESITE)
                || state.is(Blocks.DIORITE)
                || state.is(Blocks.GRANITE)
                || state.is(Blocks.TUFF);
    }

    static void set(WorldGenLevel level, BlockPos position, BlockState state) {
        if (canWrite(level, position)) {
            level.setBlock(position, state, 2);
        }
    }

    /** Smooth deterministic value noise in {@code [-1, 1]} without retaining mutable random state. */
    static double noise(long seed, int x, int z, int scale, long salt) {
        int safeScale = Math.max(2, scale);
        int cellX = Math.floorDiv(x, safeScale);
        int cellZ = Math.floorDiv(z, safeScale);
        double localX = Math.floorMod(x, safeScale) / (double) safeScale;
        double localZ = Math.floorMod(z, safeScale) / (double) safeScale;
        double smoothX = localX * localX * (3.0 - 2.0 * localX);
        double smoothZ = localZ * localZ * (3.0 - 2.0 * localZ);
        double northWest = unitHash(seed, cellX, cellZ, salt);
        double northEast = unitHash(seed, cellX + 1, cellZ, salt);
        double southWest = unitHash(seed, cellX, cellZ + 1, salt);
        double southEast = unitHash(seed, cellX + 1, cellZ + 1, salt);
        double north = mix(northWest, northEast, smoothX);
        double south = mix(southWest, southEast, smoothX);
        return mix(north, south, smoothZ) * 2.0 - 1.0;
    }

    static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    static void logFailure(String feature, BlockPos origin, RuntimeException exception) {
        if (LOGGED_FAILURES.add(feature)) {
            ModConstants.LOGGER.error(
                    "Glacial {} generation failed at {}; further identical feature errors will use normal worldgen diagnostics",
                    feature,
                    origin,
                    exception
            );
        }
    }

    private static double unitHash(long seed, int x, int z, long salt) {
        long bits = mix(seed ^ salt ^ x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL);
        return (bits >>> 11) * 0x1.0p-53;
    }

    private static double mix(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    /** Caches structure boxes per touched chunk for one feature placement. */
    static final class StructureGuard {
        private final WorldGenLevel level;
        private final Map<Long, List<BoundingBox>> boxesByChunk = new HashMap<>();

        private StructureGuard(WorldGenLevel level) {
            this.level = level;
        }

        boolean contains(BlockPos position) {
            // Treat the writable edge as protected before asking for a chunk;
            // large formations are clipped instead of loading a neighbor.
            if (!canWrite(level, position)) {
                return true;
            }
            long chunkKey = ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4);
            List<BoundingBox> boxes = boxesByChunk.computeIfAbsent(
                    chunkKey,
                    ignored -> level.getChunk(position).getAllStarts().values().stream()
                            .filter(start -> start.isValid())
                            .map(start -> start.getBoundingBox())
                            .toList()
            );
            return boxes.stream().anyMatch(box -> box.isInside(position));
        }
    }
}
