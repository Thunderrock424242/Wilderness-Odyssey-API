package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedLocalFlow;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.network.ClientWatershedSnapshotStore;
import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.RiverReachServices;
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
        if (level == null || position == null || !WildernessWaterRules.isEnabled(level)
                || !WaterSimulationConfig.watershedSimulationEnabled()) {
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
        if (level == null || position == null || !WildernessWaterRules.isEnabled(level)
                || !WaterSimulationConfig.watershedSimulationEnabled()) {
            return WatershedLocalFlow.NONE;
        }
        WatershedChunkState state;
        long basinId;
        if (level instanceof ServerLevel serverLevel) {
            if (serverLevel.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) == null) {
                return WatershedLocalFlow.NONE;
            }
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
        int contributingCells = grid.accumulation(cell);
        WatershedConditions conditions = state.conditions(basinId);
        float currentX = conditions.currentX();
        float currentZ = conditions.currentZ();
        if (WaterSimulationConfig.smoothRiverCurrents() && river(conditions)) {
            var home = new RiverCurrentField.Sample(basinId, currentX, currentZ, true);
            var current = RiverCurrentField.interpolate(position.getX() + 0.5, position.getZ() + 0.5,
                    home, (x, z) -> cachedRiverCurrent(level, x, z));
            currentX = current.currentX();
            currentZ = current.currentZ();
        }
        var reach = RiverReachServices.sample(level, position);
        if (reach != null) {
            var current = RiverReachServices.current(
                    reach.reach(), currentX, currentZ);
            currentX = current.currentX();
            currentZ = current.currentZ();
        }
        // Direction is retained as compact D8 inspection metadata. The actual
        // vector may be continuous and never gains heuristic accumulation speed.
        var direction = conditions.downstreamDirection();
        return new WatershedLocalFlow(
                basinId,
                cell,
                direction,
                contributingCells,
                grid.confluence(cell),
                currentX,
                currentZ
        );
    }

    private static RiverCurrentField.Sample cachedRiverCurrent(Level level, int chunkX, int chunkZ) {
        WatershedConditions conditions;
        if (level instanceof ServerLevel server) {
            if (server.getChunkSource().getChunkNow(chunkX, chunkZ) == null) return null;
            var state = WatershedSavedData.get(server).state(net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ));
            if (state == null) return null;
            long basin = WatershedBasinSavedData.get(server).resolve(state.localBasinId());
            conditions = state.conditions(basin);
        } else {
            conditions = ClientWatershedSnapshotStore.get(level, chunkX, chunkZ);
        }
        return river(conditions) ? new RiverCurrentField.Sample(conditions.basinId(),
                conditions.currentX(), conditions.currentZ(), true) : null;
    }

    private static boolean river(WatershedConditions conditions) {
        return conditions.waterFeature() == WatershedConditions.WaterFeature.RIVER
                || conditions.waterFeature() == WatershedConditions.WaterFeature.STREAM;
    }
}
