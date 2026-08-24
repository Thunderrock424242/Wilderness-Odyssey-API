package com.thunder.wildernessodysseyapi.simulation.integration;

import com.thunder.wildernessodysseyapi.ecosystem.api.WildlifeSimulationLod;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemRegionSnapshot;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemSimulationManager;
import com.thunder.wildernessodysseyapi.ecosystem.simulation.EcosystemSimulationSettings;
import com.thunder.wildernessodysseyapi.performance.background.ActivityLevel;
import com.thunder.wildernessodysseyapi.simulation.region.SimulationRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Read-only adapter from ecosystem-owned cells/LOD into common orchestration context.
 *
 * <p>The adapter does not move animals, alter population ledgers, or impose
 * ecosystem distances on weather, water, or vegetation. It reuses the existing
 * cell only to deduplicate shared regional requests.</p>
 */
public final class EcosystemSimulationBridge {
    private EcosystemSimulationBridge() {
    }

    /** Creates a common request key aligned to the existing ecosystem cell size. */
    public static SimulationRegion regionAt(ServerLevel level, BlockPos position) {
        return SimulationRegion.fromBlock(
                level.dimension().location(),
                position,
                EcosystemSimulationSettings.DEFAULT_CELL_SIZE
        );
    }

    /** Maps the ecosystem's nearest-player LOD into the existing performance activity vocabulary. */
    public static ActivityLevel activityAt(ServerLevel level, BlockPos position) {
        WildlifeSimulationLod lod = EcosystemSimulationManager.get().getSimulationLevel(level, position);
        return switch (lod) {
            case ACTIVE -> ActivityLevel.ACTIVE;
            case NEAR -> ActivityLevel.NEARBY;
            case DISTANT -> ActivityLevel.BACKGROUND;
            case DORMANT -> ActivityLevel.DORMANT;
        };
    }

    /** Returns the ecosystem owner's immutable regional population view when present. */
    public static Optional<EcosystemRegionSnapshot> snapshotAt(ServerLevel level, BlockPos position) {
        return EcosystemSimulationManager.get().getRegionSnapshot(level, position);
    }
}
