package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Central source-of-truth lens for Wilderness water ownership.
 *
 * <p>Future water features should ask this class "what owns this water cell?"
 * instead of checking {@code Blocks.WATER} or {@code Fluids.WATER} directly.
 * Sparse runtime overrides are authoritative over the compact generated-water
 * baseline. Namespaced Wilderness fluid blocks are the physical projection of
 * those two layers. Vanilla and externally tagged water are observable but do
 * not become Wilderness-owned state.</p>
 */
public final class WildernessWaterAuthority {

    /** Increment when canonical water conversion/storage semantics change. */
    public static final int CURRENT_WATER_SYSTEM_VERSION = 1;
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
     * <p>The returned source explains whether the position is a sparse runtime
     * override, an untouched generated cell, a provisional custom projection,
     * or non-authoritative tagged water.</p>
     */
    public static CellAuthority sample(Level level, BlockPos pos) {
        if (!WildernessWaterRules.isEnabled(level)) {
            return CellAuthority.DRY;
        }
        return sampleCellOnly(level, pos);
    }

    /**
     * Samples explicit sparse, generated, and physical projection state.
     */
    static CellAuthority sampleCellOnly(Level level, BlockPos pos) {
        if (!WildernessWaterRules.isEnabled(level)) {
            return CellAuthority.DRY;
        }
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

        GeneratedWaterChunk.WaterSpan generated = generatedSpanAt(level, pos);
        boolean wildernessProjection = isWildernessProjection(blockState, fluidState);
        if (generated != null && wildernessProjection
                && volumeUnitsFromFluid(fluidState) == generated.amountUnits()) {
            return fromGenerated(generated, tagWater, plainProjection);
        }
        if (wildernessProjection) {
            return fromProjection(level, pos, fluidState, tagWater, plainProjection);
        }

        if (!tagWater) {
            return CellAuthority.DRY;
        }
        int volumeUnits = volumeUnitsFromFluid(fluidState);
        float fillFraction = volumeUnits / (float) WaterVolumeChunk.UNITS_PER_BLOCK;
        Vec3 flow = fluidState.getFlow(level, pos);
        boolean vanillaPlain = blockState.is(Blocks.WATER);
        boolean hosted = !(blockState.getBlock() instanceof LiquidBlock);
        WaterSource source = vanillaPlain
                ? WaterSource.VANILLA_TAGGED_WATER
                : hosted ? WaterSource.HOSTED_TAGGED_WATER : WaterSource.EXTERNAL_TAGGED_WATER;
        return new CellAuthority(
                source,
                true,
                false,
                false,
                false,
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
                false
        );
    }

    /**
     * Materializes generated or provisional Wilderness water for a runtime interaction.
     *
     * @deprecated External tagged water is intentionally non-authoritative. New
     * callers should use explicit volume mutation methods instead.
     */
    @Deprecated
    public static CellAuthority importIfPresent(ServerLevel level, BlockPos pos, boolean hostedWater) {
        CanonicalWater.getOrImport(level, pos, hostedWater);
        return sample(level, pos);
    }

    /**
     * Returns whether authoritative Wilderness water exists at the position.
     *
     * <p>Vanilla and external tagged water are deliberately not treated as
     * gameplay water. Sparse runtime state, generated metadata, and namespaced
     * Wilderness projections are the only owned sources.</p>
     */
    public static boolean isWaterAt(Level level, BlockPos pos) {
        return isWOWaterAt(level, pos);
    }

    /** Returns whether Wilderness authority owns water at the position. */
    public static boolean isWOWaterAt(Level level, BlockPos pos) {
        CellAuthority authority = sample(level, pos);
        return authority.water() && authority.authorityOwned();
    }

    /** Returns true when Wilderness, not vanilla, owns simulation/render state. */
    public static boolean ownsWater(Level level, BlockPos pos) {
        return sample(level, pos).authorityOwned();
    }

    /** Returns the approximate contiguous water depth starting at this block. */
    public static float getWaterDepth(Level level, BlockPos surfacePos) {
        return getWaterDepth(level, surfacePos, 64);
    }

    /**
     * Returns the approximate contiguous water depth starting at this block.
     *
     * <p>The depth is measured in block units through Wilderness-owned cells.
     * Vanilla and external tagged water remain non-authoritative.</p>
     */
    public static float getWaterDepth(Level level, BlockPos surfacePos, int maxDepth) {
        HybridWaterBodyModel.LargeBodyCell largeBody = HybridWaterBodyModel.sampleCell(level, surfacePos);
        if (largeBody.valid()) {
            float depthFromPosition = Math.max(0.0f, surfacePos.getY() - largeBody.column().floorY());
            return Math.min(Math.max(1, maxDepth), depthFromPosition);
        }

        float depth = 0.0f;
        int boundedDepth = Math.max(1, maxDepth);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int offset = 0; offset < boundedDepth; offset++) {
            cursor.set(surfacePos.getX(), surfacePos.getY() - offset, surfacePos.getZ());
            CellAuthority authority = sample(level, cursor);
            if (!authority.water() || !authority.authorityOwned()) {
                break;
            }
            depth += authority.fillFraction();
        }
        return depth;
    }

    /**
     * Returns the visible surface Y for Wilderness water at this block, or
     * {@link Float#NaN} when no Wilderness-owned water exists.
     */
    public static float getWaterSurfaceHeight(Level level, BlockPos pos) {
        if (!isWaterAt(level, pos)) {
            return Float.NaN;
        }
        float columnSurface = getSurfaceHeight(level, pos.getX() + 0.5, pos.getZ() + 0.5, 0.0f);
        if (!Float.isNaN(columnSurface)) {
            return columnSurface;
        }
        CellAuthority authority = sample(level, pos);
        if (!authority.water() || !authority.authorityOwned()) {
            return Float.NaN;
        }
        return pos.getY() + authority.surfaceFillHeight();
    }

    /**
     * Returns the animated authoritative surface height for the column.
     *
     * <p>The height combines the generated column baseline, tide, wave profile,
     * and local disturbance. {@link Float#NaN} means the loaded column is not
     * currently owned by Wilderness water.</p>
     */
    public static float getSurfaceHeight(Level level, double x, double z, float partialTick) {
        HybridWaterBodyModel.SurfaceSample sample = HybridWaterBodyModel.sampleSurface(level, x, z, partialTick);
        return sample.valid() ? sample.surfaceHeight() : Float.NaN;
    }

    /**
     * Returns the complete primitive surface sample used by the public query API.
     *
     * <p>Keeping this conversion inside the authority prevents API and
     * compatibility packages from depending on the package-private hybrid body
     * implementation. The record contains no Minecraft object references and is
     * safe to reuse as a short-lived query result.</p>
     */
    public static SurfaceAuthority sampleSurface(Level level, double x, double z, float partialTick) {
        HybridWaterBodyModel.SurfaceSample sample = HybridWaterBodyModel.sampleSurface(level, x, z, partialTick);
        if (!sample.valid()) {
            return SurfaceAuthority.INVALID;
        }
        HybridWaterBodyModel.SurfaceColumn column = sample.column();
        return new SurfaceAuthority(
                true,
                sample.surfaceHeight(),
                sample.flowX(),
                sample.wave().velocityY(),
                sample.flowZ(),
                sample.wave().normalX(),
                sample.wave().normalY(),
                sample.wave().normalZ(),
                column.chunkX(),
                column.chunkZ(),
                column.surfaceBlockY(),
                column.floorY(),
                column.depth(),
                column.estimatedVolumeUnits(),
                column.waterType().name()
        );
    }

    /** Returns whether the entity's eye is inside Wilderness-owned water. */
    public static boolean isEntitySubmerged(Entity entity) {
        Level level = entity.level();
        float surfaceHeight = getSurfaceHeight(level, entity.getX(), entity.getZ(), 0.0f);
        return !Float.isNaN(surfaceHeight) && entity.getEyeY() <= surfaceHeight;
    }

    /** Returns the fixed-point water amount stored or represented at this cell. */
    public static int getWaterAmount(Level level, BlockPos pos) {
        CellAuthority authority = sample(level, pos);
        return authority.water() && authority.authorityOwned() ? authority.volumeUnits() : 0;
    }

    /** Returns whether this position is represented as a full Wilderness water cell. */
    public static boolean isFullWaterCell(Level level, BlockPos pos) {
        return sample(level, pos).fullSurfaceWater();
    }

    /** Returns whether this position has a non-empty but not full Wilderness water amount. */
    public static boolean isPartialWaterCell(Level level, BlockPos pos) {
        CellAuthority authority = sample(level, pos);
        return authority.water()
                && authority.authorityOwned()
                && authority.volumeUnits() > 0
                && authority.volumeUnits() < MIN_FULL_VOLUME_UNITS;
    }

    /** Returns whether detailed local volume may flow into this block. */
    public static boolean canFlowInto(Level level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() || isPlainWaterProjection(state);
    }

    /**
     * Adds detailed local volume to the Wilderness system.
     *
     * <p>The amount is measured in {@link WaterVolumeChunk#UNITS_PER_BLOCK}
     * fixed-point units. Large bodies remain cheap; added water creates or
     * updates a local sparse cell near the interaction.</p>
     */
    public static boolean addWaterVolume(Level level, BlockPos pos, int amountUnits) {
        return addWaterVolume(level, pos, amountUnits, false) > 0;
    }

    /**
     * Adds or simulates detailed local volume and returns the accepted amount.
     *
     * <p>Simulation never imports legacy blocks or creates chunk attachments.
     * It only inspects loaded state, which makes it safe for fluid handlers to
     * negotiate a transfer before executing it.</p>
     */
    public static int addWaterVolume(Level level, BlockPos pos, int amountUnits, boolean simulate) {
        if (!WildernessWaterRules.isEnabled(level)
                || !(level instanceof ServerLevel serverLevel)
                || amountUnits <= 0
                || !CanonicalWater.canAcceptVolume(serverLevel, pos)) {
            return 0;
        }
        if (simulate) {
            CellAuthority existing = sample(serverLevel, pos);
            return Math.min(amountUnits, Math.max(0, WaterVolumeChunk.UNITS_PER_BLOCK - existing.volumeUnits()));
        }
        return CanonicalWater.addVolume(serverLevel, pos, amountUnits, 0.0f, 0.0f, 0.0f);
    }

    /**
     * Removes detailed local volume from the Wilderness system.
     *
     * <p>If generated water owns the position, the mutation materializes only
     * a bounded loaded neighborhood into sparse runtime overrides.</p>
     */
    public static boolean removeWaterVolume(Level level, BlockPos pos, int amountUnits) {
        return removeWaterVolume(level, pos, amountUnits, false) > 0;
    }

    /** Removes or simulates local volume and returns the drained amount. */
    public static int removeWaterVolume(Level level, BlockPos pos, int amountUnits, boolean simulate) {
        if (!WildernessWaterRules.isEnabled(level)
                || !(level instanceof ServerLevel serverLevel)
                || amountUnits <= 0) {
            return 0;
        }
        CellAuthority existing = sample(serverLevel, pos);
        if (!existing.water() || !existing.authorityOwned() || existing.hostedWater()) {
            return 0;
        }
        int transferable = Math.min(amountUnits, existing.volumeUnits());
        if (simulate || transferable <= 0) {
            return transferable;
        }
        return CanonicalWater.drainVolume(serverLevel, pos, transferable);
    }

    /** Returns whether a bucket may pick up one full non-hosted Wilderness water cell. */
    public static boolean canBucketPickup(Level level, BlockPos pos) {
        CellAuthority authority = sample(level, pos);
        return authority.water()
                && authority.authorityOwned()
                && authority.fullSurfaceWater()
                && !authority.hostedWater();
    }

    /** Returns whether a water bucket may place Wilderness water at the position. */
    public static boolean canBucketPlace(Level level, BlockPos pos) {
        if (!WildernessWaterRules.isEnabled(level)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced()
                || isWildernessProjection(state, state.getFluidState());
    }

    /** Returns whether a boat should treat this position as floatable water. */
    public static boolean canBoatFloatAt(Level level, BlockPos pos) {
        return getWaterDepth(level, pos, 3) >= 0.35f;
    }

    /** Returns whether fishing logic should treat this position as fishable water. */
    public static boolean canFishAt(Level level, BlockPos pos) {
        return getWaterDepth(level, pos, 3) >= 1.0f;
    }

    /** Returns whether nearby Wilderness water can hydrate farmland at {@code farmlandPos}. */
    public static boolean canHydrateFarmland(Level level, BlockPos farmlandPos) {
        for (BlockPos candidate : BlockPos.betweenClosed(
                farmlandPos.offset(-4, 0, -4),
                farmlandPos.offset(4, 1, 4)
        )) {
            if (isWaterAt(level, candidate)) {
                return true;
            }
        }
        return false;
    }

    /** Returns whether a block state is a standalone water projection block. */
    public static boolean isPlainWaterProjection(BlockState state) {
        return state.is(Blocks.WATER) || state.is(WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get());
    }

    /** Converts Minecraft's eight fluid levels into fixed-point canonical units. */
    public static int volumeUnitsFromFluid(FluidState fluidState) {
        if (!fluidState.is(FluidTags.WATER)) {
            boolean wildernessFluid = fluidState.getType().isSame(WildernessFluidRegistry.WILDERNESS_WATER.get())
                    || fluidState.getType().isSame(WildernessFluidRegistry.FLOWING_WILDERNESS_WATER.get());
            if (!wildernessFluid) {
                return 0;
            }
        }
        int amount = Math.max(1, Math.min(VANILLA_LEVELS, fluidState.getAmount()));
        return amount * VOLUME_PER_VANILLA_LEVEL;
    }

    static CellAuthority fromCanonical(
            WaterVolumeChunk.WaterCell canonical,
            boolean tagWater,
            boolean plainProjection
    ) {
        int volumeUnits = Math.max(0, canonical.volumeUnits());
        if (volumeUnits <= 0) {
            return new CellAuthority(
                    WaterSource.SPARSE_DRY_OVERRIDE,
                    false,
                    true,
                    false,
                    false,
                    0,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    tagWater,
                    plainProjection,
                    false,
                    false,
                    false
            );
        }
        if (canonical.displacementReservoir()) {
            float fillFraction = Math.max(0.0f, Math.min(1.0f, canonical.fillFraction()));
            return new CellAuthority(
                    WaterSource.DISPLACEMENT_RESERVOIR,
                    false,
                    true,
                    true,
                    false,
                    volumeUnits,
                    fillFraction,
                    0.0f,
                    canonical.velocityX(),
                    canonical.velocityY(),
                    canonical.velocityZ(),
                    tagWater,
                    plainProjection,
                    false,
                    canonical.imported(),
                    false
            );
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

    private static CellAuthority fromGenerated(
            GeneratedWaterChunk.WaterSpan span,
            boolean tagWater,
            boolean plainProjection
    ) {
        int volumeUnits = span.amountUnits();
        float fillFraction = volumeUnits / (float) WaterVolumeChunk.UNITS_PER_BLOCK;
        return new CellAuthority(
                WaterSource.GENERATED,
                true,
                false,
                true,
                volumeUnits >= MIN_FULL_VOLUME_UNITS,
                volumeUnits,
                fillFraction,
                volumeUnits >= MIN_FULL_VOLUME_UNITS ? FULL_WATER_SURFACE_HEIGHT : fillFraction,
                0.0f,
                0.0f,
                0.0f,
                tagWater,
                plainProjection,
                false,
                false,
                true
        );
    }

    private static CellAuthority fromProjection(
            Level level,
            BlockPos pos,
            FluidState fluidState,
            boolean tagWater,
            boolean plainProjection
    ) {
        int volumeUnits = volumeUnitsFromFluid(fluidState);
        float fillFraction = volumeUnits / (float) WaterVolumeChunk.UNITS_PER_BLOCK;
        Vec3 flow = fluidState.getFlow(level, pos);
        return new CellAuthority(
                WaterSource.WILDERNESS_PROJECTION,
                true,
                false,
                true,
                volumeUnits >= MIN_FULL_VOLUME_UNITS && plainProjection,
                volumeUnits,
                fillFraction,
                fluidState.isSource() ? FULL_WATER_SURFACE_HEIGHT : fillFraction,
                (float) flow.x,
                0.0f,
                (float) flow.z,
                tagWater,
                plainProjection,
                !plainProjection,
                false,
                true
        );
    }

    /** Returns the compact generated span at a loaded position, or {@code null}. */
    static GeneratedWaterChunk.WaterSpan generatedSpanAt(Level level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
            return null;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        return chunk.getExistingData(ModAttachments.GENERATED_WATER)
                .map(generated -> generated.spanAt(pos))
                .orElse(null);
    }

    /** Returns whether the physical Wilderness fluid still matches its immutable generated baseline. */
    static boolean matchesGeneratedProjection(Level level, BlockPos pos, GeneratedWaterChunk.WaterSpan span) {
        if (span == null || level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        FluidState fluidState = state.getFluidState();
        return isWildernessProjection(state, fluidState)
                && volumeUnitsFromFluid(fluidState) == span.amountUnits();
    }

    static boolean isWildernessProjection(BlockState state, FluidState fluidState) {
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
        /** Conserved displacement volume is hidden behind a solid and is not occupiable water. */
        DISPLACEMENT_RESERVOIR,
        /** Sparse runtime state intentionally suppresses a generated baseline cell. */
        SPARSE_DRY_OVERRIDE,
        /** Compact world-generation metadata owns this matching physical fluid cell. */
        GENERATED,
        /** Legacy large-body source retained for compatibility with older diagnostics. */
        @Deprecated
        LARGE_BODY,
        /** Namespaced Wilderness fluid exists without matching generated metadata. */
        WILDERNESS_PROJECTION,
        /** Plain vanilla water exists but is not Wilderness-owned. */
        VANILLA_TAGGED_WATER,
        /** A standalone externally registered tagged water fluid is non-authoritative. */
        EXTERNAL_TAGGED_WATER,
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
            return water
                    && authorityOwned
                    && volumeUnits >= MIN_FULL_VOLUME_UNITS
                    && !hostedWater;
        }

        /** Returns a compact three-dimensional velocity magnitude. */
        public float speed() {
            return (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
        }
    }

    /** Primitive large-body surface data exposed to the public authority facade. */
    public record SurfaceAuthority(
            boolean valid,
            float surfaceHeight,
            float currentX,
            float currentY,
            float currentZ,
            float normalX,
            float normalY,
            float normalZ,
            int chunkX,
            int chunkZ,
            int surfaceBlockY,
            int floorY,
            float depth,
            long estimatedVolumeUnits,
            String waterType
    ) {
        /** Shared invalid result for dry or unloaded columns. */
        public static final SurfaceAuthority INVALID = new SurfaceAuthority(
                false,
                Float.NaN,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                1.0f,
                0.0f,
                0,
                0,
                0,
                0,
                0.0f,
                0L,
                "POND"
        );
    }
}
