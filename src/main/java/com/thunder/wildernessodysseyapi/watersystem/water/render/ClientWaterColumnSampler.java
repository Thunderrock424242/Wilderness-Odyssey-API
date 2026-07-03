package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterCompatibility;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Samples the client-visible water column from canonical volume first, then
 * from vanilla water as the compatibility projection.
 *
 * <p>Keeping this logic in one place prevents shoreline overlays, open-ocean
 * replacement quads, and underwater fog from disagreeing about partial fills,
 * flow velocity, or the surface height of the same block.</p>
 */
final class ClientWaterColumnSampler {

    private static final int MIN_FULL_VOLUME_UNITS = WaterVolumeChunk.UNITS_PER_BLOCK * 7 / 8;
    private static final float FULL_WATER_SURFACE_HEIGHT = 0.8888889f;
    private static final float MIN_VISIBLE_DEPTH = 0.05f;

    private ClientWaterColumnSampler() {
    }

    /**
     * Samples the exposed top water cell for one X/Z column using the same
     * heightmap and cover checks as the replacement renderers.
     */
    static ColumnSample sampleExposedSurface(
            ClientLevel level,
            int x,
            int z,
            int maxDepthSample,
            float surfaceOffset
    ) {
        int surfaceBlockY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        BlockPos surfacePos = new BlockPos(x, surfaceBlockY, z);
        if (level.isOutsideBuildHeight(surfacePos) || !level.hasChunkAt(surfacePos)) {
            return ColumnSample.INVALID;
        }

        CellSample cell = sampleCell(level, surfacePos);
        if (!cell.valid || isCovered(level, surfacePos.above())) {
            return ColumnSample.INVALID;
        }

        float depth = scanDepthFromSurface(level, surfacePos, cell.fillFraction, maxDepthSample);
        return new ColumnSample(
                true,
                surfaceBlockY,
                surfaceBlockY + cell.surfaceFillHeight + surfaceOffset,
                depth,
                cell.fullWater,
                cell.fillFraction,
                cell.velocityX,
                cell.velocityY,
                cell.velocityZ,
                cell.canonical,
                cell.replacementSafe
        );
    }

    /** Samples the water state at one block position without checking exposure. */
    static CellSample sampleCell(ClientLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
            return CellSample.INVALID;
        }

        var canonicalCell = CanonicalWater.getTracked(level, pos);
        if (canonicalCell != null) {
            if (canonicalCell.volumeUnits() <= 0) {
                return CellSample.INVALID;
            }
            float fillFraction = clamp(canonicalCell.fillFraction(), 0.0f, 1.0f);
            boolean fullWater = canonicalCell.volumeUnits() >= MIN_FULL_VOLUME_UNITS;
            return new CellSample(
                    true,
                    fullWater,
                    fillFraction,
                    fullWater ? FULL_WATER_SURFACE_HEIGHT : fillFraction,
                    canonicalCell.velocityX(),
                    canonicalCell.velocityY(),
                    canonicalCell.velocityZ(),
                    true,
                    fullWater
            );
        }

        BlockState state = level.getBlockState(pos);
        FluidState fluid = state.getFluidState();
        if (!fluid.is(FluidTags.WATER)) {
            return CellSample.INVALID;
        }

        boolean fullWater = fluid.isSource();
        float ownHeight = clamp(fluid.getOwnHeight(), 0.0f, 1.0f);
        Vec3 flow = fluid.getFlow(level, pos);
        boolean plainSourceWater = fullWater && WaterCompatibility.isPlainWaterProjection(state);
        return new CellSample(
                true,
                fullWater,
                fullWater ? 1.0f : ownHeight,
                fullWater ? FULL_WATER_SURFACE_HEIGHT : ownHeight,
                (float) flow.x,
                0.0f,
                (float) flow.z,
                false,
                plainSourceWater
        );
    }

    /** Returns whether canonical or vanilla-compatible water occupies the block. */
    static boolean hasWater(ClientLevel level, BlockPos pos) {
        return sampleCell(level, pos).valid;
    }

    /**
     * Measures visible water depth by accumulating canonical/vanilla fill
     * fractions downward until terrain, an unloaded block, or air is reached.
     */
    static float scanDepthFromSurface(
            ClientLevel level,
            BlockPos surfacePos,
            float surfaceFillFraction,
            int maxDepthSample
    ) {
        float depth = Math.max(0.0f, surfaceFillFraction);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int offset = 1; offset <= maxDepthSample; offset++) {
            cursor.set(surfacePos.getX(), surfacePos.getY() - offset, surfacePos.getZ());
            if (level.isOutsideBuildHeight(cursor) || !level.hasChunkAt(cursor)) {
                break;
            }

            CellSample cell = sampleCell(level, cursor);
            if (cell.valid) {
                depth += cell.fillFraction;
                continue;
            }

            BlockState state = level.getBlockState(cursor);
            if (!state.getCollisionShape(level, cursor).isEmpty()) {
                break;
            }
            break;
        }
        return Math.max(MIN_VISIBLE_DEPTH, depth);
    }

    private static boolean isCovered(ClientLevel level, BlockPos pos) {
        CellSample aboveWater = sampleCell(level, pos);
        if (aboveWater.valid) {
            return true;
        }
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
            return true;
        }
        BlockState aboveState = level.getBlockState(pos);
        return !aboveState.getCollisionShape(level, pos).isEmpty();
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Exposed water column sample shared by surface renderers. */
    record ColumnSample(
            boolean valid,
            int surfaceBlockY,
            float surfaceY,
            float depth,
            boolean fullWater,
            float fillFraction,
            float velocityX,
            float velocityY,
            float velocityZ,
            boolean canonical,
            boolean replacementSafe
    ) {
        static final ColumnSample INVALID = new ColumnSample(false, 0, 0.0f, 0.0f,
                false, 0.0f, 0.0f, 0.0f, 0.0f, false, false);

        /** Returns whether the open-ocean replacement mesh may hide vanilla top faces here. */
        public boolean replacementSafe() {
            return valid && replacementSafe;
        }
    }

    /** Raw water cell sample used by camera immersion and depth scans. */
    record CellSample(
            boolean valid,
            boolean fullWater,
            float fillFraction,
            float surfaceFillHeight,
            float velocityX,
            float velocityY,
            float velocityZ,
            boolean canonical,
            boolean replacementSafe
    ) {
        static final CellSample INVALID = new CellSample(false, false, 0.0f,
                0.0f, 0.0f, 0.0f, 0.0f, false, false);

        /** Returns bounded three-dimensional water motion in blocks per second. */
        float speed() {
            return (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
        }
    }
}
