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
 * <p>World generation still creates normal {@code minecraft:water}, which is
 * important for compatibility and modded terrain. This seeder lazily mirrors
 * that stable water into canonical cells when chunks load. When configured, it
 * also migrates accepted plain water blocks to the namespaced Wilderness water
 * projection so the replacement system owns the block/fluid identity while
 * keeping {@code #minecraft:water} tag compatibility.</p>
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
        // Chunk-load seeding runs on Minecraft's loading/generation path, so it
        // imports canonical state only. Rewriting world blocks here can stall
        // spawn preparation when large oceans are being generated.
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

        while (columnIndex < COLUMNS_PER_CHUNK && scannedColumns < boundedColumns) {
            if (allowBlockConversion && conversionBudget.exhausted()) {
                break;
            }

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
            scannedColumns++;
            if (columnResult.conversionBudgetExhausted()) {
                break;
            }
            columnIndex++;
        }
        return new SeedSlice(stats, columnIndex, columnIndex >= COLUMNS_PER_CHUNK,
                allowBlockConversion && conversionBudget.exhausted());
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
        SeedStats stats = new SeedStats(1, 0, 0, 0, 0, 0);
        int waterSurfaceY = findWaterSurface(level, cursor, worldX, surfaceY, worldZ);
        if (waterSurfaceY == Integer.MIN_VALUE) {
            return new SeedColumnResult(stats, false);
        }

        for (int depth = 0; depth < maxColumnDepth; depth++) {
            cursor.set(worldX, waterSurfaceY - depth, worldZ);
            if (level.isOutsideBuildHeight(cursor) || !level.hasChunkAt(cursor)) {
                return new SeedColumnResult(stats, false);
            }
            WaterCellCandidate candidate = waterCellCandidate(level, cursor);
            if (!candidate.water()) {
                return new SeedColumnResult(stats, false);
            }
            if (candidate.waterloggedHost()) {
                stats = stats.withSkippedWaterlogged(stats.skippedWaterlogged() + 1);
                return new SeedColumnResult(stats, false);
            }
            if (CanonicalWater.isTracked(level, cursor)) {
                ConversionResult conversion = convertSeededProjectionIfNeeded(
                        level,
                        cursor,
                        stats,
                        allowBlockConversion,
                        conversionBudget
                );
                stats = conversion.stats();
                if (conversion.budgetExhausted()) {
                    return new SeedColumnResult(stats, true);
                }
                stats = stats.withSkippedTracked(stats.skippedTracked() + 1);
                continue;
            }
            WaterVolumeChunk.WaterCell imported = CanonicalWater.getOrImport(level, cursor);
            if (imported.volumeUnits() > 0) {
                stats = stats.withImportedCells(stats.importedCells() + 1);
                ConversionResult conversion = convertSeededProjectionIfNeeded(
                        level,
                        cursor,
                        stats,
                        allowBlockConversion,
                        conversionBudget
                );
                stats = conversion.stats();
                if (conversion.budgetExhausted()) {
                    return new SeedColumnResult(stats, true);
                }
            }
        }
        return new SeedColumnResult(stats, false);
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
                && !isPlainSeedWaterBlock(state);
        return new WaterCellCandidate(true, waterloggedHost);
    }

    private static boolean isPlainSeedWaterBlock(BlockState state) {
        return state.is(Blocks.WATER) || state.is(WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get());
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
            return new ConversionResult(stats, true);
        }
        if (convertPlainVanillaWaterBlock(level, pos)) {
            conversionBudget.recordConversion();
            return new ConversionResult(
                    stats.withConvertedBlocks(stats.convertedBlocks() + 1),
                    conversionBudget.exhausted()
            );
        }
        return new ConversionResult(stats, false);
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

    private record WaterCellCandidate(boolean water, boolean waterloggedHost) {
        private static final WaterCellCandidate DRY = new WaterCellCandidate(false, false);
    }

    private record SeedColumnResult(SeedStats stats, boolean conversionBudgetExhausted) {
    }

    private record ConversionResult(SeedStats stats, boolean budgetExhausted) {
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
            boolean conversionBudgetExhausted
    ) {
    }

    /** Import counters returned by automatic seeding and debug commands. */
    public record SeedStats(
            int scannedColumns,
            int importedCells,
            int convertedBlocks,
            int skippedTracked,
            int skippedWaterlogged,
            int loadedChunks
    ) {
        /** Shared zero-value stats object. */
        public static final SeedStats EMPTY = new SeedStats(0, 0, 0, 0, 0, 0);

        /** Returns a copy with one more loaded chunk counted. */
        public SeedStats countedChunk() {
            return new SeedStats(scannedColumns, importedCells, convertedBlocks, skippedTracked, skippedWaterlogged,
                    loadedChunks + 1);
        }

        /** Combines two independent seed results. */
        public SeedStats plus(SeedStats other) {
            return new SeedStats(
                    scannedColumns + other.scannedColumns,
                    importedCells + other.importedCells,
                    convertedBlocks + other.convertedBlocks,
                    skippedTracked + other.skippedTracked,
                    skippedWaterlogged + other.skippedWaterlogged,
                    loadedChunks + other.loadedChunks
            );
        }

        private SeedStats withImportedCells(int importedCells) {
            return new SeedStats(scannedColumns, importedCells, convertedBlocks, skippedTracked, skippedWaterlogged,
                    loadedChunks);
        }

        private SeedStats withConvertedBlocks(int convertedBlocks) {
            return new SeedStats(scannedColumns, importedCells, convertedBlocks, skippedTracked, skippedWaterlogged,
                    loadedChunks);
        }

        private SeedStats withSkippedTracked(int skippedTracked) {
            return new SeedStats(scannedColumns, importedCells, convertedBlocks, skippedTracked, skippedWaterlogged,
                    loadedChunks);
        }

        private SeedStats withSkippedWaterlogged(int skippedWaterlogged) {
            return new SeedStats(scannedColumns, importedCells, convertedBlocks, skippedTracked, skippedWaterlogged,
                    loadedChunks);
        }
    }
}
