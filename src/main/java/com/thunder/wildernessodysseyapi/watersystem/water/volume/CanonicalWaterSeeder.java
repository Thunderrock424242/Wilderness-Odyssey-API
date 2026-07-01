package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/**
 * Imports exposed vanilla world water into the canonical chunk-volume store.
 *
 * <p>World generation still creates normal {@code minecraft:water}, which is
 * important for compatibility and modded terrain. This seeder lazily mirrors
 * that stable water into canonical cells when chunks load, making the
 * replacement system aware of oceans, rivers, lakes, and frozen water columns
 * without forcing every untouched reservoir to tick.</p>
 */
public final class CanonicalWaterSeeder {

    private static final int CHUNK_WIDTH = 16;

    private CanonicalWaterSeeder() {
    }

    /**
     * Seeds one loaded chunk if automatic world seeding is enabled.
     *
     * @param level server level owning the chunk
     * @param chunk loaded chunk to inspect
     * @return import statistics for logging or debug commands
     */
    public static SeedStats seedLoadedChunk(ServerLevel level, LevelChunk chunk) {
        if (!WaterSimulationConfig.ENABLE_CANONICAL_WORLD_SEEDING.get()) {
            return SeedStats.EMPTY;
        }
        return seedChunk(level, chunk, WaterSimulationConfig.worldSeedMaxColumnDepth());
    }

    /**
     * Seeds one loaded chunk using a caller-supplied maximum column depth.
     *
     * <p>The method imports only stable compatibility cells. Imported cells are
     * flagged as lazy worldgen mirrors, so the finite-volume ticker ignores them
     * until a bucket, drain, or adjacent disturbed cell explicitly changes them.</p>
     */
    public static SeedStats seedChunk(ServerLevel level, LevelChunk chunk, int maxColumnDepth) {
        int boundedDepth = Math.max(1, Math.min(64, maxColumnDepth));
        ChunkPos chunkPos = chunk.getPos();
        SeedStats stats = SeedStats.EMPTY;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
            for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ) - 1;
                stats = stats.plus(seedColumn(level, cursor, worldX, surfaceY, worldZ, boundedDepth));
            }
        }
        return stats;
    }

    private static SeedStats seedColumn(
            ServerLevel level,
            BlockPos.MutableBlockPos cursor,
            int worldX,
            int surfaceY,
            int worldZ,
            int maxColumnDepth
    ) {
        SeedStats stats = new SeedStats(1, 0, 0, 0, 0);
        int waterSurfaceY = findWaterSurface(level, cursor, worldX, surfaceY, worldZ);
        if (waterSurfaceY == Integer.MIN_VALUE) {
            return stats;
        }

        for (int depth = 0; depth < maxColumnDepth; depth++) {
            cursor.set(worldX, waterSurfaceY - depth, worldZ);
            if (level.isOutsideBuildHeight(cursor) || !level.hasChunkAt(cursor)) {
                return stats;
            }
            WaterCellCandidate candidate = waterCellCandidate(level, cursor);
            if (!candidate.water()) {
                return stats;
            }
            if (candidate.waterloggedHost()) {
                stats = stats.withSkippedWaterlogged(stats.skippedWaterlogged() + 1);
                return stats;
            }
            if (CanonicalWater.isTracked(level, cursor)) {
                stats = stats.withSkippedTracked(stats.skippedTracked() + 1);
                continue;
            }
            WaterVolumeChunk.WaterCell imported = CanonicalWater.getOrImport(level, cursor);
            if (imported.volumeUnits() > 0) {
                stats = stats.withImportedCells(stats.importedCells() + 1);
            }
        }
        return stats;
    }

    private static int findWaterSurface(
            ServerLevel level,
            BlockPos.MutableBlockPos cursor,
            int worldX,
            int surfaceY,
            int worldZ
    ) {
        int scanDepth = WaterSimulationConfig.coveredWaterSurfaceScanDepth();
        for (int offset = 0; offset <= scanDepth; offset++) {
            cursor.set(worldX, surfaceY - offset, worldZ);
            if (level.isOutsideBuildHeight(cursor) || !level.hasChunkAt(cursor)) {
                return Integer.MIN_VALUE;
            }
            WaterCellCandidate candidate = waterCellCandidate(level, cursor);
            if (candidate.water() && !candidate.waterloggedHost()) {
                return cursor.getY();
            }
        }
        return Integer.MIN_VALUE;
    }

    private static WaterCellCandidate waterCellCandidate(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        FluidState fluid = state.getFluidState();
        if (!fluid.is(FluidTags.WATER)) {
            return WaterCellCandidate.DRY;
        }
        boolean waterloggedHost = WaterSimulationConfig.SEED_ONLY_PLAIN_WATER_BLOCKS.get()
                && !state.is(Blocks.WATER);
        return new WaterCellCandidate(true, waterloggedHost);
    }

    private record WaterCellCandidate(boolean water, boolean waterloggedHost) {
        private static final WaterCellCandidate DRY = new WaterCellCandidate(false, false);
    }

    /** Import counters returned by automatic seeding and debug commands. */
    public record SeedStats(
            int scannedColumns,
            int importedCells,
            int skippedTracked,
            int skippedWaterlogged,
            int loadedChunks
    ) {
        /** Shared zero-value stats object. */
        public static final SeedStats EMPTY = new SeedStats(0, 0, 0, 0, 0);

        /** Returns a copy with one more loaded chunk counted. */
        public SeedStats countedChunk() {
            return new SeedStats(scannedColumns, importedCells, skippedTracked, skippedWaterlogged,
                    loadedChunks + 1);
        }

        /** Combines two independent seed results. */
        public SeedStats plus(SeedStats other) {
            return new SeedStats(
                    scannedColumns + other.scannedColumns,
                    importedCells + other.importedCells,
                    skippedTracked + other.skippedTracked,
                    skippedWaterlogged + other.skippedWaterlogged,
                    loadedChunks + other.loadedChunks
            );
        }

        private SeedStats withImportedCells(int importedCells) {
            return new SeedStats(scannedColumns, importedCells, skippedTracked, skippedWaterlogged, loadedChunks);
        }

        private SeedStats withSkippedTracked(int skippedTracked) {
            return new SeedStats(scannedColumns, importedCells, skippedTracked, skippedWaterlogged, loadedChunks);
        }

        private SeedStats withSkippedWaterlogged(int skippedWaterlogged) {
            return new SeedStats(scannedColumns, importedCells, skippedTracked, skippedWaterlogged, loadedChunks);
        }
    }
}
