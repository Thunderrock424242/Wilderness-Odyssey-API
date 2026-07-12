package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Public access point for the server-authoritative Wilderness water volume.
 *
 * <p>Canonical cells own sparse runtime disturbances. Untouched world water
 * remains in {@link GeneratedWaterChunk}; a bounded loaded neighborhood is
 * materialized only when runtime logic changes that generated baseline.</p>
 */
public final class CanonicalWater {

    private static final int VANILLA_LEVELS = 8;
    private static final int VOLUME_PER_VANILLA_LEVEL = WaterVolumeChunk.UNITS_PER_BLOCK / VANILLA_LEVELS;
    private static final int DISPLACEMENT_VERTICAL_SEARCH = 4;
    private static final int DISPLACEMENT_HORIZONTAL_RADIUS = 2;
    private static final int GENERATED_MATERIALIZATION_RADIUS = 1;

    private static final Map<ServerLevel, ActiveQueue> ACTIVE_QUEUES =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private CanonicalWater() {
    }

    /** Returns tracked sparse state without materializing generated water. */
    public static WaterVolumeChunk.WaterCell get(Level level, BlockPos pos) {
        WaterVolumeChunk.WaterCell tracked = getTracked(level, pos);
        return tracked == null ? WaterVolumeChunk.WaterCell.EMPTY : tracked;
    }

    /** Returns tracked sparse state, including explicit generated-water dry overrides. */
    @Nullable
    public static WaterVolumeChunk.WaterCell getTracked(Level level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
            return null;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        var existing = chunk.getExistingData(ModAttachments.WATER_VOLUME);
        if (existing.isEmpty() || !existing.get().contains(pos)) {
            return null;
        }
        return existing.get().get(pos);
    }

    /** Returns whether canonical state has been established for a position. */
    public static boolean isTracked(Level level, BlockPos pos) {
        return getTracked(level, pos) != null;
    }

    /** Materializes generated or provisional Wilderness water for a runtime interaction. */
    public static WaterVolumeChunk.WaterCell getOrImport(ServerLevel level, BlockPos pos) {
        return getOrImport(level, pos, false);
    }

    /**
     * Retains the historical hosted-water parameter for source compatibility.
     * External tagged and waterlogged fluids are not imported by the generated
     * architecture.
     */
    public static WaterVolumeChunk.WaterCell getOrImport(ServerLevel level, BlockPos pos, boolean hostedWater) {
        // Canonical reads must never turn a local simulation or settlement near
        // a chunk edge into a synchronous chunk load. Callers can retry once the
        // destination is naturally loaded by Minecraft's normal tracking path.
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
            return WaterVolumeChunk.WaterCell.EMPTY;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        var existing = chunk.getExistingData(ModAttachments.WATER_VOLUME);
        if (existing.isPresent() && existing.get().contains(pos)) {
            return existing.get().get(pos);
        }

        GeneratedWaterChunk.WaterSpan generated = WildernessWaterAuthority.generatedSpanAt(level, pos);
        if (WildernessWaterAuthority.matchesGeneratedProjection(level, pos, generated)) {
            materializeGeneratedNeighborhood(level, pos);
            WaterVolumeChunk.WaterCell materialized = getTracked(level, pos);
            return materialized == null ? WaterVolumeChunk.WaterCell.EMPTY : materialized;
        }

        BlockState state = level.getBlockState(pos);
        FluidState fluidState = state.getFluidState();
        if (!WildernessWaterAuthority.isWildernessProjection(state, fluidState)) {
            return WaterVolumeChunk.WaterCell.EMPTY;
        }

        WaterVolumeChunk.WaterCell provisional = WaterVolumeChunk.WaterCell.still(
                WildernessWaterAuthority.volumeUnitsFromFluid(fluidState),
                WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED
        );
        chunk.getData(ModAttachments.WATER_VOLUME).set(pos, provisional);
        return provisional;
    }

    /**
     * Returns true when Wilderness authority owns water at the position.
     *
     * @deprecated Ask {@link WildernessWaterAuthority} directly for gameplay and
     * visual ownership checks. This bridge remains only to keep older callers
     * from treating unconverted tagged water as authoritative.
     */
    @Deprecated
    public static boolean isWater(Level level, BlockPos pos) {
        return WildernessWaterAuthority.isWaterAt(level, pos);
    }

    /**
     * Records one player-placed bucket as a full canonical Wilderness cell.
     *
     * <p>A bucket on flat ground should behave like a stable local reservoir,
     * not spread itself into an invisible one-pixel film. We only wake the
     * finite-volume ticker immediately when the placement has an obvious
     * downhill outlet or touches already-active local water.</p>
     */
    public static void placeBucket(ServerLevel level, BlockPos pos) {
        if (!WildernessWaterRules.isEnabled(level)) {
            return;
        }
        boolean activePlacement = shouldActivateBucketPlacement(level, pos);
        int flags = WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED;
        if (!activePlacement) {
            flags |= WaterVolumeChunk.FLAG_SLEEPING;
        }
        set(level, pos, WaterVolumeChunk.WaterCell.still(
                WaterVolumeChunk.UNITS_PER_BLOCK,
                flags
        ), true, activePlacement);
    }

    /**
     * Returns whether a freshly placed bucket should start finite-volume flow.
     *
     * <p>This keeps ordinary flat-ground placement stable while still allowing
     * buckets on ledges, waterfalls, drains, leaks, or disturbed neighboring
     * water to move right away.</p>
     */
    private static boolean shouldActivateBucketPlacement(ServerLevel level, BlockPos pos) {
        if (canAcceptVolume(level, pos.below())) {
            return true;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            if (canAcceptVolume(level, side) && canAcceptVolume(level, side.below())) {
                return true;
            }

            WaterVolumeChunk.WaterCell neighbour = getTracked(level, side);
            if (neighbour != null
                    && neighbour.volumeUnits() > 0
                    && !neighbour.imported()
                    && !neighbour.sleeping()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether canonical water may visibly occupy this block position.
     *
     * <p>The same predicate is shared by volume transfer and compatibility
     * projection so a source cell is never drained toward a destination that
     * later rejects the write. Waterlogged host blocks are deliberately
     * excluded because replacing them would destroy unrelated block state.</p>
     */
    public static boolean canAcceptVolume(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() || isPlainWaterProjection(state) || state.canBeReplaced();
    }

    /**
     * Conserves local water when a solid block replaces a water cell.
     *
     * <p>Block placement has already written the solid into the world by the
     * time NeoForge notifies us, so the displaced cell is removed from
     * canonical storage without projecting air over the newly placed block. The
     * volume is then pushed into loaded side cells first. If those cannot accept
     * water, nearby upward cells receive it, which visually reads as the local
     * surface level rising around the obstruction.</p>
     *
     * @return the number of fixed-point water units successfully moved
     */
    public static int displaceForSolidPlacement(
            ServerLevel level,
            BlockPos pos,
            BlockState replacedState,
            BlockState placedState
    ) {
        if (!isDisplacingSolid(level, pos, placedState)) {
            return 0;
        }

        WaterVolumeChunk.WaterCell source = displacementSource(level, pos, replacedState);
        if (source.volumeUnits() <= 0 || source.hostedWater()) {
            return 0;
        }

        if (getTracked(level, pos) != null) {
            set(level, pos, WaterVolumeChunk.WaterCell.EMPTY, false, false);
        }

        int remaining = source.volumeUnits();
        int moved = 0;
        int sideMoved = distributeDisplacedVolume(
                level,
                pos,
                source,
                remaining,
                sideCandidates(pos)
        );
        remaining -= sideMoved;
        moved += sideMoved;

        if (remaining > 0) {
            int raised = distributeDisplacedVolume(
                    level,
                    pos,
                    source,
                    remaining,
                    raisedSurfaceCandidates(pos, sideMoved == 0)
            );
            remaining -= raised;
            moved += raised;
        }

        if (remaining > 0) {
            moved += distributeDisplacedVolume(
                    level,
                    pos,
                    source,
                    remaining,
                    fallbackDisplacementCandidates(pos)
            );
        }
        return moved;
    }

    /** Adds bounded volume and returns the amount accepted by the target cell. */
    public static int addVolume(
            ServerLevel level,
            BlockPos pos,
            int requestedUnits,
            float velocityX,
            float velocityY,
            float velocityZ
    ) {
        if (requestedUnits <= 0) {
            return 0;
        }

        // Canonical water may replace air, replaceable vegetation, and an
        // existing plain-water projection, but it must never hide inside a
        // solid or waterlogged host block where players cannot see it.
        if (!canAcceptVolume(level, pos)) {
            return 0;
        }
        WaterVolumeChunk.WaterCell previous = getOrImport(level, pos);
        int accepted = Math.min(requestedUnits,
                WaterVolumeChunk.UNITS_PER_BLOCK - previous.volumeUnits());
        if (accepted <= 0) {
            return 0;
        }

        int total = previous.volumeUnits() + accepted;
        float previousWeight = previous.volumeUnits() / (float) total;
        float addedWeight = accepted / (float) total;
        set(level, pos, new WaterVolumeChunk.WaterCell(
                total,
                previous.velocityX() * previousWeight + velocityX * addedWeight,
                previous.velocityY() * previousWeight + velocityY * addedWeight,
                previous.velocityZ() * previousWeight + velocityZ * addedWeight,
                WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED,
                previous.temperatureMilliKelvin()
        ), true);
        return accepted;
    }

    /** Drains bounded volume and returns the amount removed. */
    public static int drainVolume(ServerLevel level, BlockPos pos, int requestedUnits) {
        if (requestedUnits <= 0) {
            return 0;
        }
        WaterVolumeChunk.WaterCell previous = getOrImport(level, pos);
        int drained = Math.min(requestedUnits, previous.volumeUnits());
        if (drained <= 0) {
            return 0;
        }
        int remaining = previous.volumeUnits() - drained;
        set(level, pos, remaining == 0
                ? WaterVolumeChunk.WaterCell.EMPTY
                : new WaterVolumeChunk.WaterCell(
                        remaining,
                        previous.velocityX(),
                        previous.velocityY(),
                        previous.velocityZ(),
                        WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED,
                        previous.temperatureMilliKelvin()
                ), true);
        return drained;
    }

    /**
     * Re-applies the namespaced physical projection for one tracked cell.
     *
     * <p>This is primarily used by debug/repair tooling. It
     * does not change sparse volume; it only makes the physical Wilderness
     * block state match the tracked cell.</p>
     */
    public static void reprojectCompatibility(ServerLevel level, BlockPos pos) {
        WaterVolumeChunk.WaterCell cell = get(level, pos);
        projectCompatibility(level, pos, cell.volumeUnits());
    }

    /** Replaces one cell and optionally updates the vanilla compatibility block. */
    public static void set(
            ServerLevel level,
            BlockPos pos,
            WaterVolumeChunk.WaterCell cell,
            boolean projectCompatibility
    ) {
        set(level, pos, cell, projectCompatibility, true);
    }

    /**
     * Replaces one cell and optionally schedules neighboring active-flow work.
     *
     * <p>Sleeping cells use this with scheduling disabled so the act of going
     * dormant does not immediately put them back into the active queue.</p>
     */
    public static void set(
            ServerLevel level,
            BlockPos pos,
            WaterVolumeChunk.WaterCell cell,
            boolean projectCompatibility,
            boolean scheduleUpdates
    ) {
        // Runtime water writes are restricted to already-loaded chunks. This is
        // especially important for SPH settlement, whose bounded search can
        // cross a chunk edge while the neighboring chunk is absent.
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
            return;
        }
        GeneratedWaterChunk.WaterSpan generated = WildernessWaterAuthority.generatedSpanAt(level, pos);
        if (generated != null) {
            materializeGeneratedNeighborhood(level, pos);
        }
        WaterVolumeChunk.WaterCell effectiveCell = generatedOverrideCell(cell, generated != null);
        volume(level, pos).set(pos, effectiveCell);
        if (projectCompatibility) {
            projectCompatibility(level, pos, effectiveCell.volumeUnits());
        }
        if (scheduleUpdates) {
            scheduleAround(level, pos);
        }
    }

    /**
     * Materializes a bounded loaded neighborhood around disturbed generated water.
     *
     * <p>The copied cells begin asleep and consume no finite-volume tick budget
     * until the normal mutation path wakes the affected positions. No chunk is
     * force-loaded and untouched generated interiors remain span-backed.</p>
     */
    public static void materializeGeneratedNeighborhood(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int offsetX = -GENERATED_MATERIALIZATION_RADIUS;
             offsetX <= GENERATED_MATERIALIZATION_RADIUS; offsetX++) {
            for (int offsetY = -GENERATED_MATERIALIZATION_RADIUS;
                 offsetY <= GENERATED_MATERIALIZATION_RADIUS; offsetY++) {
                for (int offsetZ = -GENERATED_MATERIALIZATION_RADIUS;
                     offsetZ <= GENERATED_MATERIALIZATION_RADIUS; offsetZ++) {
                    cursor.set(center.getX() + offsetX, center.getY() + offsetY, center.getZ() + offsetZ);
                    if (level.isOutsideBuildHeight(cursor) || !level.hasChunkAt(cursor)
                            || getTracked(level, cursor) != null) {
                        continue;
                    }
                    GeneratedWaterChunk.WaterSpan span = WildernessWaterAuthority.generatedSpanAt(level, cursor);
                    if (!WildernessWaterAuthority.matchesGeneratedProjection(level, cursor, span)) {
                        continue;
                    }
                    WaterVolumeChunk.WaterCell generatedCell = WaterVolumeChunk.WaterCell.still(
                            span.amountUnits(),
                            WaterVolumeChunk.FLAG_GENERATED_OVERRIDE
                                    | WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED
                                    | WaterVolumeChunk.FLAG_SLEEPING
                    );
                    volume(level, cursor).set(cursor, generatedCell);
                }
            }
        }
    }

    /** Schedules a persisted dynamic cell to resume finite-volume processing. */
    public static void schedule(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
            return;
        }
        WaterVolumeChunk.WaterCell tracked = getTracked(level, pos);
        if (tracked != null && tracked.sleeping()) {
            volume(level, pos).set(pos, tracked.withoutFlags(WaterVolumeChunk.FLAG_SLEEPING));
        }
        queue(level).offer(pos.immutable());
    }

    /** Polls one active cell for the finite-volume ticker. */
    public static BlockPos pollActive(ServerLevel level) {
        return queue(level).poll();
    }

    /** Releases runtime queues when a server dimension unloads. */
    public static void clearLevel(ServerLevel level) {
        ACTIVE_QUEUES.remove(level);
    }

    private static WaterVolumeChunk volume(Level level, BlockPos pos) {
        return level.getChunkAt(pos).getData(ModAttachments.WATER_VOLUME);
    }

    private static WaterVolumeChunk.WaterCell generatedOverrideCell(
            WaterVolumeChunk.WaterCell cell,
            boolean generatedBaseline
    ) {
        WaterVolumeChunk.WaterCell sanitized = cell == null ? WaterVolumeChunk.WaterCell.EMPTY : cell.sanitized();
        if (!generatedBaseline) {
            return sanitized;
        }
        int flags = sanitized.flags() | WaterVolumeChunk.FLAG_GENERATED_OVERRIDE;
        if (sanitized.volumeUnits() <= 0) {
            flags |= WaterVolumeChunk.FLAG_DRY_OVERRIDE;
        } else {
            flags &= ~WaterVolumeChunk.FLAG_DRY_OVERRIDE;
        }
        return new WaterVolumeChunk.WaterCell(
                sanitized.volumeUnits(),
                sanitized.velocityX(),
                sanitized.velocityY(),
                sanitized.velocityZ(),
                flags,
                sanitized.temperatureMilliKelvin()
        ).sanitized();
    }

    private static WaterVolumeChunk.WaterCell displacementSource(
            ServerLevel level,
            BlockPos pos,
            BlockState replacedState
    ) {
        WaterVolumeChunk.WaterCell tracked = getTracked(level, pos);
        if (tracked != null && tracked.volumeUnits() > 0) {
            return tracked.withoutFlags(WaterVolumeChunk.FLAG_SLEEPING);
        }

        if (!isPlainWaterProjection(replacedState)) {
            return WaterVolumeChunk.WaterCell.EMPTY;
        }
        int volumeUnits = WildernessWaterAuthority.volumeUnitsFromFluid(replacedState.getFluidState());
        return WaterVolumeChunk.WaterCell.still(
                volumeUnits,
                WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED
        );
    }

    private static boolean isDisplacingSolid(ServerLevel level, BlockPos pos, BlockState placedState) {
        if (placedState.isAir()
                || placedState.canBeReplaced()
                || isPlainWaterProjection(placedState)
                || placedState.getFluidState().is(FluidTags.WATER)) {
            return false;
        }
        return !placedState.getCollisionShape(level, pos).isEmpty();
    }

    private static int distributeDisplacedVolume(
            ServerLevel level,
            BlockPos sourcePos,
            WaterVolumeChunk.WaterCell source,
            int requestedUnits,
            List<BlockPos> candidates
    ) {
        int remaining = requestedUnits;
        int moved = 0;
        for (int index = 0; index < candidates.size() && remaining > 0; index++) {
            BlockPos target = candidates.get(index);
            int candidatesLeft = candidates.size() - index;
            int requestedForTarget = Math.max(1, (remaining + candidatesLeft - 1) / candidatesLeft);
            int accepted = addVolume(
                    level,
                    target,
                    requestedForTarget,
                    displacementVelocityX(sourcePos, target, source),
                    displacementVelocityY(sourcePos, target, source),
                    displacementVelocityZ(sourcePos, target, source)
            );
            remaining -= accepted;
            moved += accepted;
        }
        return moved;
    }

    private static float displacementVelocityX(
            BlockPos sourcePos,
            BlockPos targetPos,
            WaterVolumeChunk.WaterCell source
    ) {
        int deltaX = Integer.compare(targetPos.getX(), sourcePos.getX());
        return source.velocityX() * 0.35f + deltaX * 0.22f;
    }

    private static float displacementVelocityY(
            BlockPos sourcePos,
            BlockPos targetPos,
            WaterVolumeChunk.WaterCell source
    ) {
        int deltaY = Math.max(0, targetPos.getY() - sourcePos.getY());
        return source.velocityY() * 0.25f + deltaY * 0.18f;
    }

    private static float displacementVelocityZ(
            BlockPos sourcePos,
            BlockPos targetPos,
            WaterVolumeChunk.WaterCell source
    ) {
        int deltaZ = Integer.compare(targetPos.getZ(), sourcePos.getZ());
        return source.velocityZ() * 0.35f + deltaZ * 0.22f;
    }

    private static List<BlockPos> sideCandidates(BlockPos pos) {
        List<BlockPos> candidates = new ArrayList<>(4);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            candidates.add(pos.relative(direction));
        }
        return candidates;
    }

    private static List<BlockPos> raisedSurfaceCandidates(BlockPos pos, boolean prioritizeColumn) {
        List<BlockPos> candidates = new ArrayList<>(6);
        if (prioritizeColumn) {
            candidates.add(pos.above());
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            candidates.add(pos.above().relative(direction));
        }
        if (!prioritizeColumn) {
            candidates.add(pos.above());
        }
        candidates.add(pos.above(2));
        return candidates;
    }

    private static List<BlockPos> fallbackDisplacementCandidates(BlockPos pos) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int yOffset = 0; yOffset <= DISPLACEMENT_VERTICAL_SEARCH; yOffset++) {
            for (int radius = 1; radius <= DISPLACEMENT_HORIZONTAL_RADIUS; radius++) {
                for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                    addFallbackCandidate(candidates, pos, xOffset, yOffset, -radius);
                    addFallbackCandidate(candidates, pos, xOffset, yOffset, radius);
                }
                for (int zOffset = -radius + 1; zOffset <= radius - 1; zOffset++) {
                    addFallbackCandidate(candidates, pos, -radius, yOffset, zOffset);
                    addFallbackCandidate(candidates, pos, radius, yOffset, zOffset);
                }
            }
        }
        for (int yOffset = 3; yOffset <= DISPLACEMENT_VERTICAL_SEARCH; yOffset++) {
            candidates.add(pos.above(yOffset));
        }
        return candidates;
    }

    private static void addFallbackCandidate(
            List<BlockPos> candidates,
            BlockPos pos,
            int xOffset,
            int yOffset,
            int zOffset
    ) {
        if (xOffset == 0 && yOffset == 0 && zOffset == 0) {
            return;
        }
        candidates.add(pos.offset(xOffset, yOffset, zOffset));
    }

    private static void scheduleAround(ServerLevel level, BlockPos pos) {
        schedule(level, pos);
        schedule(level, pos.below());
        schedule(level, pos.above());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            schedule(level, pos.relative(direction));
        }
    }

    // Projects fixed-point volume back to the namespaced Wilderness fluid's
    // eight physical levels.
    private static void projectCompatibility(ServerLevel level, BlockPos pos, int volumeUnits) {
        BlockState current = level.getBlockState(pos);
        if (volumeUnits <= 0) {
            if (isPlainWaterProjection(current)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            return;
        }
        if (!current.isAir() && !isPlainWaterProjection(current) && !current.canBeReplaced()) {
            return;
        }

        int amount = Math.max(1, Math.min(VANILLA_LEVELS,
                (volumeUnits + VOLUME_PER_VANILLA_LEVEL - 1) / VOLUME_PER_VANILLA_LEVEL));
        BlockState projected = amount >= VANILLA_LEVELS
                ? WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get().defaultBlockState()
                : WildernessFluidRegistry.WILDERNESS_WATER.get().getFlowing(amount, false).createLegacyBlock();
        if (!current.equals(projected)) {
            level.setBlock(pos, projected, 3);
        }
    }

    private static boolean isPlainWaterProjection(BlockState state) {
        return WildernessWaterAuthority.isPlainWaterProjection(state);
    }

    private static ActiveQueue queue(ServerLevel level) {
        return ACTIVE_QUEUES.computeIfAbsent(level, ignored -> new ActiveQueue());
    }

    private static final class ActiveQueue {
        private final ArrayDeque<BlockPos> positions = new ArrayDeque<>();
        private final Set<BlockPos> queued = new java.util.HashSet<>();

        private void offer(BlockPos pos) {
            if (queued.add(pos)) {
                positions.addLast(pos);
            }
        }

        private BlockPos poll() {
            BlockPos next = positions.pollFirst();
            if (next != null) {
                queued.remove(next);
            }
            return next;
        }
    }
}
