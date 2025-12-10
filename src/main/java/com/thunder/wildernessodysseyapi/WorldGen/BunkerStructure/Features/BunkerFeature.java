package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.Features;

import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.BunkerProtectionHandler;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.StructureSpawnTracker;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.SpawnBlock.CryoSpawnData;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.SpawnBlock.PlayerSpawnHandler;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.SpawnBlock.WorldSpawnHandler;
import com.thunder.wildernessodysseyapi.WorldGen.structure.NBTStructurePlacer;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StructurePlacementDebugger;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StructurePlacementDebugger.PlacementAttempt;
import com.thunder.wildernessodysseyapi.WorldGen.configurable.StructureConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.List;

/**
 * Feature that places the bunker structure during world generation.
 */
public class BunkerFeature extends Feature<NoneFeatureConfiguration> {
    private static final NBTStructurePlacer BUNKER_PLACER =
            new NBTStructurePlacer("wildernessodysseyapi", "bunker");

    public BunkerFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        LevelAccessor levelAccessor = context.level();
        if (!(levelAccessor instanceof ServerLevel level)) return false;

        BlockPos origin = context.origin();
        PlacementAttempt attempt = StructurePlacementDebugger.startAttempt(level, BUNKER_PLACER.id(), BUNKER_PLACER.peekSize(level), origin);

        if (StructureConfig.DEBUG_DISABLE_BUNKER_SPAWNS.get()) {
            StructurePlacementDebugger.markFailure(attempt, "bunker spawns disabled in config");
            return false;
        }

        StructureSpawnTracker tracker = StructureSpawnTracker.get(level);

        if (tracker.hasSpawnedAt(origin)) {
            StructurePlacementDebugger.markFailure(attempt, "already spawned at this anchor");
            return false;
        }

        int minDist = StructureConfig.BUNKER_MIN_DISTANCE.get();
        int maxCount = StructureConfig.BUNKER_MAX_COUNT.get();

        if (tracker.getSpawnCount() >= maxCount) {
            StructurePlacementDebugger.markFailure(attempt, "spawn limit reached");
            return false;
        }
        if (!tracker.isFarEnough(origin, minDist)) {
            StructurePlacementDebugger.markFailure(attempt, "too close to prior bunker");
            return false;
        }

        NBTStructurePlacer.PlacementResult result = BUNKER_PLACER.place(level, origin, attempt);
        if (result == null) {
            StructurePlacementDebugger.markFailure(attempt, "placement failed");
            return false;
        }

        tracker.addSpawnPos(origin);
        BunkerProtectionHandler.addBunkerBounds(result.bounds());

        List<BlockPos> cryoPositions = result.cryoPositions();
        if (!cryoPositions.isEmpty()) {
            CryoSpawnData data = CryoSpawnData.get(level);
            data.replaceAll(cryoPositions);
            PlayerSpawnHandler.setSpawnBlocks(cryoPositions);
            WorldSpawnHandler.refreshWorldSpawn(level);
        }

        return true;
    }
}
