package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Public access point for the server-authoritative Wilderness water volume.
 *
 * <p>Canonical cells own water amount and velocity once a position has been
 * imported or changed. Namespaced Wilderness water is projected from that state
 * so tag-aware Minecraft and third-party systems can continue to detect water
 * without requiring the simulation to write vanilla water blocks forever.</p>
 */
public final class CanonicalWater {

    private static final int VANILLA_LEVELS = 8;
    private static final int VOLUME_PER_VANILLA_LEVEL = WaterVolumeChunk.UNITS_PER_BLOCK / VANILLA_LEVELS;

    private static final Map<ServerLevel, ActiveQueue> ACTIVE_QUEUES =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private CanonicalWater() {
    }

    /** Returns tracked canonical state without importing vanilla water. */
    public static WaterVolumeChunk.WaterCell get(Level level, BlockPos pos) {
        WaterVolumeChunk.WaterCell tracked = getTracked(level, pos);
        return tracked == null ? WaterVolumeChunk.WaterCell.EMPTY : tracked;
    }

    /** Returns tracked state, or {@code null} when vanilla has not been imported. */
    @Nullable
    public static WaterVolumeChunk.WaterCell getTracked(Level level, BlockPos pos) {
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

    /**
     * Imports an existing vanilla water level the first time server logic needs it.
     * Imported cells remain stable until explicitly disturbed by canonical flow.
     */
    public static WaterVolumeChunk.WaterCell getOrImport(ServerLevel level, BlockPos pos) {
        return getOrImport(level, pos, false);
    }

    /**
     * Imports a water cell while preserving whether Minecraft stores that
     * fluid inside another block's waterlogged state.
     *
     * <p>Hosted water is useful for depth, optics, and diagnostics, but it must
     * never be projected back as a standalone water block because that would
     * destroy the block that owns the waterlogged state.</p>
     */
    public static WaterVolumeChunk.WaterCell getOrImport(ServerLevel level, BlockPos pos, boolean hostedWater) {
        LevelChunk chunk = level.getChunkAt(pos);
        var existing = chunk.getExistingData(ModAttachments.WATER_VOLUME);
        if (existing.isPresent() && existing.get().contains(pos)) {
            WaterVolumeChunk.WaterCell cell = existing.get().get(pos);
            if (hostedWater && cell.volumeUnits() > 0 && !cell.hostedWater()) {
                WaterVolumeChunk.WaterCell hosted = cell.withAddedFlags(WaterVolumeChunk.FLAG_HOSTED_WATER);
                existing.get().set(pos, hosted);
                return hosted;
            }
            return cell;
        }

        FluidState fluidState = level.getFluidState(pos);
        if (!fluidState.is(FluidTags.WATER)) {
            return WaterVolumeChunk.WaterCell.EMPTY;
        }

        int flags = hostedWater
                ? WaterVolumeChunk.FLAG_IMPORTED | WaterVolumeChunk.FLAG_HOSTED_WATER
                : WaterVolumeChunk.FLAG_IMPORTED | WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED;
        WaterVolumeChunk.WaterCell imported = WaterVolumeChunk.WaterCell.still(
                WildernessWaterAuthority.volumeUnitsFromFluid(fluidState),
                flags
        );
        WaterVolumeChunk volume = chunk.getData(ModAttachments.WATER_VOLUME);
        volume.set(pos, imported);
        return imported;
    }

    /** Returns true when canonical or compatibility water occupies the position. */
    public static boolean isWater(Level level, BlockPos pos) {
        WaterVolumeChunk.WaterCell cell = get(level, pos);
        return cell.volumeUnits() > 0 || level.getFluidState(pos).is(FluidTags.WATER);
    }

    /** Records one player-placed bucket as a full active canonical cell. */
    public static void placeBucket(ServerLevel level, BlockPos pos) {
        set(level, pos, WaterVolumeChunk.WaterCell.still(
                WaterVolumeChunk.UNITS_PER_BLOCK,
                WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED
        ), true);
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
        if (level.isOutsideBuildHeight(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() || isPlainWaterProjection(state) || state.canBeReplaced();
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
     * Re-applies the vanilla water compatibility projection for one tracked cell.
     *
     * <p>This is primarily used by debug/repair tooling and chunk migration. It
     * does not change canonical volume; it only makes the vanilla block state
     * match the canonical cell so other mods can continue detecting water.</p>
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
        if (level.isOutsideBuildHeight(pos)) {
            return;
        }
        volume(level, pos).set(pos, cell);
        if (projectCompatibility) {
            projectCompatibility(level, pos, cell == null ? 0 : cell.volumeUnits());
        }
        scheduleAround(level, pos);
    }

    /** Schedules a persisted dynamic cell to resume finite-volume processing. */
    public static void schedule(ServerLevel level, BlockPos pos) {
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

    private static void scheduleAround(ServerLevel level, BlockPos pos) {
        schedule(level, pos);
        schedule(level, pos.below());
        schedule(level, pos.above());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            schedule(level, pos.relative(direction));
        }
    }

    // Projects fixed-point volume back to Minecraft's eight fluid levels. New
    // writes use the namespaced Wilderness water block; vanilla water remains
    // accepted here only as a migration/import source for existing worlds.
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
