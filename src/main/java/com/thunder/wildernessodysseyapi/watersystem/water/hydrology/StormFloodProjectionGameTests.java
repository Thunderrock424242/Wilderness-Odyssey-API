package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.thunder.wildernessodysseyapi.watersystem.water.hydrology.HydrologicReservoir.FLOODPLAIN;
import static com.thunder.wildernessodysseyapi.watersystem.water.hydrology.RegionalHydrologyState.Boundary.UPSTREAM;

/** Actual chunk heightmaps must place funded floodwater above intact ground. */
@GameTestHolder("wildernessodysseyapi_storm_runoff_tests")
@PrefixGameTestTemplate(false)
public final class StormFloodProjectionGameTests {
    private StormFloodProjectionGameTests() { }

    @GameTest(template = "water_flow")
    public static void expandsIntoAirAboveTheHeightmapSurface(GameTestHelper helper) {
        var level = helper.getLevel();
        var chunkPosition = new ChunkPos(helper.absolutePos(new BlockPos(2, 2, 2)));
        // This sole-test namespace owns a known nine-chunk callback halo.
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            level.getChunk(chunkPosition.x + x, chunkPosition.z + z);
        }
        var chunk = level.getChunk(chunkPosition.x, chunkPosition.z);
        int floor = helper.absolutePos(new BlockPos(2, 1, 2)).getY();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            BlockPos ground = new BlockPos(chunkPosition.getMinBlockX() + x, floor, chunkPosition.getMinBlockZ() + z);
            level.setBlock(ground, Blocks.STONE.defaultBlockState(), 2);
            for (int y = 1; y <= 4; y++) level.setBlock(ground.above(y), Blocks.AIR.defaultBlockState(), 2);
        }
        long key = chunkPosition.toLong();
        var data = RegionalHydrologySavedData.get(level);
        var region = data.state(key);
        if (region == null) {
            region = new RegionalHydrologyState(key, level.getGameTime(), floor);
            helper.assertTrue(data.admit(region, Integer.MAX_VALUE), "Could not admit the loaded test region");
        }
        var ledger = TemporaryFloodSavedData.get(level);
        long before = region.totalStored() + ledger.ownedMilliUnits(key);
        long input = 3 * HydrologicStorage.MILLI_UNITS_PER_CUBIC_BLOCK;
        helper.assertTrue(region.credit(FLOODPLAIN, input, UPSTREAM) == input, "Missing finite test funding");
        BlockPos seed = new BlockPos(chunkPosition.getMinBlockX() + 2, floor + 1, chunkPosition.getMinBlockZ() + 2);
        var view = WatershedChunkState.create(key, floor, WatershedConditions.DrainageDirection.SINK,
                WatershedConditions.WaterFeature.RIVER, 1, seed.asLong(), .88f, level.getGameTime());
        view.apply(new WatershedSimulationModel.Result(0, 0, 0, 1, 0, 1, true, 0, 1, 0, 0, 0, 0, 0), .88f, level.getGameTime());
        var metadata = new WatershedSavedData();
        metadata.store(key, view);
        helper.assertTrue(TemporaryFloodManager.placeTrackedSurfaceWater(level, metadata, key, view, seed,
                SurfaceWaterKind.FLOOD, 4096, 0, 0), "Could not place the funded connected seed");
        int added = TemporaryFloodManager.expand(level, metadata, key, view, 2);
        helper.assertTrue(added > 0, "Flood expansion sampled solid ground instead of connected air above it");
        helper.assertTrue(level.getBlockState(seed.below()).is(Blocks.STONE), "Expansion replaced its ground");
        helper.assertTrue(region.totalStored() + ledger.ownedMilliUnits(key) == before + input,
                "Heightmap expansion lost or duplicated its funding");
        helper.assertTrue(region.residualMilliUnits() == 0, "Expanded region has a nonzero residual");
        helper.succeed();
    }
}
