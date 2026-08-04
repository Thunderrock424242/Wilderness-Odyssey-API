package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
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
            return WatershedSavedData.get(serverLevel).conditions(
                    position.getX() >> 4,
                    position.getZ() >> 4
            );
        }
        return ClientWatershedSnapshotStore.get(
                level,
                position.getX() >> 4,
                position.getZ() >> 4
        );
    }
}
