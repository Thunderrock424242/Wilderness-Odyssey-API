package com.thunder.wildernessodysseyapi.watersystem.water.reactive;

import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Isolated opt-in profile; normal server ticks remain the simulation owner. */
@GameTestHolder("wildernessodysseyapi_reactive_water_tests")
@PrefixGameTestTemplate(false)
public final class ReactivePoolGameTests {

    private static final int FULL = WaterVolumeChunk.UNITS_PER_BLOCK;

    private ReactivePoolGameTests() { }

    @GameTest(template = "water_flow", timeoutTicks = 200)
    public static void ordinaryTicksAdvanceOneDisjointPoolAndConserveItsWater(GameTestHelper helper) {
        WaterSimulationConfig.ENABLE_REACTIVE_SHALLOW_POOLS.set(true);
        ServerLevel level = helper.getLevel();
        BlockPos source = channel(helper, 3);
        seed(level, source, FULL);
        helper.succeedWhen(() -> {
            helper.assertTrue(CanonicalWater.get(level, source.east(2)).volumeUnits() > 0,
                    "Reactive surface flux did not reach the dry receiver through ordinary ticks");
            helper.assertTrue(sum(level, source, 3) == FULL, "Reactive pool changed its canonical inventory");
            var before = ReactiveWaterController.status(level);
            helper.assertTrue(before.steps() > 0 && before.regions() > 0,
                    "The optional surface solver was never reached by the canonical ticker");
            int[] levels = {CanonicalWater.get(level, source).volumeUnits(),
                    CanonicalWater.get(level, source.east()).volumeUnits(),
                    CanonicalWater.get(level, source.east(2)).volumeUnits()};
            helper.assertTrue(ReactiveWaterController.tryTick(level, source)
                    && ReactiveWaterController.tryTick(level, source.east()), "Region membership was not shared");
            helper.assertTrue(ReactiveWaterController.status(level).steps() == before.steps(),
                    "Repeated same-tick evaluation advanced the region twice");
            for (int x = 0; x < 3; x++) helper.assertTrue(
                    CanonicalWater.get(level, source.east(x)).volumeUnits() == levels[x],
                    "Same-tick region evaluation changed canonical levels");
        });
    }

    @GameTest(template = "water_flow")
    public static void rejectedDestinationDoesNotDebitOrCoolTheDonor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = channel(helper, 3);
        seed(level, source, 2000);
        var before = CanonicalWater.get(level, source).withTemperature(280_000);
        CanonicalWater.set(level, source, before, true, false);
        level.setBlock(source.east(), Blocks.STONE.defaultBlockState(), 2);
        helper.assertTrue(CanonicalFluxBridge.transfer(level, source, source.east(), 500, 1, 0) == 0,
                "Solid receiver accepted a surface flux");
        helper.assertTrue(before.equals(CanonicalWater.get(level, source)), "Rejected flux changed donor state");
        level.setBlock(source.east(), Blocks.AIR.defaultBlockState(), 2);
        seed(level, source.east(), 4000);
        int accepted = CanonicalFluxBridge.transfer(level, source, source.east(), 500, 1, 0);
        helper.assertTrue(accepted == 96, "Bridge ignored exact receiver capacity");
        helper.assertTrue(CanonicalWater.get(level, source).volumeUnits() == 1904
                && sum(level, source, 3) == 6000, "Bridge did not debit exactly the accepted credit");
        helper.assertTrue(CanonicalWater.get(level, source).temperatureMilliKelvin() == 280_000,
                "Surface transfer lost donor temperature provenance");
        helper.assertTrue(CanonicalWater.get(level, source.east()).temperatureMilliKelvin() == 292_841,
                "Surface receipt did not preserve volume-weighted temperature");
        helper.succeed();
    }

    @GameTest(template = "water_flow", batch = "reactive_topology")
    public static void editBesideDryRegionCellsInvalidatesAndWakesTheWetOwner(GameTestHelper helper) {
        WaterSimulationConfig.ENABLE_REACTIVE_SHALLOW_POOLS.set(true);
        ServerLevel level = helper.getLevel();
        BlockPos source = channel(helper, 7);
        seed(level, source, FULL);
        helper.assertTrue(ReactiveWaterController.tryTick(level, source), "Closed shallow channel was rejected");
        for (int x = 0; x < 7; x++) {
            var cell = CanonicalWater.get(level, source.east(x));
            if (cell.volumeUnits() > 0) CanonicalWater.set(level, source.east(x),
                    cell.withAddedFlags(WaterVolumeChunk.FLAG_SLEEPING), false, false);
        }
        level.setBlock(source.east(5), Blocks.STONE.defaultBlockState(), 2);
        helper.assertTrue(ReactiveWaterController.regionAt(level, source).cells() == 0,
                "Far dry-member edit retained stale physical-region topology");
        helper.assertTrue(!CanonicalWater.get(level, source).sleeping(),
                "Invalidated region did not wake its distant wet member");
        helper.assertTrue(sum(level, source, 7) == FULL, "Topology invalidation changed inventory");
        helper.succeed();
    }

    @GameTest(template = "water_flow", batch = "reactive_disabled")
    public static void disabledAndUnsupportedGeometryRemainWithVoxelFlow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = channel(helper, 3);
        seed(level, source, FULL);
        WaterSimulationConfig.ENABLE_REACTIVE_SHALLOW_POOLS.set(false);
        helper.assertTrue(!ReactiveWaterController.tryTick(level, source), "Disabled solver claimed water");
        WaterSimulationConfig.ENABLE_REACTIVE_SHALLOW_POOLS.set(true);
        level.setBlock(source.east().below(), Blocks.AIR.defaultBlockState(), 2);
        helper.assertTrue(!ReactiveWaterController.tryTick(level, source), "Falling-water geometry was admitted");
        helper.assertTrue(sum(level, source, 3) == FULL, "Rejected admission changed inventory");
        helper.succeed();
    }

    @GameTest(template = "water_flow", batch = "reactive_unload")
    public static void chunkCacheReleaseRetainsCanonicalInventoryAndAwakeWork(GameTestHelper helper) {
        WaterSimulationConfig.ENABLE_REACTIVE_SHALLOW_POOLS.set(true);
        ServerLevel level = helper.getLevel();
        BlockPos source = channel(helper, 3);
        seed(level, source, FULL);
        helper.assertTrue(ReactiveWaterController.tryTick(level, source), "Shallow pool was not admitted");
        ReactiveWaterController.onChunkUnload(level, new net.minecraft.world.level.ChunkPos(source));
        helper.assertTrue(ReactiveWaterController.regionAt(level, source).cells() == 0, "Chunk release retained region cache");
        helper.assertTrue(sum(level, source, 3) == FULL && !CanonicalWater.get(level, source).sleeping(),
                "Cache release lost water or stranded pending canonical work");
        helper.succeed();
    }

    @GameTest(template = "water_flow", timeoutTicks = 100)
    public static void exhaustedDeadlineRetainsTimeAndResumesThroughTheExistingQueue(GameTestHelper helper) {
        WaterSimulationConfig.ENABLE_REACTIVE_SHALLOW_POOLS.set(true);
        ServerLevel level = helper.getLevel();
        BlockPos source = channel(helper, 3);
        seed(level, source, FULL);
        helper.assertTrue(ReactiveWaterController.tryTick(level, source, 0), "Eligible region was not admitted");
        helper.assertTrue(ReactiveWaterController.regionAt(level, source).deferredSeconds() >= 0.05,
                "Exhausted time budget discarded the region interval");
        helper.assertTrue(CanonicalWater.get(level, source).volumeUnits() == FULL,
                "Exhausted deadline still transferred water");
        helper.succeedWhen(() -> {
            helper.assertTrue(CanonicalWater.get(level, source.east(2)).volumeUnits() > 0,
                    "Deferred surface work never resumed");
            helper.assertTrue(sum(level, source, 3) == FULL, "Budget deferral changed inventory");
        });
    }

    @GameTest(template = "water_flow", timeoutTicks = 100)
    public static void settledRegionSleepsWithoutDiscardingItsCanonicalWater(GameTestHelper helper) {
        WaterSimulationConfig.ENABLE_REACTIVE_SHALLOW_POOLS.set(true);
        ServerLevel level = helper.getLevel();
        BlockPos source = channel(helper, 3);
        for (int x = 0; x < 3; x++) seed(level, source.east(x), 1365);
        helper.assertTrue(ReactiveWaterController.tryTick(level, source), "Still pool was not admitted");
        helper.succeedWhen(() -> {
            for (int x = 0; x < 3; x++) helper.assertTrue(CanonicalWater.get(level, source.east(x)).sleeping(),
                    "Still region retained awake canonical work");
            helper.assertTrue(ReactiveWaterController.regionAt(level, source).cells() == 0,
                    "Sleeping region retained runtime ownership");
            helper.assertTrue(sum(level, source, 3) == 4095, "Sleeping discarded fixed-point remainder");
        });
    }

    @GameTest(template = "water_flow", batch = "reactive_large")
    public static void oversizedConnectedPoolRejectsAdmissionWithoutChangingWater(GameTestHelper helper) {
        WaterSimulationConfig.ENABLE_REACTIVE_SHALLOW_POOLS.set(true);
        ServerLevel level = helper.getLevel();
        BlockPos source = channel(helper, 17);
        seed(level, source, FULL);
        helper.assertTrue(!ReactiveWaterController.tryTick(level, source), "Oversized pool bypassed admission cap");
        helper.assertTrue(ReactiveWaterController.regionAt(level, source).cells() == 0
                && sum(level, source, 17) == FULL, "Rejected pool retained physical ownership or changed inventory");
        helper.succeed();
    }

    @GameTest(template = "water_flow", batch = "reactive_empty", timeoutTicks = 40)
    public static void drainingTheLastParcelReleasesRegionMembership(GameTestHelper helper) {
        WaterSimulationConfig.ENABLE_REACTIVE_SHALLOW_POOLS.set(true);
        ServerLevel level = helper.getLevel();
        BlockPos source = channel(helper, 3);
        seed(level, source, FULL);
        helper.assertTrue(ReactiveWaterController.tryTick(level, source), "Pool was not admitted");
        int withdrawn = 0;
        for (int x = 0; x < 3; x++) withdrawn += CanonicalWater.drainVolume(level, source.east(x), FULL);
        helper.assertTrue(withdrawn == FULL && sum(level, source, 3) == 0,
                "Fixture did not withdraw the exact canonical inventory");
        helper.succeedWhen(() -> {
            helper.assertTrue(ReactiveWaterController.regionAt(level, source).cells() == 0,
                    "Empty pool retained region membership after its last parcel was withdrawn");
            helper.assertTrue(!ReactiveWaterController.tryTick(level, source), "Empty pool was readmitted");
        });
    }

    @GameTest(template = "water_flow", batch = "reactive_capacity")
    public static void regionCapLeavesExcessWaterWithTheExistingVoxelOwner(GameTestHelper helper) {
        WaterSimulationConfig.ENABLE_REACTIVE_SHALLOW_POOLS.set(true);
        ServerLevel level = helper.getLevel();
        BlockPos first = channel(helper, 3);
        BlockPos second = first.south(4);
        channel(level, second, 3, helper);
        seed(level, first, FULL);
        seed(level, second, FULL);
        int oldLimit = WaterSimulationConfig.REACTIVE_SHALLOW_POOL_MAX_REGIONS.get();
        try {
            WaterSimulationConfig.REACTIVE_SHALLOW_POOL_MAX_REGIONS.set(ReactiveWaterController.status(level).regions() + 1);
            helper.assertTrue(ReactiveWaterController.tryTick(level, first), "Available region slot was rejected");
            helper.assertTrue(!ReactiveWaterController.tryTick(level, second), "Region admission exceeded its cap");
            helper.assertTrue(sum(level, second, 3) == FULL
                    && ReactiveWaterController.regionAt(level, second).cells() == 0,
                    "Admission pressure changed excess inventory or claimed its simulation");
        } finally {
            WaterSimulationConfig.REACTIVE_SHALLOW_POOL_MAX_REGIONS.set(oldLimit);
        }
        helper.succeed();
    }

    @GameTest(template = "water_flow")
    public static void unknownTerrainStaysUnloadedAfterAdmission(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos unknown = new BlockPos(29_000_000, helper.absolutePos(new BlockPos(1, 3, 4)).getY(), 29_000_000);
        helper.assertTrue(level.getChunkSource().getChunkNow(unknown.getX() >> 4, unknown.getZ() >> 4) == null,
                "Unknown-terrain fixture is already loaded");
        helper.assertTrue(ShallowPoolAdmission.sample(level, unknown).isEmpty(), "Unknown frontier was admitted");
        helper.assertTrue(level.getChunkSource().getChunkNow(unknown.getX() >> 4, unknown.getZ() >> 4) == null,
                "Surface admission synchronously loaded unknown terrain");
        helper.succeed();
    }

    @GameTest(template = "water_flow", batch = "reactive_provenance")
    public static void unsupportedProvenanceReleasesPhysicalMembership(GameTestHelper helper) {
        WaterSimulationConfig.ENABLE_REACTIVE_SHALLOW_POOLS.set(true);
        ServerLevel level = helper.getLevel();
        BlockPos source = channel(helper, 3);
        seed(level, source, FULL);
        helper.assertTrue(ReactiveWaterController.tryTick(level, source), "Pool was not admitted");
        var external = CanonicalWater.get(level, source).withAddedFlags(WaterVolumeChunk.FLAG_IMPORTED);
        CanonicalWater.set(level, source, external, false, false);
        helper.assertTrue(ReactiveWaterController.regionAt(level, source).cells() == 0,
                "Unsupported provenance retained physical-region ownership");
        helper.assertTrue(sum(level, source, 3) == FULL, "Ownership release changed canonical units");
        helper.succeed();
    }

    private static BlockPos channel(GameTestHelper helper, int length) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 3, 4));
        channel(level, origin, length, helper);
        return origin;
    }

    private static void channel(ServerLevel level, BlockPos origin, int length, GameTestHelper helper) {
        for (BlockPos position : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(length, 2, 1))) {
            helper.assertTrue(level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) != null,
                    "Fixture needs naturally loaded terrain");
            level.setBlock(position, Blocks.STONE.defaultBlockState(), 2);
        }
        for (int x = 0; x < length; x++) {
            level.setBlock(origin.east(x), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(origin.east(x).above(), Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static void seed(ServerLevel level, BlockPos position, int units) {
        CanonicalWater.set(level, position, WaterVolumeChunk.WaterCell.still(units,
                WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED), true, true);
    }

    private static int sum(ServerLevel level, BlockPos origin, int length) {
        int result = 0;
        for (int x = 0; x < length; x++) result += CanonicalWater.get(level, origin.east(x)).volumeUnits();
        return result;
    }
}
