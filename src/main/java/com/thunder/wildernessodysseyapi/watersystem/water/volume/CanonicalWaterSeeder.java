package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/**
 * Imports exposed world water into the canonical chunk-volume store.
 *
 * <p>World generation can still create normal {@code minecraft:water} as an
 * intermediate terrain/aquifer result. This seeder finalizes that stable water
 * into canonical cells and, on watched chunks or background ticks, rewrites
 * accepted plain water to the namespaced Wilderness water projection. Waterlogged
 * host blocks contribute hosted canonical water without being replaced.</p>
 */
public final class CanonicalWaterSeeder {

    private static final int CHUNK_WIDTH = 16;
    private static final int COLUMNS_PER_CHUNK = CHUNK_WIDTH * CHUNK_WIDTH;

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
        // Raw chunk-load seeding runs on Minecraft's loading/generation path,
        // so it imports canonical state only. Watched-chunk finalization and
        // server ticks perform the block rewrites under explicit budgets.
        return seedChunk(level, chunk, WaterSimulationConfig.worldSeedMaxColumnDepth(), false);
    }

    /**
     * Seeds one loaded chunk using a caller-supplied maximum column depth.
     *
     * <p>The method imports only stable compatibility cells. Imported cells are
     * flagged as lazy worldgen mirrors, so the finite-volume ticker ignores them
     * until a bucket, drain, or adjacent disturbed cell explicitly changes them.</p>
     */
    public static SeedStats seedChunk(ServerLevel level, LevelChunk chunk, int maxColumnDepth) {
        return seedChunk(level, chunk, maxColumnDepth, true);
    }

    /**
     * Seeds one chunk with explicit control over whether accepted vanilla blocks
     * may be migrated to Wilderness water during the scan.
     */
    public static SeedStats seedChunk(
            ServerLevel level,
            LevelChunk chunk,
            int maxColumnDepth,
            boolean allowBlockConversion
    ) {
        return seedChunkSlice(
                level,
                chunk,
                maxColumnDepth,
                0,
                COLUMNS_PER_CHUNK,
                Integer.MAX_VALUE,
                allowBlockConversion
        ).stats();
    }

    /**
     * Seeds a bounded range of X/Z columns from one chunk.
     *
     * <p>Automatic migration uses this method to spread work across server
     * ticks. It never loads missing chunks; callers are expected to pass an
     * already-loaded {@link LevelChunk}.</p>
     *
     * @param level server level owning the chunk
     * @param chunk loaded chunk to inspect
     * @param maxColumnDepth maximum water depth imported per column
     * @param startColumnIndex next local column index, from {@code 0} to {@code 255}
     * @param maxColumns maximum columns to scan in this slice
     * @param maxConversions maximum vanilla blocks to convert in this slice
     * @param allowBlockConversion whether this slice may migrate block states
     * @return slice progress and import/conversion counters
     */
    public static SeedSlice seedChunkSlice(
            ServerLevel level,
            LevelChunk chunk,
            int maxColumnDepth,
            int startColumnIndex,
            int maxColumns,
            int maxConversions,
            boolean allowBlockConversion
    ) {
        int boundedDepth = Math.max(1, Math.min(64, maxColumnDepth));
        int boundedStart = Math.max(0, Math.min(COLUMNS_PER_CHUNK, startColumnIndex));
        int boundedColumns = Math.max(1, maxColumns);
        ConversionBudget conversionBudget = new ConversionBudget(Math.max(0, maxConversions));
        ChunkPos chunkPos = chunk.getPos();
        SeedStats stats = SeedStats.EMPTY;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int columnIndex = boundedStart;
        int scannedColumns = 0;
        int firstPendingConversionColumn = -1;

        while (columnIndex < COLUMNS_PER_CHUNK && scannedColumns < boundedColumns) {
            int localX = columnIndex / CHUNK_WIDTH;
            int localZ = columnIndex % CHUNK_WIDTH;
            int worldX = chunkPos.getMinBlockX() + localX;
            int worldZ = chunkPos.getMinBlockZ() + localZ;
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ) - 1;
            SeedColumnResult columnResult = seedColumn(
                    level,
                    cursor,
                    worldX,
                    surfaceY,
                    worldZ,
                    boundedDepth,
                    allowBlockConversion,
                    conversionBudget
            );
            stats = stats.plus(columnResult.stats());
            if (columnResult.pendingPlainWaterConversion() && firstPendingConversionColumn < 0) {
                firstPendingConversionColumn = columnIndex;
            }
            scannedColumns++;
            columnIndex++;
        }
        return new SeedSlice(
                stats,
                firstPendingConversionColumn >= 0 ? firstPendingConversionColumn : columnIndex,
                columnIndex >= COLUMNS_PER_CHUNK && firstPendingConversionColumn < 0,
                firstPendingConversionColumn >= 0
        );
    }

    private static SeedColumnResult seedColumn(
            ServerLevel level,
            BlockPos.MutableBlockPos cursor,
            int worldX,
            int surfaceY,
            int worldZ,
            int maxColumnDepth,
            boolean allowBlockConversion,
            ConversionBudget conversionBudget
    ) {
        SeedStats stats = new SeedStats(1, 0, 0, 0, 0, 0, 0);
        int coverScanDepth = WaterSimulationConfig.coveredWaterSurfaceScanDepth();
        int maxScanDepth = Math.max(1, maxColumnDepth + coverScanDepth);
        boolean foundWater = false;
        int waterCellsSeen = 0;
        boolean pendingPlainWaterConversion = false;

        for (int offset = 0; offset < maxScanDepth && waterCellsSeen < maxColumnDepth; offset++) {
            cursor.set(worldX, surfaceY - offset, worldZ);
            if (level.isOutsideBuildHeight(cursor) || !level.hasChunkAt(cursor)) {
                return new SeedColumnResult(stats, pendingPlainWaterConversion);
            }
            WaterCellCandidate candidate = waterCellCandidate(level, cursor);
            if (!candidate.water()) {
                if (foundWater || offset >= coverScanDepth) {
                    return new SeedColumnResult(stats, pendingPlainWaterConversion);
                }
                continue;
            }
            foundWater = true;
            waterCellsSeen++;
            if (!candidate.importable()) {
                stats = stats.withSkippedWaterlogged(stats.skippedWaterlogged() + 1);
                continue;
            }
            if (CanonicalWater.isTracked(level, cursor)) {
                if (candidate.hostedWater()) {
                    CanonicalWater.getOrImport(level, cursor, true);
                } else {
                    ConversionResult conversion = convertSeededProjectionIfNeeded(
                            level,
                            cursor,
                            stats,
                            allowBlockConversion,
                            conversionBudget
                    );
                    stats = conversion.stats();
                    pendingPlainWaterConversion |= conversion.pendingPlainWaterConversion();
                }
                stats = stats.withSkippedTracked(stats.skippedTracked() + 1);
                continue;
            }
            WaterVolumeChunk.WaterCell imported = CanonicalWater.getOrImport(level, cursor, candidate.hostedWater());
            if (imported.volumeUnits() > 0) {
                stats = stats.withImportedCells(stats.importedCells() + 1);
                if (candidate.hostedWater()) {
                    stats = stats.withHostedWaterCells(stats.hostedWaterCells() + 1);
                } else {
                    ConversionResult conversion = convertSeededProjectionIfNeeded(
                            level,
                            cursor,
                            stats,
                            allowBlockConversion,
                            conversionBudget
                    );
                    stats = conversion.stats();
                    pendingPlainWaterConversion |= conversion.pendingPlainWaterConversion();
                }
            }
        }
        return new SeedColumnResult(stats, pendingPlainWaterConversion);
    }

    private static WaterCellCandidate waterCellCandidate(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        FluidState fluid = state.getFluidState();
        if (!fluid.is(FluidTags.WATER)) {
            return WaterCellCandidate.DRY;
        }
        boolean plainWater = isPlainSeedWaterBlock(state);
        boolean hostedWater = !plainWater;
        boolean importHosted = WaterSimulationConfig.importWaterloggedHostWater()
                || !WaterSimulationConfig.SEED_ONLY_PLAIN_WATER_BLOCKS.get();
        return new WaterCellCandidate(true, hostedWater, plainWater || importHosted);
    }

    private static boolean isPlainSeedWaterBlock(BlockState state) {
        return WildernessWaterAuthority.isPlainWaterProjection(state);
    }

    private static ConversionResult convertSeededProjectionIfNeeded(
            ServerLevel level,
            BlockPos pos,
            SeedStats stats,
            boolean allowBlockConversion,
            ConversionBudget conversionBudget
    ) {
        if (!allowBlockConversion) {
            return new ConversionResult(stats, false);
        }
        if (conversionBudget.exhausted()) {
            return new ConversionResult(stats, isPendingPlainVanillaWaterBlock(level, pos));
        }
        if (convertPlainVanillaWaterBlock(level, pos)) {
            conversionBudget.recordConversion();
            return new ConversionResult(
                    stats.withConvertedBlocks(stats.convertedBlocks() + 1),
                    false
            );
        }
        return new ConversionResult(stats, false);
    }

    private static boolean isPendingPlainVanillaWaterBlock(ServerLevel level, BlockPos pos) {
        BlockState current = level.getBlockState(pos);
        return current.is(Blocks.WATER) && current.getFluidState().is(FluidTags.WATER);
    }

    private static boolean convertPlainVanillaWaterBlock(ServerLevel level, BlockPos pos) {
        if (!WaterSimulationConfig.convertSeededWorldWaterToWilderness()) {
            return false;
        }

        BlockState current = level.getBlockState(pos);
        if (!current.is(Blocks.WATER)) {
            return false;
        }

        FluidState fluid = current.getFluidState();
        if (!fluid.is(FluidTags.WATER)) {
            return false;
        }

        // Preserve vanilla's eight-level water amount so migration changes the
        // registry identity, not the visible fill level of a flowing edge.
        int amount = Math.max(1, Math.min(8, fluid.getAmount()));
        BlockState projected = fluid.isSource()
                ? WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get().defaultBlockState()
                : WildernessFluidRegistry.WILDERNESS_WATER.get().getFlowing(amount, false).createLegacyBlock();
        if (current.equals(projected)) {
            return false;
        }

        level.setBlock(pos, projected, Block.UPDATE_CLIENTS);
        return true;
    }

    private record WaterCellCandidate(boolean water, boolean hostedWater, boolean importable) {
        private static final WaterCellCandidate DRY = new WaterCellCandidate(false, false, false);
    }

    private record SeedColumnResult(SeedStats stats, boolean pendingPlainWaterConversion) {
    }

    private record ConversionResult(SeedStats stats, boolean pendingPlainWaterConversion) {
    }

    private static final class ConversionBudget {
        private int remainingConversions;

        private ConversionBudget(int remainingConversions) {
            this.remainingConversions = remainingConversions;
        }

        private boolean exhausted() {
            return remainingConversions <= 0;
        }

        private void recordConversion() {
            if (remainingConversions != Integer.MAX_VALUE) {
                remainingConversions--;
            }
        }
    }

    /** Progress returned by a bounded automatic seeding slice. */
    public record SeedSlice(
            SeedStats stats,
            int nextColumnIndex,
            boolean complete,
            boolean pendingPlainWaterConversions
    ) {
    }

    /** Import counters returned by automatic seeding and debug commands. */
    public record SeedStats(
            int scannedColumns,
            int importedCells,
            int hostedWaterCells,
            int convertedBlocks,
            int skippedTracked,
            int skippedWaterlogged,
            int loadedChunks
    ) {
        /** Shared zero-value stats object. */
        public static final SeedStats EMPTY = new SeedStats(0, 0, 0, 0, 0, 0, 0);

        /** Returns a copy with one more loaded chunk counted. */
        public SeedStats countedChunk() {
            return new SeedStats(scannedColumns, importedCells, hostedWaterCells, convertedBlocks, skippedTracked,
                    skippedWaterlogged, loadedChunks + 1);
        }

        /** Combines two independent seed results. */
        public SeedStats plus(SeedStats other) {
            return new SeedStats(
                    scannedColumns + other.scannedColumns,
                    importedCells + other.importedCells,
                    hostedWaterCells + other.hostedWaterCells,
                    convertedBlocks + other.convertedBlocks,
                    skippedTracked + other.skippedTracked,
                    skippedWaterlogged + other.skippedWaterlogged,
                    loadedChunks + other.loadedChunks
            );
        }

        private SeedStats withImportedCells(int importedCells) {
            return new SeedStats(scannedColumns, importedCells, hostedWaterCells, convertedBlocks, skippedTracked,
                    skippedWaterlogged, loadedChunks);
        }

        private SeedStats withHostedWaterCells(int hostedWaterCells) {
            return new SeedStats(scannedColumns, importedCells, hostedWaterCells, convertedBlocks, skippedTracked,
                    skippedWaterlogged, loadedChunks);
        }

        private SeedStats withConvertedBlocks(int convertedBlocks) {
            return new SeedStats(scannedColumns, importedCells, hostedWaterCells, convertedBlocks, skippedTracked,
                    skippedWaterlogged, loadedChunks);
        }

        private SeedStats withSkippedTracked(int skippedTracked) {
            return new SeedStats(scannedColumns, importedCells, hostedWaterCells, convertedBlocks, skippedTracked,
                    skippedWaterlogged, loadedChunks);
        }

        private SeedStats withSkippedWaterlogged(int skippedWaterlogged) {
            return new SeedStats(scannedColumns, importedCells, hostedWaterCells, convertedBlocks, skippedTracked,
                    skippedWaterlogged, loadedChunks);
        }
    }
}
