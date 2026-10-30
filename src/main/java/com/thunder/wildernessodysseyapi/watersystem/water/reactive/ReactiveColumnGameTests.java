package com.thunder.wildernessodysseyapi.watersystem.water.reactive;

import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge.WaterMutationSafety;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Column transactions and conservative rejection of three-dimensional layouts. */
@GameTestHolder("wildernessodysseyapi_reactive_water_tests")
@PrefixGameTestTemplate(false)
public final class ReactiveColumnGameTests {

    private ReactiveColumnGameTests() { }

    @GameTest(template = "water_flow", batch = "reactive_column_bridge")
    public static void unevenColumnTransferKeepsPackedCellsAndParcelTemperature(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = scaffold(helper);
        SurfaceColumn donor = new SurfaceColumn(base, 2);
        SurfaceColumn receiver = new SurfaceColumn(base.east().below(), 3);
        seed(level, donor.bottom(), 4096, 280_000);
        seed(level, donor.bottom().above(), 2048, 280_000);
        seed(level, receiver.bottom(), 4096, 293_150);
        seed(level, receiver.bottom().above(), 4000, 293_150);
        int moved = CanonicalColumnFluxBridge.transfer(level, donor, receiver, 500, 1, 0);
        helper.assertTrue(moved == 500 && donor.volume(level) == 5644 && receiver.volume(level) == 8596,
                "Uneven column bridge did not preserve exact accepted units");
        helper.assertTrue(CanonicalWater.get(level, donor.bottom()).volumeUnits() == 4096
                && CanonicalWater.get(level, receiver.bottom().above()).volumeUnits() == 4096,
                "Column transfer left a hole beneath its surface");
        helper.assertTrue(CanonicalWater.get(level, receiver.bottom().above()).temperatureMilliKelvin() == 292_841
                && CanonicalWater.get(level, receiver.bottom().above(2)).temperatureMilliKelvin() == 280_000,
                "Column receipts lost parcel temperature");
        helper.succeed();
    }

    @GameTest(template = "water_flow", batch = "reactive_column_bridge")
    public static void blockedUpperCapacityDebitsOnlyTheAcceptedLowerReceipt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = scaffold(helper);
        SurfaceColumn donor = new SurfaceColumn(base, 2);
        SurfaceColumn receiver = new SurfaceColumn(base.east(), 2);
        seed(level, donor.bottom(), 4096, 280_000);
        seed(level, donor.bottom().above(), 4096, 280_000);
        seed(level, receiver.bottom(), 4000, 293_150);
        level.setBlock(receiver.bottom().above(), Blocks.STONE.defaultBlockState(), 2);
        int moved = CanonicalColumnFluxBridge.transfer(level, donor, receiver, 500, 1, 0);
        helper.assertTrue(moved == 96 && donor.volume(level) == 8096 && receiver.volume(level) == 4096,
                "Partial column rejection lost or over-debited a parcel");
        helper.assertTrue(CanonicalWater.get(level, donor.bottom().above()).temperatureMilliKelvin() == 280_000,
                "Rejected remainder lost its temperature");
        helper.succeed();
    }

    @GameTest(template = "water_flow", batch = "reactive_column_geometry")
    public static void suspendedLayerAndMultipleRoomsFallBackToVoxelFlow(GameTestHelper helper) {
        WaterSimulationConfig.ENABLE_REACTIVE_SHALLOW_POOLS.set(true);
        ServerLevel level = helper.getLevel();
        BlockPos base = scaffold(helper);
        seed(level, base.above(), 4096, 293_150);
        helper.assertTrue(!ReactiveWaterController.tryTick(level, base.above()),
                "Suspended water was flattened into a height field");
        CanonicalWater.set(level, base.above(), WaterVolumeChunk.WaterCell.EMPTY, true, false);
        seed(level, base, 4096, 293_150);
        // One neighboring X/Z exposes two disconnected vertical openings.
        level.setBlock(base.east().above(), Blocks.STONE.defaultBlockState(), 2);
        seed(level, base.above(), 4096, 293_150);
        seed(level, base.above(2), 2048, 293_150);
        helper.assertTrue(!ReactiveWaterController.tryTick(level, base),
                "Multiple vertical rooms were assigned one height-field column");
        helper.succeed();
    }

    @GameTest(template = "water_flow", batch = "reactive_column_growth")
    public static void waterAboveTheCachedEnvelopeRetiresItsOldFootprint(GameTestHelper helper) {
        WaterSimulationConfig.ENABLE_REACTIVE_SHALLOW_POOLS.set(true);
        ServerLevel level = helper.getLevel();
        BlockPos base = scaffold(helper);
        seed(level, base, 4096, 293_150);
        helper.assertTrue(ReactiveWaterController.tryTick(level, base), "Initial column was not admitted");
        seed(level, base.above(2), 1024, 293_150);
        helper.assertTrue(ReactiveWaterController.regionAt(level, base).cells() == 0,
                "Vertical growth retained stale physical ownership");
        helper.succeed();
    }

    @GameTest(template = "water_flow", batch = "reactive_spill", timeoutTicks = 300)
    public static void finiteInflowOvertopsASillWithoutDrainingTheRestingBasin(GameTestHelper helper) {
        WaterSimulationConfig.ENABLE_REACTIVE_SHALLOW_POOLS.set(true);
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(1, 4, 4));
        for (BlockPos position : BlockPos.betweenClosed(base.offset(-1, -2, -1), base.offset(5, 3, 1))) {
            helper.assertTrue(level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) != null,
                    "Spill fixture requires naturally loaded terrain");
            level.setBlock(position, Blocks.STONE.defaultBlockState(), 2);
        }
        for (int x = 0; x < 5; x++) {
            int bed = x < 2 ? 0 : x == 2 ? 1 : -1;
            for (int y = bed; y <= 2; y++) level.setBlock(base.offset(x, y, 0), Blocks.AIR.defaultBlockState(), 2);
        }
        seed(level, base, 4096, 293_150);
        seed(level, base.east(), 4096, 293_150);
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(units(level, base, 3, 4, -1, 2) == 0,
                    "A resting basin below its sill leaked into the receiver");
            helper.assertTrue(ReactiveWaterController.regionAt(level, base).cells() > 0,
                    "Resting spill basin never entered the physical solver");
            helper.assertTrue(CanonicalWater.addVolume(level, base.above(), 4096, 0, 0, 0) == 4096,
                    "Finite spill inflow was not accepted");
            helper.succeedWhen(() -> {
                helper.assertTrue(units(level, base, 3, 4, -1, 2) > 2048,
                        "Funded water did not overtop the sill into the lower channel");
                helper.assertTrue(units(level, base, 0, 4, -1, 2) == 12288,
                        "Overtopping changed its exact finite ledger");
            });
        });
    }

    @GameTest(template = "water_flow", batch = "reactive_column_boundary", timeoutTicks = 150)
    public static void deepColumnsExchangeAcrossAnAlreadyLoadedChunkEdge(GameTestHelper helper) {
        WaterSimulationConfig.ENABLE_REACTIVE_SHALLOW_POOLS.set(true);
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(4, 3, 4));
        BlockPos selected = null;
        for (int dx = -1; dx <= 1 && selected == null; dx++) {
            BlockPos candidate = new BlockPos(((anchor.getX() >> 4) + dx) * 16 + 15,
                    level.getMaxBuildHeight() - 10, anchor.getZ());
            if (WaterMutationSafety.isReady(level, candidate, WaterMutationSafety.CALLBACK_RADIUS + 4)) selected = candidate;
        }
        helper.assertTrue(selected != null, "No loaded chunk edge available for column fixture");
        BlockPos base = selected;
        for (BlockPos position : BlockPos.betweenClosed(base.offset(-1, -1, -1), base.offset(3, 2, 1))) {
            level.setBlock(position, Blocks.STONE.defaultBlockState(), 2);
        }
        for (int x = 0; x < 3; x++) for (int y = 0; y < 2; y++) {
            level.setBlock(base.offset(x, y, 0), Blocks.AIR.defaultBlockState(), 2);
        }
        seed(level, base, 4096, 293_150);
        seed(level, base.above(), 4096, 293_150);
        helper.succeedWhen(() -> {
            helper.assertTrue(CanonicalWater.get(level, base.east(2)).volumeUnits() > 0,
                    "Physical columns did not route water across the admitted chunk edge");
            helper.assertTrue(ReactiveWaterController.regionAt(level, base).cells() > 0
                    && ReactiveWaterController.regionAt(level, base.east(2)).cells() > 0,
                    "Cross-chunk transfer did not participate in a physical region");
            helper.assertTrue(units(level, base, 0, 2, 0, 1) == 8192,
                    "Cross-chunk column exchange changed volume");
        });
    }

    private static int units(ServerLevel level, BlockPos base, int minX, int maxX, int minY, int maxY) {
        int total = 0;
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) {
            total += CanonicalWater.get(level, base.offset(x, y, 0)).volumeUnits();
        }
        return total;
    }

    @GameTest(template = "water_flow", batch = "reactive_depth_limit")
    public static void fullEightCellDepthIsSupportedAndNinthCellFallsBack(GameTestHelper helper) {
        WaterSimulationConfig.ENABLE_REACTIVE_SHALLOW_POOLS.set(true);
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(3, 5, 3)).above(20);
        for (BlockPos position : BlockPos.betweenClosed(base.offset(-1, -1, -1), base.offset(1, 9, 1))) {
            helper.assertTrue(level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) != null,
                    "Depth fixture requires naturally loaded terrain");
            level.setBlock(position, Blocks.STONE.defaultBlockState(), 2);
        }
        for (int y = 0; y <= 8; y++) level.setBlock(base.above(y), Blocks.AIR.defaultBlockState(), 2);
        for (int y = 0; y < 8; y++) seed(level, base.above(y), 4096, 293_150);
        helper.assertTrue(ReactiveWaterController.tryTick(level, base),
                "A full eight-cell column was rejected because of unused air headroom");
        helper.assertTrue(ReactiveWaterController.regionAt(level, base).cells() == 8,
                "Depth admission exceeded its bounded capacity footprint");
        seed(level, base.above(8), 4096, 293_150);
        helper.assertTrue(ReactiveWaterController.regionAt(level, base).cells() == 0,
                "Ninth layer retained the eight-layer footprint");
        helper.runAfterDelay(1, () -> {
            CanonicalWater.schedule(level, base);
            helper.assertTrue(!ReactiveWaterController.tryTick(level, base), "Ninth layer exceeded supported column depth");
            helper.assertTrue(units(level, base, 0, 0, 0, 8) == 36864, "Depth fallback changed inventory");
            helper.succeed();
        });
    }

    private static BlockPos scaffold(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(2, 4, 3));
        for (BlockPos position : BlockPos.betweenClosed(base.offset(-1, -2, -1), base.offset(2, 4, 1))) {
            helper.assertTrue(level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) != null,
                    "Column fixture requires naturally loaded terrain");
            level.setBlock(position, Blocks.STONE.defaultBlockState(), 2);
        }
        for (int y = 0; y <= 3; y++) {
            level.setBlock(base.above(y), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(base.east().above(y), Blocks.AIR.defaultBlockState(), 2);
        }
        level.setBlock(base.east().below(), Blocks.AIR.defaultBlockState(), 2);
        return base;
    }

    private static void seed(ServerLevel level, BlockPos position, int units, int temperature) {
        CanonicalWater.set(level, position, WaterVolumeChunk.WaterCell.still(units,
                WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED).withTemperature(temperature), true, true);
    }
}
