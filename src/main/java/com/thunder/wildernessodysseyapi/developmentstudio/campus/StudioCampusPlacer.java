package com.thunder.wildernessodysseyapi.developmentstudio.campus;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.developmentstudio.StudioWorldAccess;
import com.thunder.wildernessodysseyapi.developmentstudio.StudioWorldData;
import com.thunder.wildernessodysseyapi.worldgen.structure.NBTStructurePlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;

/** Places the data-driven Phase 1 campus once near the real Overworld spawn. */
public final class StudioCampusPlacer {
    private static final NBTStructurePlacer CAMPUS_PLACER = new NBTStructurePlacer(
            StudioCampusLayout.TEMPLATE_ID
    );

    private StudioCampusPlacer() {
    }

    /** Attempts one bounded template placement and persists its exact origin. */
    public static boolean placeIfNeeded(ServerLevel level) {
        if (!StudioWorldAccess.isDevelopmentStudioWorld(level.getServer())) {
            return false;
        }

        StudioWorldData data = StudioWorldData.getOrCreate(level.getServer());
        if (data.isCampusPlaced()) {
            return true;
        }

        Vec3i templateSize = CAMPUS_PLACER.peekSize(level);
        if (templateSize.equals(Vec3i.ZERO)) {
            ModConstants.LOGGER.error("Development Campus template {} is unavailable.", StudioCampusLayout.TEMPLATE_ID);
            return false;
        }

        BlockPos anchor = StudioCampusSiteFinder.find(
                level,
                level.getSharedSpawnPos(),
                templateSize,
                StudioCampusLayout.LEVELING_MARKER_OFFSET
        );
        if (anchor == null) {
            ModConstants.LOGGER.warn("No bounded natural site was suitable for the Development Campus near {}.",
                    level.getSharedSpawnPos());
            return false;
        }

        NBTStructurePlacer.PlacementResult result = CAMPUS_PLACER.placeAnchored(level, anchor);
        if (result == null) {
            ModConstants.LOGGER.warn("Development Campus placement failed at {}.", anchor);
            return false;
        }

        data.markCampusPlaced(result.origin());
        StudioLocationRegistry.bootstrapDefaults();
        ModConstants.LOGGER.info("Placed Development Campus {} at {} with bounds {}.",
                StudioCampusLayout.TEMPLATE_ID, result.origin(), result.bounds());
        return true;
    }
}
