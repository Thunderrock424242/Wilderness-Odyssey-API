package com.thunder.wildernessodysseyapi.watersystem.water.reactive;

import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Optional bounded physical surface patches, invoked only by the canonical flow queue.
 * Membership is disjoint. Caches own face momentum and elapsed time, never depth,
 * volume, claims or projections. Unsupported terrain keeps the existing voxel path.
 */
public final class ReactiveWaterController {

    private static final int MAX_SUBSTEPS = 4;
    private static final int QUIET_TICKS_TO_SLEEP = 20;
    private static final Map<ServerLevel, LevelRegions> LEVELS = new WeakHashMap<>();

    private ReactiveWaterController() { }

    /** Returns whether this invocation belongs to a surface patch instead of voxel flow. */
    public static boolean tryTick(ServerLevel level, BlockPos seed) {
        return tryTick(level, seed, Long.MAX_VALUE);
    }

    /** Uses the existing ticker's absolute time deadline; unprocessed time remains queued. */
    public static boolean tryTick(ServerLevel level, BlockPos seed, long deadlineNanos) {
        if (!level.getServer().isSameThread() || level.getServer().isStopped()) return false;
        if (!WaterSimulationConfig.ENABLE_REACTIVE_SHALLOW_POOLS.get()) {
            LevelRegions old = LEVELS.remove(level);
            if (old != null) for (Region region : old.regions) wake(level, region);
            return false;
        }
        var seedCell = CanonicalWater.getTracked(level, seed);
        if (seedCell == null || seedCell.volumeUnits() <= 0 || seedCell.sleeping()
                || !CanonicalFluxBridge.ordinary(seedCell)) return false;
        LevelRegions state = LEVELS.computeIfAbsent(level, ignored -> new LevelRegions());
        long tick = level.getGameTime();
        if (state.evaluationTick != tick) {
            state.evaluationTick = tick;
            state.evaluated.clear();
        }
        // An accepted terrain edit may remove membership during this tick.
        // Defer those former members rather than advancing them a second time.
        if (state.evaluated.contains(seed)) {
            CanonicalWater.schedule(level, seed);
            return true;
        }
        Region region = state.byCell.get(seed);
        List<BlockPos> sampled = ShallowPoolAdmission.sample(level, seed);
        if (region != null && !region.cells.equals(sampled)) {
            remove(level, state, region);
            region = null;
        }
        if (sampled.isEmpty()) return false;
        if (region == null) {
            if (state.regions.size() >= WaterSimulationConfig.REACTIVE_SHALLOW_POOL_MAX_REGIONS.get()) return false;
            // Topology can join formerly separate patches. Remove their cache
            // ownership before publishing the one connected replacement.
            Set<Region> previous = new LinkedHashSet<>();
            for (BlockPos position : sampled) {
                Region other = state.byCell.get(position);
                if (other != null) previous.add(other);
            }
            for (Region other : previous) remove(level, state, other);
            region = new Region(sampled, tick);
            state.regions.add(region);
            for (BlockPos position : sampled) state.byCell.put(position, region);
        }
        boolean alreadyAdvanced = region.cells.stream().anyMatch(state.evaluated::contains);
        state.evaluated.addAll(region.cells);
        if (!alreadyAdvanced) advance(level, state, region, tick, deadlineNanos);
        if (region.quietTicks >= QUIET_TICKS_TO_SLEEP) {
            for (BlockPos position : region.cells) {
                var cell = CanonicalWater.get(level, position);
                if (cell.volumeUnits() > 0) CanonicalWater.set(level, position,
                        cell.withFlowState(cell.volumeUnits(), 0, 0, 0)
                                .withAddedFlags(WaterVolumeChunk.FLAG_SLEEPING), false, false);
            }
            state.regions.remove(region);
            for (BlockPos position : region.cells) state.byCell.remove(position);
        } else {
            wake(level, region);
        }
        return true;
    }

    private static void advance(ServerLevel level, LevelRegions state, Region region, long tick, long deadline) {
        region.pendingSeconds += Math.max(0, (tick - region.lastTick) / 20.0);
        region.lastTick = tick;
        int moved = 0;
        int substeps = 0;
        for (; substeps < MAX_SUBSTEPS && region.pendingSeconds > 1.0e-10; substeps++) {
            if (System.nanoTime() >= deadline) break;
            int[] volumes = region.cells.stream().mapToInt(position -> CanonicalWater.get(level, position).volumeUnits()).toArray();
            var plan = CanonicalSurfaceFlux.plan(volumes, region.negative, region.positive,
                    region.discharge, region.pendingSeconds);
            region.discharge = plan.discharge();
            for (int face = 0; face < region.negative.length; face++) {
                int signed = plan.signedUnits()[face];
                if (signed == 0) continue;
                int donor = signed > 0 ? region.negative[face] : region.positive[face];
                int receiver = signed > 0 ? region.positive[face] : region.negative[face];
                BlockPos a = region.cells.get(donor);
                BlockPos b = region.cells.get(receiver);
                double speed = Math.min(CanonicalSurfaceFlux.MAXIMUM_SPEED,
                        Math.abs(region.discharge[face]) / Math.max(1.0 / 4096, volumes[donor] / 4096.0));
                int accepted = CanonicalFluxBridge.transfer(level, a, b, Math.abs(signed),
                        (float) (speed * (b.getX() - a.getX())), (float) (speed * (b.getZ() - a.getZ())));
                region.discharge[face] *= accepted / (double) Math.abs(signed);
                moved += accepted;
            }
            region.pendingSeconds = Math.max(0, region.pendingSeconds - plan.seconds());
        }
        if (substeps > 0) state.steps++;
        state.movedUnits += moved;
        boolean quiet = moved == 0 && region.pendingSeconds <= 1.0e-10;
        for (double discharge : region.discharge) quiet &= Math.abs(discharge) < 1.0e-5;
        region.quietTicks = quiet ? region.quietTicks + 1 : 0;
    }

    /** Invalidates indexed topology near an accepted edit, including currently dry members. */
    public static void invalidateTerrain(ServerLevel level, BlockPos position) {
        LevelRegions state = LEVELS.get(level);
        if (state == null) return;
        Set<Region> affected = new LinkedHashSet<>();
        Region at = state.byCell.get(position);
        if (at != null) affected.add(at);
        for (Direction direction : Direction.values()) {
            Region neighbor = state.byCell.get(position.relative(direction));
            if (neighbor != null) affected.add(neighbor);
        }
        for (Region region : affected) remove(level, state, region);
    }

    /** Removes runtime caches before the canonical chunk queue is detached. */
    public static void onChunkUnload(ServerLevel level, ChunkPos chunk) {
        LevelRegions state = LEVELS.get(level);
        if (state == null) return;
        for (Region region : List.copyOf(state.regions)) {
            if (region.cells.stream().anyMatch(position -> Math.abs((position.getX() >> 4) - chunk.x) <= 1
                    && Math.abs((position.getZ() >> 4) - chunk.z) <= 1)) remove(level, state, region);
        }
    }

    /** Level shutdown drops no inventory: all quantities already live in chunk attachments. */
    public static void clearLevel(ServerLevel level) {
        LEVELS.remove(level);
    }

    /** Retires cache ownership after withdrawals or a transition to unsupported provenance. */
    public static void onCanonicalCellChanged(ServerLevel level, BlockPos position) {
        LevelRegions state = LEVELS.get(level);
        Region region = state == null ? null : state.byCell.get(position);
        if (region == null) return;
        var changed = CanonicalWater.getTracked(level, position);
        if (!CanonicalFluxBridge.ordinary(changed)) {
            remove(level, state, region);
        } else if ((changed == null || changed.volumeUnits() == 0) && region.cells.stream()
                .allMatch(cell -> CanonicalWater.get(level, cell).volumeUnits() == 0)) {
            state.regions.remove(region);
            for (BlockPos cell : region.cells) state.byCell.remove(cell);
        }
    }

    /** Read-only counters; this does not admit, import, schedule or repair water. */
    public static Status status(ServerLevel level) {
        LevelRegions state = LEVELS.get(level);
        if (state == null) return new Status(0, 0, 0, 0, 0);
        double deferred = state.regions.stream().mapToDouble(region -> region.pendingSeconds).sum();
        return new Status(state.regions.size(), state.byCell.size(), state.steps, state.movedUnits, deferred);
    }

    /** Read-only position detail for inspection and integrations; zero cells means no surface ownership. */
    public static RegionStatus regionAt(ServerLevel level, BlockPos position) {
        LevelRegions state = LEVELS.get(level);
        Region region = state == null ? null : state.byCell.get(position);
        return region == null ? new RegionStatus(0, 0)
                : new RegionStatus(region.cells.size(), region.pendingSeconds);
    }

    private static void remove(ServerLevel level, LevelRegions state, Region region) {
        state.regions.remove(region);
        for (BlockPos position : region.cells) state.byCell.remove(position);
        wake(level, region);
    }

    private static void wake(ServerLevel level, Region region) {
        for (BlockPos position : region.cells) {
            if (CanonicalWater.get(level, position).volumeUnits() > 0) CanonicalWater.schedule(level, position);
        }
    }

    public record Status(int regions, int cells, long steps, long movedUnits, double deferredSeconds) { }

    public record RegionStatus(int cells, double deferredSeconds) { }

    private static final class LevelRegions {
        final Set<Region> regions = new LinkedHashSet<>();
        final Map<BlockPos, Region> byCell = new HashMap<>();
        final Set<BlockPos> evaluated = new HashSet<>();
        long evaluationTick = Long.MIN_VALUE;
        long steps;
        long movedUnits;
    }

    private static final class Region {
        final List<BlockPos> cells;
        final int[] negative;
        final int[] positive;
        double[] discharge;
        long lastTick;
        double pendingSeconds;
        int quietTicks;

        Region(List<BlockPos> cells, long tick) {
            this.cells = cells;
            lastTick = tick - 1;
            Map<BlockPos, Integer> indexes = new HashMap<>();
            for (int i = 0; i < cells.size(); i++) indexes.put(cells.get(i), i);
            List<Integer> a = new ArrayList<>();
            List<Integer> b = new ArrayList<>();
            for (int i = 0; i < cells.size(); i++) {
                for (Direction direction : new Direction[]{Direction.EAST, Direction.SOUTH}) {
                    Integer neighbor = indexes.get(cells.get(i).relative(direction));
                    if (neighbor != null) { a.add(i); b.add(neighbor); }
                }
            }
            negative = a.stream().mapToInt(Integer::intValue).toArray();
            positive = b.stream().mapToInt(Integer::intValue).toArray();
            discharge = new double[negative.length];
        }
    }
}
