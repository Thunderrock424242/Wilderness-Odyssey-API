package com.thunder.wildernessodysseyapi.developmentstudio.campus;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.developmentstudio.StudioWorldAccess;
import com.thunder.wildernessodysseyapi.developmentstudio.StudioWorldData;
import com.thunder.wildernessodysseyapi.developmentstudio.entity.StudioEntityService;
import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioTestRegionRegistry;
import com.thunder.wildernessodysseyapi.worldgen.structure.NBTStructurePlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Places and version-upgrades the bounded data-driven campus near real Overworld spawn. */
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

        finishPlacement(level, data, result, false);
        return true;
    }

    /**
     * Expands the mod-owned 21x21 scaffold around its existing hub center.
     *
     * <p>This migration runs only in Development Studio worlds whose saved
     * campus version proves they still use the legacy scaffold. The origin is
     * committed only after the replacement template succeeds, so interrupted
     * upgrades retain their prior authoritative metadata.</p>
     */
    public static boolean upgradeLegacyCampus(ServerLevel level, StudioWorldData data) {
        if (level == null || data == null || !data.needsCampusUpgrade()) {
            return false;
        }
        BlockPos legacyOrigin = data.campusOrigin().orElse(null);
        if (legacyOrigin == null) {
            return false;
        }
        CAMPUS_PLACER.reload(level);
        BlockPos upgradedOrigin = StudioCampusLayout.upgradedOrigin(legacyOrigin);
        BlockPos retainedHubAnchor = upgradedOrigin.offset(StudioCampusLayout.LEVELING_MARKER_OFFSET);
        NBTStructurePlacer.PlacementResult result = CAMPUS_PLACER.placeAnchored(level, retainedHubAnchor);
        if (result == null) {
            ModConstants.LOGGER.error(
                    "Development Campus upgrade from version {} to {} failed at {}. Saved metadata was not changed.",
                    data.campusVersion(), StudioCampusLayout.CURRENT_VERSION, legacyOrigin
            );
            return false;
        }
        int removedLegacyEntities = data.testRegion(StudioTestRegionRegistry.ENTITY_LAB)
                .map(region -> StudioEntityService.discardTaggedEntities(level, region))
                .orElse(0);
        finishPlacement(level, data, result, true);
        if (removedLegacyEntities > 0) {
            ModConstants.LOGGER.info("Removed {} tagged entities from the legacy Entity Lab during campus upgrade.",
                    removedLegacyEntities);
        }
        return true;
    }

    private static void finishPlacement(ServerLevel level,
                                        StudioWorldData data,
                                        NBTStructurePlacer.PlacementResult result,
                                        boolean upgraded) {
        // The blue-wool leveling marker is only a template anchor. Restore the
        // operations-hub floor after generic terrain-aware placement consumes it.
        level.setBlock(result.origin().offset(StudioCampusLayout.LEVELING_MARKER_OFFSET),
                Blocks.POLISHED_ANDESITE.defaultBlockState(), 2);
        data.markCampusPlaced(result.origin());
        StudioLocationRegistry.bootstrapDefaults();
        ModConstants.LOGGER.info("{} Development Campus version {} at {} with bounds {}.",
                upgraded ? "Upgraded" : "Placed",
                StudioCampusLayout.CURRENT_VERSION,
                result.origin(),
                result.bounds());
    }
}
