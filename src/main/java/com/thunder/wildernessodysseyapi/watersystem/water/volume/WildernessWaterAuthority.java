package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Central source-of-truth lens for Wilderness water ownership.
 *
 * <p>Future water features should ask this class "what owns this water cell?"
 * instead of checking {@code Blocks.WATER} or {@code Fluids.WATER} directly.
 * Canonical chunk volume is authoritative. Namespaced Wilderness fluid blocks
 * are compatibility projections of that authority. Plain vanilla water is only
 * an import/migration source, so renderers can avoid treating Minecraft water
 * as their fallback simulation.</p>
 */
public final class WildernessWaterAuthority {

    /** Minimum fixed-point volume that behaves like a full surface block. */
    public static final int MIN_FULL_VOLUME_UNITS = WaterVolumeChunk.UNITS_PER_BLOCK * 7 / 8;
    /** Vanilla's full source-water render height, reused for projection parity. */
    public static final float FULL_WATER_SURFACE_HEIGHT = 0.8888889f;

    private static final int VANILLA_LEVELS = 8;
    private static final int VOLUME_PER_VANILLA_LEVEL = WaterVolumeChunk.UNITS_PER_BLOCK / VANILLA_LEVELS;

    private WildernessWaterAuthority() {
    }

    /**
     * Samples one block without mutating world state.
     *
     * <p>The returned source explains whether the position is truly owned by
     * Wilderness water, merely projected for tag compatibility, or still
     * waiting for vanilla-to-Wilderness migration.</p>
     */
    public static CellAuthority sample(Level level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
            return CellAuthority.DRY;
        }

        WaterVolumeChunk.WaterCell canonical = CanonicalWater.getTracked(level, pos);
        BlockState blockState = level.getBlockState(pos);
        FluidState fluidState = blockState.getFluidState();
        boolean tagWater = fluidState.is(FluidTags.WATER);
        boolean plainProjection = isPlainWaterProjection(blockState);
        if (canonical != null) {
            return fromCanonical(canonical, tagWater, plainProjection);
        }
        if (!tagWater) {
            return CellAuthority.DRY;
        }

        int volumeUnits = volumeUnitsFromFluid(fluidState);
        float fillFraction = volumeUnits / (float) WaterVolumeChunk.UNITS_PER_BLOCK;
        Vec3 flow = fluidState.getFlow(level, pos);
        boolean wildernessProjection = isWildernessProjection(blockState, fluidState);
        boolean vanillaPlain = blockState.is(Blocks.WATER);
        WaterSource source = wildernessProjection
                ? WaterSource.WILDERNESS_PROJECTION
                : vanillaPlain
                        ? WaterSource.VANILLA_MIGRATION_SOURCE
                        : WaterSource.HOSTED_TAGGED_WATER;
        boolean hosted = !plainProjection;
        boolean authorityOwned = wildernessProjection;
        return new CellAuthority(
                source,
                true,
                false,
                authorityOwned,
                authorityOwned,
                !authorityOwned,
                volumeUnits,
                fillFraction,
                fluidState.isSource() ? FULL_WATER_SURFACE_HEIGHT : fillFraction,
                (float) flow.x,
                0.0f,
                (float) flow.z,
                tagWater,
                plainProjection,
                hosted,
                false,
                wildernessProjection
        );
    }

    /**
     * Imports tagged water into canonical storage, then returns the authoritative
     * sample. Use this from server-side migration and interaction paths only.
     */
    public static CellAuthority importIfPresent(ServerLevel level, BlockPos pos, boolean hostedWater) {
        CanonicalWater.getOrImport(level, pos, hostedWater);
        return sample(level, pos);
    }

    /** Returns true when Wilderness, not vanilla, owns simulation/render state. */
    public static boolean ownsWater(Level level, BlockPos pos) {
        return sample(level, pos).authorityOwned();
    }

    /** Returns true when a plain vanilla water block still needs migration. */
    public static boolean isPendingMigration(Level level, BlockPos pos) {
        return sample(level, pos).migrationCandidate();
    }

    /** Returns whether a block state is a standalone water projection block. */
    public static boolean isPlainWaterProjection(BlockState state) {
        return state.is(Blocks.WATER) || state.is(WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get());
    }

    /** Converts Minecraft's eight fluid levels into fixed-point canonical units. */
    public static int volumeUnitsFromFluid(FluidState fluidState) {
        if (!fluidState.is(FluidTags.WATER)) {
            return 0;
        }
        int amount = Math.max(1, Math.min(VANILLA_LEVELS, fluidState.getAmount()));
        return amount * VOLUME_PER_VANILLA_LEVEL;
    }

    private static CellAuthority fromCanonical(
            WaterVolumeChunk.WaterCell canonical,
            boolean tagWater,
            boolean plainProjection
    ) {
        int volumeUnits = Math.max(0, canonical.volumeUnits());
        if (volumeUnits <= 0) {
            return CellAuthority.DRY;
        }
        boolean hosted = canonical.hostedWater();
        float fillFraction = Math.max(0.0f, Math.min(1.0f, canonical.fillFraction()));
        boolean replacementSafe = volumeUnits >= MIN_FULL_VOLUME_UNITS && !hosted;
        return new CellAuthority(
                hosted ? WaterSource.CANONICAL_HOSTED : WaterSource.CANONICAL,
                true,
                true,
                true,
                replacementSafe,
                false,
                volumeUnits,
                fillFraction,
                volumeUnits >= MIN_FULL_VOLUME_UNITS ? FULL_WATER_SURFACE_HEIGHT : fillFraction,
                canonical.velocityX(),
                canonical.velocityY(),
                canonical.velocityZ(),
                tagWater,
                plainProjection,
                hosted,
                canonical.imported(),
                (canonical.flags() & WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED) != 0
        );
    }

    private static boolean isWildernessProjection(BlockState state, FluidState fluidState) {
        return state.is(WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get())
                || fluidState.getType().isSame(WildernessFluidRegistry.WILDERNESS_WATER.get())
                || fluidState.getType().isSame(WildernessFluidRegistry.FLOWING_WILDERNESS_WATER.get());
    }

    /** Explains why a cell is or is not Wilderness-owned. */
    public enum WaterSource {
        /** No water exists at this position. */
        DRY,
        /** Canonical chunk volume owns this normal visible cell. */
        CANONICAL,
        /** Canonical chunk volume tracks hosted water inside another block. */
        CANONICAL_HOSTED,
        /** Namespaced Wilderness fluid exists before/without a client snapshot. */
        WILDERNESS_PROJECTION,
        /** Plain vanilla water remains only as a migration source. */
        VANILLA_MIGRATION_SOURCE,
        /** Tagged water exists in a non-plain host block, such as waterlogged vegetation. */
        HOSTED_TAGGED_WATER
    }

    /** Immutable ownership sample used by rendering, diagnostics, and gameplay hooks. */
    public record CellAuthority(
            WaterSource source,
            boolean water,
            boolean canonicalTracked,
            boolean authorityOwned,
            boolean replacementSurfaceSafe,
            boolean migrationCandidate,
            int volumeUnits,
            float fillFraction,
            float surfaceFillHeight,
            float velocityX,
            float velocityY,
            float velocityZ,
            boolean tagWater,
            boolean plainProjection,
            boolean hostedWater,
            boolean imported,
            boolean compatibilityProjected
    ) {
        /** Shared dry result to avoid allocating for common empty cells. */
        public static final CellAuthority DRY = new CellAuthority(
                WaterSource.DRY,
                false,
                false,
                false,
                false,
                false,
                0,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                false,
                false,
                false,
                false,
                false
        );

        /** Returns true when this sample is a full non-hosted visible surface. */
        public boolean fullSurfaceWater() {
            return volumeUnits >= MIN_FULL_VOLUME_UNITS && !hostedWater;
        }

        /** Returns a compact three-dimensional velocity magnitude. */
        public float speed() {
            return (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
        }
    }
}
