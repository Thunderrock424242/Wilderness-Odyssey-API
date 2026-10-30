package com.thunder.wildernessodysseyapi.watersystem.water.reactive;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge.WaterMutationSafety;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;

/** Loaded-only admission of connected, packed surface columns with verified face openings. */
final class ShallowPoolAdmission {

    private static final int MAX_COLUMNS = 32;
    private static final int MAX_DEPTH = 8;

    private ShallowPoolAdmission() { }

    static List<BlockPos> sample(ServerLevel level, BlockPos seed) {
        SurfaceRegionLayout layout = sampleColumns(level, seed, null);
        return layout == null ? List.of() : layout.cells();
    }

    static SurfaceRegionLayout sampleColumns(ServerLevel level, BlockPos seed, Integer fixedCeiling) {
        Sampler sampler = new Sampler(level);
        int ceiling = fixedCeiling == null ? seed.getY() + 2 : fixedCeiling;
        if (fixedCeiling == null) {
            for (int offset = 1; offset <= MAX_DEPTH; offset++) {
                BlockPos above = seed.above(offset);
                if (sampler.kind(above) < 0) return null;
                if (CanonicalWater.get(level, above).volumeUnits() == 0) break;
                ceiling = above.getY() + 2;
            }
        }
        SurfaceColumn first = sampler.column(seed, ceiling);
        if (first == null || sampler.failed) return null;
        Map<Long, SurfaceColumn> columns = new LinkedHashMap<>();
        var pending = new ArrayDeque<SurfaceColumn>();
        columns.put(key(first.bottom()), first);
        pending.add(first);
        while (!pending.isEmpty()) {
            SurfaceColumn column = pending.removeFirst();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                SurfaceColumn neighbor = null;
                for (int y = column.bottom().getY(); y < column.topY(); y++) {
                    BlockPos entry = new BlockPos(column.bottom().getX() + direction.getStepX(), y,
                            column.bottom().getZ() + direction.getStepZ());
                    int kind = sampler.kind(entry);
                    if (kind < 0) return null;
                    if (kind == 0) continue;
                    if (neighbor == null) neighbor = sampler.column(entry, ceiling);
                    if (sampler.failed || neighbor == null || !neighbor.contains(entry)) return null;
                }
                if (neighbor == null) continue;
                SurfaceColumn existing = columns.get(key(neighbor.bottom()));
                if (existing != null) {
                    if (!existing.equals(neighbor)) return null;
                } else {
                    if (columns.size() == MAX_COLUMNS) return null;
                    columns.put(key(neighbor.bottom()), neighbor);
                    pending.add(neighbor);
                }
            }
        }
        var ordered = new ArrayList<>(columns.values());
        ordered.sort(Comparator.comparingLong(column -> key(column.bottom())));
        return new SurfaceRegionLayout(ceiling, ordered);
    }

    static long key(BlockPos position) { return BlockPos.asLong(position.getX(), 0, position.getZ()); }

    private static final class Sampler {
        private final ServerLevel level;
        private final Map<BlockPos, Integer> kinds = new HashMap<>();
        private boolean failed;

        private Sampler(ServerLevel level) { this.level = level; }

        private SurfaceColumn column(BlockPos entry, int ceiling) {
            if (entry.getY() >= ceiling || kind(entry) != 1) { failed = true; return null; }
            int bottom = entry.getY();
            while (true) {
                BlockPos below = new BlockPos(entry.getX(), bottom - 1, entry.getZ());
                int kind = kind(below);
                if (kind < 0) return null;
                if (kind == 0) {
                    if (!level.getBlockState(below).isCollisionShapeFullBlock(level, below)) failed = true;
                    break;
                }
                bottom--;
                if (entry.getY() - bottom + 1 > MAX_DEPTH) { failed = true; return null; }
            }
            if (failed) return null;
            int topLimit = Math.min(ceiling, bottom + MAX_DEPTH);
            int top = entry.getY() + 1;
            while (top < topLimit && kind(new BlockPos(entry.getX(), top, entry.getZ())) == 1) top++;
            if (failed) return null;
            if (top == topLimit) {
                BlockPos outside = new BlockPos(entry.getX(), top, entry.getZ());
                if (kind(outside) < 0 || CanonicalWater.get(level, outside).volumeUnits() > 0) {
                    failed = true;
                    return null;
                }
            }
            boolean partial = false;
            for (int y = bottom; y < top; y++) {
                int volume = CanonicalWater.get(level, new BlockPos(entry.getX(), y, entry.getZ())).volumeUnits();
                if (partial && volume > 0) { failed = true; return null; }
                partial |= volume < com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk.UNITS_PER_BLOCK;
            }
            return new SurfaceColumn(new BlockPos(entry.getX(), bottom, entry.getZ()), top - bottom);
        }

        private int kind(BlockPos position) {
            return kinds.computeIfAbsent(position.immutable(), this::readKind);
        }

        private int readKind(BlockPos position) {
            int result = readLoaded(position);
            if (result < 0) failed = true;
            return result;
        }

        private int readLoaded(BlockPos position) {
            if (level.isOutsideBuildHeight(position)
                    || !WaterMutationSafety.isReady(level, position, WaterMutationSafety.CALLBACK_RADIUS + 1)) return -1;
            var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
            if (chunk == null) return -1;
            var state = chunk.getBlockState(position);
            var cell = CanonicalWater.getTracked(level, position);
            if (!CanonicalFluxBridge.ordinary(cell)) return -1;
            if (!state.isAir() && !WildernessWaterAuthority.isPlainWaterProjection(state)) {
                return state.getFluidState().isEmpty() && !state.canBeReplaced() ? 0 : -1;
            }
            if (cell == null && chunk.getExistingData(ModAttachments.GENERATED_WATER)
                    .map(generated -> generated.spanAt(position) != null).orElse(false)) return -1;
            if (!state.isAir() && (cell == null || cell.volumeUnits() <= 0)) return -1;
            if (state.isAir() && cell != null && cell.volumeUnits() > 0) return -1;
            return 1;
        }
    }
}
