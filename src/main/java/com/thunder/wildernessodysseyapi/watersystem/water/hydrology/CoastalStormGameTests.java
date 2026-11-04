package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.thunder.wildernessodysseyapi.watersystem.water.hydrology.RegionalHydrologyState.Boundary.*;

/** A sole-namespace fixture: explicit storm forcing, real loaded flood projection and recession. */
@GameTestHolder("wildernessodysseyapi_coastal_storm_tests")
@PrefixGameTestTemplate(false)
public final class CoastalStormGameTests {
    private CoastalStormGameTests() { }

    @GameTest(template = "water_flow", timeoutTicks = 400)
    public static void fundedSurgeRespectsStageProjectsReloadsAndRecedes(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = new ChunkPos(helper.absolutePos(new BlockPos(2, 2, 2)));
        // Fixture preparation alone loads this known callback halo. Production
        // surge integration and projection never request terrain chunks.
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.getChunk(position.x + x, position.z + z);
        int floor = helper.absolutePos(new BlockPos(2, 1, 2)).getY();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            BlockPos ground = new BlockPos(position.getMinBlockX() + x, floor, position.getMinBlockZ() + z);
            level.setBlock(ground, Blocks.STONE.defaultBlockState(), 2);
            // The shared flow template includes an enclosure above four
            // blocks. Clear its entire bounded height so WORLD_SURFACE probes
            // the prepared shore, independent of the test's chunk alignment.
            for (int y = 1; y <= 20; y++) level.setBlock(ground.above(y), Blocks.AIR.defaultBlockState(), 2);
        }
        long key = position.toLong();
        var data = RegionalHydrologySavedData.get(level);
        var region = data.state(key);
        if (region == null) {
            region = new RegionalHydrologyState(key, level.getGameTime(), floor);
            helper.assertTrue(data.admit(region, Integer.MAX_VALUE), "Could not admit the fixture coast");
        }
        region.ocean = true;
        var ledger = TemporaryFloodSavedData.get(level);
        // This controlled boundary experiment supplies its own historical
        // forcing; ambient scheduling must not overwrite it during the fixture.
        region.lastSimulationTick = level.getGameTime() + 1000;
        region.surgeTargetMetres = .5;
        for (int i = 0; i < 2000; i++) CoastalStormSurge.advance(region, .5, 120, 2, ledger.ownedMilliUnits(key));
        BlockPos seed = new BlockPos(position.getMinBlockX() + 2, floor, position.getMinBlockZ() + 2);
        level.setBlock(seed.below(), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(seed, Blocks.AIR.defaultBlockState(), 2);
        var view = WatershedChunkState.create(key, floor, WatershedConditions.DrainageDirection.SINK,
                WatershedConditions.WaterFeature.COASTAL, 1, seed.asLong(), .88f, level.getGameTime());
        view.apply(new WatershedSimulationModel.Result(0, 0, 0, 0, .5f, 1, true, 0, 1, 0, 0, 0, 0, 0),
                .88f, level.getGameTime());
        var metadata = new WatershedSavedData();
        metadata.store(key, view);
        helper.assertTrue(TemporaryFloodManager.placeTrackedSurfaceWater(level, metadata, key, view, seed,
                SurfaceWaterKind.FLOOD, 4096, 0, 0), "Could not fund the connected sea-edge seed");
        helper.assertTrue(TemporaryFloodManager.expand(level, metadata, key, view, 2) == 0,
                "Half-metre surge placed full blocks above its funded waterline");
        region.surgeTargetMetres = 1.5;
        for (int i = 0; i < 2000; i++) CoastalStormSurge.advance(region, 1.5, 120, 2, ledger.ownedMilliUnits(key));
        int added = 0;
        for (int i = 0; i < 8; i++) added += TemporaryFloodManager.expand(level, metadata, key, view, 2);
        helper.assertTrue(added > 1,
                "Funded higher surge did not reach connected low ground: added=" + added
                        + " stage=" + region.stageMetres() + " seedUnits="
                        + com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater.get(level, seed).volumeUnits()
                        + " raisedUnits=" + com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater
                        .get(level, seed.above()).volumeUnits() + " sourceHeight="
                        + level.getChunk(position.x, position.z).getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 2, 2)
                        + " floor=" + floor + " reference=" + BlockPos.of(view.representativePosition()).getY());
        checkBudget(helper, region, ledger);
        var restored = RegionalHydrologyState.load(region.save());
        data.regions().put(key, restored);
        long funded = restored.receipt(OCEAN_IN);
        for (int i = 0; i < 100; i++) CoastalStormSurge.advance(restored, 1.5, 120, 2, ledger.ownedMilliUnits(key));
        helper.assertTrue(restored.receipt(OCEAN_IN) == funded, "Reload/projection funded surge twice");
        checkBudget(helper, restored, ledger);
        int withdrawn = com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater
                .drainVolume(level, seed.above(), 1024);
        helper.assertTrue(withdrawn == 1024, "Could not partially drain a real funded surge block");
        var active = RegionalHydrologyState.load(restored.save());
        data.regions().put(key, active);
        long beforeReplacement = active.receipt(OCEAN_IN);
        CoastalStormSurge.advance(active, 1.5, 120, 2, ledger.ownedMilliUnits(key));
        helper.assertTrue(active.receipt(OCEAN_IN) > beforeReplacement, "An external withdrawal left phantom target inventory");
        for (int i = 0; i < 2000; i++) CoastalStormSurge.advance(active, 0, 120, 2, ledger.ownedMilliUnits(key));
        helper.startSequence().thenIdle(45).thenWaitUntil(() -> {
                TemporaryFloodManager.recede(level, metadata, 256);
            RegionalHydrologyModel.advance(active, null,
                    new RegionalHydrologyModel.Parameters(0, 0, .045, 1.25, 1800, false, false), 2);
            helper.assertTrue(active.residualMilliUnits() == 0, "Recession has a regional residual");
            helper.assertTrue(active.receipt(PROJECTION_OUT) - active.receipt(PROJECTION_IN)
                    == ledger.ownedMilliUnits(key) + withdrawn * 1000L, "Live claims/exported water disagree");
            helper.assertTrue(ledger.ownedMilliUnits(key) == 0, "Surge still has young/moving detailed claims: " + ledger.ownedMilliUnits(key));
        }).thenExecute(() -> {
            helper.assertTrue(level.getBlockState(seed.below()).is(Blocks.STONE), "Surge modified protected ground");
            ModConstants.LOGGER.info("[Coastal storm fixture] oceanIn={} oceanOut={} projected={} returned={} externalUnits={} residual={}",
                    active.receipt(OCEAN_IN), active.receipt(OCEAN_OUT), active.receipt(PROJECTION_OUT),
                    active.receipt(PROJECTION_IN), withdrawn, active.residualMilliUnits());
        }).thenSucceed();
    }

    private static void checkBudget(GameTestHelper helper, RegionalHydrologyState region, TemporaryFloodSavedData ledger) {
        helper.assertTrue(region.residualMilliUnits() == 0, "Nonzero regional surge residual");
        helper.assertTrue(region.receipt(PROJECTION_OUT) - region.receipt(PROJECTION_IN) == ledger.ownedMilliUnits(region.key),
                "Detailed flood claims do not match funded regional parcels");
    }
}
