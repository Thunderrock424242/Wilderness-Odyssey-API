package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;

/**
 * Explicitly converts a bounded loaded cube of legacy vanilla water.
 *
 * <p>This service is only called by an operator command. It never searches
 * completed chunks automatically and uses {@code getChunkNow} before visiting a
 * column, so conversion cannot generate or synchronously load terrain.</p>
 */
public final class ExistingWorldWaterConverter {

    /** Keeps one operator invocation below 120,000 inspected block positions. */
    public static final int MAX_RADIUS = 24;
    /** Small default suitable for inspecting and upgrading one local shoreline. */
    public static final int DEFAULT_RADIUS = 8;

    private ExistingWorldWaterConverter() {
    }

    /** Converts exact vanilla liquid blocks inside the loaded bounded region. */
    public static ConversionResult convertLoaded(ServerLevel level, BlockPos center, int requestedRadius) {
        int radius = boundedRadius(requestedRadius);
        int minimumY = Math.max(level.getMinBuildHeight(), center.getY() - radius);
        int maximumY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + radius);

        int inspected = 0;
        int unloadedColumns = 0;
        int vanillaWater = 0;
        int converted = 0;
        int reprojected = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
                    unloadedColumns++;
                    continue;
                }
                for (int y = minimumY; y <= maximumY; y++) {
                    cursor.set(x, y, z);
                    inspected++;
                    BlockState state = level.getBlockState(cursor);
                    if (!state.is(Blocks.WATER)) {
                        continue;
                    }
                    vanillaWater++;
                    if (CanonicalWater.isTracked(level, cursor)) {
                        CanonicalWater.reprojectCompatibility(level, cursor);
                        reprojected++;
                        continue;
                    }

                    FluidState fluid = state.getFluidState();
                    int volumeUnits = WildernessWaterAuthority.volumeUnitsFromFluid(fluid);
                    if (volumeUnits <= 0) {
                        continue;
                    }
                    boolean stableSource = fluid.isSource();
                    int flags = WaterVolumeChunk.FLAG_IMPORTED
                            | WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED;
                    if (stableSource) {
                        flags |= WaterVolumeChunk.FLAG_SLEEPING;
                    }
                    CanonicalWater.set(
                            level,
                            cursor,
                            WaterVolumeChunk.WaterCell.still(volumeUnits, flags),
                            true,
                            !stableSource
                    );
                    converted++;
                }
            }
        }
        return new ConversionResult(radius, inspected, unloadedColumns, vanillaWater, converted, reprojected);
    }

    /** Checks that every column in a requested transition cube is already loaded. */
    public static LoadedCoverage inspectLoadedCoverage(
            ServerLevel level,
            BlockPos center,
            int requestedRadius
    ) {
        int radius = boundedRadius(requestedRadius);
        int loadedColumns = 0;
        int unloadedColumns = 0;
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
                    unloadedColumns++;
                } else {
                    loadedColumns++;
                }
            }
        }
        return new LoadedCoverage(radius, loadedColumns, unloadedColumns);
    }

    /**
     * Validates every exact vanilla-water cell before an OFF-to-ON transition.
     * The check includes loaded coverage, convertible fluid amounts, and sparse
     * attachment capacity, so staging cannot silently skip part of the cube.
     */
    public static ActivationPreflight preflightActivation(
            ServerLevel level,
            BlockPos center,
            int requestedRadius
    ) {
        int radius = boundedRadius(requestedRadius);
        int minimumY = Math.max(level.getMinBuildHeight(), center.getY() - radius);
        int maximumY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + radius);
        int exactVanillaWater = 0;
        int invalidWater = 0;
        int mismatchedTrackedWater = 0;
        int unloadedColumns = 0;
        Map<Long, Integer> additions = new HashMap<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null) {
                    unloadedColumns++;
                    continue;
                }
                for (int y = minimumY; y <= maximumY; y++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (!state.is(Blocks.WATER)) {
                        continue;
                    }
                    exactVanillaWater++;
                    int expectedVolume = WildernessWaterAuthority.volumeUnitsFromFluid(
                            state.getFluidState());
                    WaterVolumeChunk.WaterCell tracked = CanonicalWater.getTracked(level, cursor);
                    if (expectedVolume <= 0) {
                        invalidWater++;
                    } else if (tracked != null && tracked.volumeUnits() != expectedVolume) {
                        mismatchedTrackedWater++;
                    } else if (tracked == null) {
                        additions.merge(ChunkPos.asLong(x >> 4, z >> 4), 1, Integer::sum);
                    }
                }
            }
        }

        int capacityExceededChunks = 0;
        for (Map.Entry<Long, Integer> entry : additions.entrySet()) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(
                    ChunkPos.getX(entry.getKey()),
                    ChunkPos.getZ(entry.getKey())
            );
            if (chunk == null) {
                unloadedColumns++;
                continue;
            }
            int existing = chunk.getExistingData(ModAttachments.WATER_VOLUME)
                    .map(volume -> volume.snapshot().size())
                    .orElse(0);
            if ((long) existing + entry.getValue() > WaterVolumeChunk.MAX_PERSISTED_CELLS) {
                capacityExceededChunks++;
            }
        }
        return new ActivationPreflight(
                radius,
                exactVanillaWater,
                unloadedColumns,
                invalidWater,
                mismatchedTrackedWater,
                capacityExceededChunks
        );
    }

    /**
     * Stages canonical metadata while leaving vanilla blocks untouched.
     * Authority remains safely OFF until the caller verifies every staged cell.
     */
    public static ConversionResult stageLoadedForActivation(
            ServerLevel level,
            BlockPos center,
            ActivationPreflight preflight
    ) {
        if (preflight == null || !preflight.successful()) {
            throw new IllegalArgumentException("Water activation staging requires a successful preflight");
        }
        int radius = preflight.radius();
        int minimumY = Math.max(level.getMinBuildHeight(), center.getY() - radius);
        int maximumY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + radius);
        int inspected = 0;
        int vanillaWater = 0;
        int converted = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                for (int y = minimumY; y <= maximumY; y++) {
                    cursor.set(x, y, z);
                    inspected++;
                    BlockState state = level.getBlockState(cursor);
                    if (!state.is(Blocks.WATER)) {
                        continue;
                    }
                    vanillaWater++;
                    if (CanonicalWater.isTracked(level, cursor)) {
                        continue;
                    }
                    FluidState fluid = state.getFluidState();
                    int flags = WaterVolumeChunk.FLAG_IMPORTED;
                    if (fluid.isSource()) {
                        flags |= WaterVolumeChunk.FLAG_SLEEPING;
                    }
                    CanonicalWater.set(level, cursor, WaterVolumeChunk.WaterCell.still(
                            WildernessWaterAuthority.volumeUnitsFromFluid(fluid), flags
                    ), false, false);
                    converted++;
                }
            }
        }
        return new ConversionResult(radius, inspected, 0, vanillaWater, converted, 0);
    }

    /** Confirms every still-vanilla block has matching canonical metadata. */
    public static StagingVerification verifyStaged(
            ServerLevel level,
            BlockPos center,
            int requestedRadius
    ) {
        int radius = boundedRadius(requestedRadius);
        int minimumY = Math.max(level.getMinBuildHeight(), center.getY() - radius);
        int maximumY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + radius);
        int vanillaWater = 0;
        int mismatches = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                for (int y = minimumY; y <= maximumY; y++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (!state.is(Blocks.WATER)) {
                        continue;
                    }
                    vanillaWater++;
                    WaterVolumeChunk.WaterCell tracked = CanonicalWater.getTracked(level, cursor);
                    int expected = WildernessWaterAuthority.volumeUnitsFromFluid(state.getFluidState());
                    if (tracked == null || tracked.volumeUnits() != expected) {
                        mismatches++;
                    }
                }
            }
        }
        return new StagingVerification(radius, vanillaWater, mismatches);
    }

    /** Projects verified staged cells only after persisted authority is ON. */
    public static int projectStaged(ServerLevel level, BlockPos center, int requestedRadius) {
        int radius = boundedRadius(requestedRadius);
        int minimumY = Math.max(level.getMinBuildHeight(), center.getY() - radius);
        int maximumY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + radius);
        int projected = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                for (int y = minimumY; y <= maximumY; y++) {
                    cursor.set(x, y, z);
                    if (!level.getBlockState(cursor).is(Blocks.WATER)) {
                        continue;
                    }
                    WaterVolumeChunk.WaterCell tracked = CanonicalWater.getTracked(level, cursor);
                    if (tracked == null) {
                        continue;
                    }
                    CanonicalWater.reprojectCompatibility(level, cursor);
                    CanonicalWater.set(level, cursor, tracked.withAddedFlags(
                            WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED
                    ), false, false);
                    if (!tracked.sleeping()) {
                        CanonicalWater.schedule(level, cursor);
                    }
                    projected++;
                }
            }
        }
        return projected;
    }

    /** Verifies that conversion left no exact vanilla-water projection behind. */
    public static ConversionVerification verifyLoaded(
            ServerLevel level,
            BlockPos center,
            int requestedRadius
    ) {
        int radius = boundedRadius(requestedRadius);
        int minimumY = Math.max(level.getMinBuildHeight(), center.getY() - radius);
        int maximumY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + radius);
        int inspected = 0;
        int unloadedColumns = 0;
        int remainingVanillaWater = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
                    unloadedColumns++;
                    continue;
                }
                for (int y = minimumY; y <= maximumY; y++) {
                    cursor.set(x, y, z);
                    inspected++;
                    if (level.getBlockState(cursor).is(Blocks.WATER)) {
                        remainingVanillaWater++;
                    }
                }
            }
        }
        return new ConversionVerification(radius, inspected, unloadedColumns, remainingVanillaWater);
    }

    static int boundedRadius(int requestedRadius) {
        return Math.max(1, Math.min(MAX_RADIUS, requestedRadius));
    }

    /** Immutable command report for one explicit conversion invocation. */
    public record ConversionResult(
            int radius,
            int inspected,
            int unloadedColumns,
            int vanillaWater,
            int converted,
            int reprojected
    ) {
    }

    /** Immutable preflight report used before changing persisted ownership. */
    public record LoadedCoverage(int radius, int loadedColumns, int unloadedColumns) {
        /** Returns whether the requested cube can be converted without loading terrain. */
        public boolean complete() {
            return unloadedColumns == 0;
        }
    }

    /** Immutable whole-cube preflight for a persisted authority transition. */
    public record ActivationPreflight(
            int radius,
            int exactVanillaWater,
            int unloadedColumns,
            int invalidWater,
            int mismatchedTrackedWater,
            int capacityExceededChunks
    ) {
        /** Returns whether every exact water block can be staged without partial projection. */
        public boolean successful() {
            return unloadedColumns == 0
                    && invalidWater == 0
                    && mismatchedTrackedWater == 0
                    && capacityExceededChunks == 0;
        }
    }

    /** Immutable proof that vanilla blocks and staged canonical metadata agree. */
    public record StagingVerification(int radius, int vanillaWater, int mismatches) {
        /** Returns whether committing persisted authority is safe. */
        public boolean successful() {
            return mismatches == 0;
        }
    }

    /** Immutable post-conversion proof used before committing persisted ownership. */
    public record ConversionVerification(
            int radius,
            int inspected,
            int unloadedColumns,
            int remainingVanillaWater
    ) {
        /** Returns whether the bounded conversion completed without a coverage or projection gap. */
        public boolean successful() {
            return unloadedColumns == 0 && remainingVanillaWater == 0;
        }
    }
}
