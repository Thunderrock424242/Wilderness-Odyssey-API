package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.Features;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.BunkerProtectionHandler;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.StructureSpawnTracker;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.Worldedit.WorldEditStructurePlacer;
import com.thunder.wildernessodysseyapi.WorldGen.worldgen.configurable.StructureConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;

/**
 * Feature that places the bunker structure during world generation.
 */
public class BunkerFeature extends Feature<NoneFeatureConfiguration> {
    public BunkerFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        LevelAccessor levelAccessor = context.level();
        if (!ModList.get().isLoaded("worldedit")) return false;
        if (!(levelAccessor instanceof ServerLevel level)) return false;

        BlockPos origin = context.origin();
        StructureSpawnTracker tracker = StructureSpawnTracker.get(level);

        // Skip placement if a bunker already exists at this location
        if (tracker.hasSpawnedAt(origin)) return false;

        // Respect configured spawn distance and max count
        int minDist = StructureConfig.BUNKER_MIN_DISTANCE.get();
        int maxCount = StructureConfig.BUNKER_MAX_COUNT.get();

        if (tracker.getSpawnCount() >= maxCount) return false;
        if (!tracker.isFarEnough(origin, minDist)) return false;

        // Use the bunker schematic from data packs if available, otherwise bundled resource
        if (!WorldEditStructurePlacer.isWorldEditReady()) {
            ModConstants.LOGGER.debug("WorldEdit not ready; skipping bunker feature placement at {}", origin);
            return false;
        }
        WorldEditStructurePlacer placer = new WorldEditStructurePlacer(ModConstants.MOD_ID, "bunker.schem");
        AABB bounds = placer.placeStructure(level, origin);
        if (bounds != null) {
            tracker.addSpawnPos(origin);
            BunkerProtectionHandler.addBunkerBounds(bounds);
            return true;
        }
        return false;
    }
}
