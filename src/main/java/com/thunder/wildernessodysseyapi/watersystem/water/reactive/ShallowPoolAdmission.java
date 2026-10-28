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
import java.util.HashSet;
import java.util.List;

/** Loaded-only, bounded admission of a closed one-layer free-surface domain. */
final class ShallowPoolAdmission {

    private static final int MAX_CELLS = 16;

    private ShallowPoolAdmission() { }

    static List<BlockPos> sample(ServerLevel level, BlockPos seed) {
        var pending = new ArrayDeque<BlockPos>();
        var visited = new HashSet<BlockPos>();
        var cells = new ArrayList<BlockPos>();
        pending.add(seed.immutable());
        while (!pending.isEmpty()) {
            BlockPos position = pending.removeFirst();
            if (!visited.add(position)) continue;
            int kind = kind(level, position);
            if (kind < 0) return List.of();
            if (kind == 0) continue;
            if (cells.size() == MAX_CELLS) return List.of();
            cells.add(position);
            for (Direction direction : Direction.Plane.HORIZONTAL) pending.add(position.relative(direction));
        }
        cells.sort(Comparator.comparingLong(BlockPos::asLong));
        return List.copyOf(cells);
    }

    private static int kind(ServerLevel level, BlockPos position) {
        if (level.isOutsideBuildHeight(position.below()) || level.isOutsideBuildHeight(position.above())
                || !WaterMutationSafety.isReady(level, position, WaterMutationSafety.CALLBACK_RADIUS + 1)) return -1;
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null) return -1;
        var state = chunk.getBlockState(position);
        var cell = CanonicalWater.getTracked(level, position);
        if (!CanonicalFluxBridge.ordinary(cell)) return -1;
        if (!state.isAir() && !WildernessWaterAuthority.isPlainWaterProjection(state)) {
            return state.getFluidState().isEmpty() && !state.canBeReplaced() ? 0 : -1;
        }
        // Generated oceans remain compact. Do not import an untracked physical
        // projection or assign a stale generated baseline to this finite pool.
        if (cell == null && chunk.getExistingData(ModAttachments.GENERATED_WATER)
                .map(generated -> generated.spanAt(position) != null).orElse(false)) return -1;
        if (!state.isAir() && (cell == null || cell.volumeUnits() <= 0)) return -1;
        if (state.isAir() && cell != null && cell.volumeUnits() > 0) return -1;
        var floor = level.getBlockState(position.below());
        if (!floor.getFluidState().isEmpty() || !floor.isCollisionShapeFullBlock(level, position.below())
                || !level.getBlockState(position.above()).isAir()
                || CanonicalWater.get(level, position.above()).volumeUnits() > 0
                || CanonicalWater.get(level, position.below()).volumeUnits() > 0) return -1;
        return 1;
    }
}
