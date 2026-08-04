package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedLocalFlow;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWatershedSnapshotStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Resolves authoritative server or immutable client watershed conditions.
 *
 * <p>The common entry point contains no client-only Minecraft references, so
 * gameplay and shared surface code can query it safely on dedicated servers.</p>
 */
public final class WatershedServices {

    private WatershedServices() {
    }

    /** Returns loaded chunk-scale conditions for a world position. */
    public static WatershedConditions conditions(Level level, BlockPos position) {
        if (level == null || position == null || !WaterSimulationConfig.watershedSimulationEnabled()) {
            return WatershedConditions.NONE;
        }
        if (level instanceof ServerLevel serverLevel) {
            WatershedChunkState state = WatershedSavedData.get(serverLevel).state(
                    net.minecraft.world.level.ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4)
            );
            if (state == null) {
                return WatershedConditions.NONE;
            }
            long canonical = WatershedBasinSavedData.get(serverLevel).resolve(state.localBasinId());
            return state.conditions(canonical);
        }
        return ClientWatershedSnapshotStore.get(
                level,
                position.getX() >> 4,
                position.getZ() >> 4
        );
    }

    /** Returns local four-by-four drainage flow for an already-known chunk. */
    public static WatershedLocalFlow localFlow(Level level, BlockPos position) {
        if (level == null || position == null || !WaterSimulationConfig.watershedSimulationEnabled()) {
            return WatershedLocalFlow.NONE;
        }
        WatershedChunkState state;
        long basinId;
        if (level instanceof ServerLevel serverLevel) {
            state = WatershedSavedData.get(serverLevel).state(
                    net.minecraft.world.level.ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4)
            );
            if (state == null) {
                return WatershedLocalFlow.NONE;
            }
            basinId = WatershedBasinSavedData.get(serverLevel).resolve(state.localBasinId());
        } else {
            state = ClientWatershedSnapshotStore.state(
                    level,
                    position.getX() >> 4,
                    position.getZ() >> 4
            );
            if (state == null) {
                return WatershedLocalFlow.NONE;
            }
            basinId = state.localBasinId();
        }
        WatershedDrainageGrid grid = state.drainageGrid();
        int cell = WatershedDrainageGrid.cell(position.getX(), position.getZ());
        var direction = grid.direction(cell);
        int contributingCells = grid.accumulation(cell);
        WatershedConditions conditions = state.conditions(basinId);
        float strength = conditions.currentStrength()
                * (0.68f + contributingCells / 15.0f * 0.42f)
                * (grid.confluence(cell) ? 1.12f : 1.0f);
        return new WatershedLocalFlow(
                basinId,
                cell,
                direction,
                contributingCells,
                grid.confluence(cell),
                direction.unitX() * strength,
                direction.unitZ() * strength
        );
    }
}
