package com.thunder.wildernessodysseyapi.watersystem.water.fluid;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge.WorldFluidMutationReconciler;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded-world regressions for legacy-water boundaries and persisted authority. */
@GameTestHolder(ModConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaterAuditGameTests {
    private WaterAuditGameTests() {
    }

    @GameTest(template = "water_flow", batch = "water_audit_flow")
    public static void gravityCannotOverwriteUnconvertedVanillaWater(GameTestHelper helper) {
        assertLegacyBoundary(helper, Direction.DOWN);
    }

    @GameTest(template = "water_flow", batch = "water_audit_flow")
    public static void lateralFlowCannotOverwriteUnconvertedVanillaWater(GameTestHelper helper) {
        assertLegacyBoundary(helper, Direction.EAST);
    }

    @GameTest(template = "water_flow", batch = "water_audit_flow")
    public static void drainingBubbleColumnReprojectsPartialAndEmptyVolume(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(5, 5, 5));
        level.setBlock(source.below(), Blocks.SOUL_SAND.defaultBlockState(), 3);
        CanonicalWater.placeBucket(level, source);
        BubbleColumnBlock.updateColumn(level, source, Blocks.SOUL_SAND.defaultBlockState());
        CanonicalWater.reprojectCompatibility(level, source);
        helper.assertTrue(level.getBlockState(source).is(Blocks.BUBBLE_COLUMN),
                "A full canonical source must retain its hosted bubble column");
        int partial = WaterVolumeChunk.UNITS_PER_BLOCK / 2;
        CanonicalWater.set(level, source, WaterVolumeChunk.WaterCell.still(partial,
                WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED), true, false);
        helper.assertTrue(CanonicalWater.get(level, source).volumeUnits() == partial
                        && !CanonicalWater.get(level, source).projectionPending(),
                "Partial bubble-column volume remained pending instead of projecting");
        helper.assertTrue(level.getBlockState(source).is(WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get())
                        && !level.getFluidState(source).isSource(),
                "A partly drained bubble column must become finite flowing Wilderness water");
        CanonicalWater.set(level, source, WaterVolumeChunk.WaterCell.still(0,
                WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED), true, false);
        helper.assertTrue(CanonicalWater.get(level, source).volumeUnits() == 0
                        && !CanonicalWater.get(level, source).projectionPending()
                        && level.getBlockState(source).isAir(),
                "An emptied hosted cell must clear both physical water and pending projection");
        helper.succeed();
    }

    @GameTest(template = "water_flow", batch = "water_audit_flow")
    public static void solidPlacementDoesNotAdoptLegacyWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(5, 5, 5));
        WorldFluidMutationReconciler.setCanonicalProjectionBlock(level, position,
                Blocks.WATER.defaultBlockState(), 2);
        helper.assertTrue(!CanonicalWater.isTracked(level, position), "Fixture is already canonical");
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, position);
        boolean capture = level.captureBlockSnapshots;
        int snapshotCount = level.capturedBlockSnapshots.size();
        try {
            level.captureBlockSnapshots = true;
            level.setBlock(position, Blocks.STONE.defaultBlockState(), 3);
            level.captureBlockSnapshots = false;
            helper.assertTrue(!EventHooks.onBlockPlace(null, snapshot, Direction.UP), "Placement was canceled");
        } finally {
            level.captureBlockSnapshots = capture;
            while (level.capturedBlockSnapshots.size() > snapshotCount) {
                level.capturedBlockSnapshots.remove(level.capturedBlockSnapshots.size() - 1);
            }
        }
        for (BlockPos pos : BlockPos.betweenClosed(position.offset(-2, -1, -2), position.offset(2, 4, 2))) {
            helper.assertTrue(!CanonicalWater.isTracked(level, pos),
                    "Replacing external water implicitly created a canonical parcel");
        }
        helper.succeed();
    }

    private static void assertLegacyBoundary(GameTestHelper helper, Direction outlet) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(5, 5, 5));
        BlockPos target = source.relative(outlet);
        for (BlockPos pos : BlockPos.betweenClosed(source.offset(-2, -2, -2), source.offset(2, 1, 2))) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        }
        WorldFluidMutationReconciler.setCanonicalProjectionBlock(level, target,
                Blocks.WATER.defaultBlockState(), 2);
        CanonicalWater.set(level, source, WaterVolumeChunk.WaterCell.still(
                WaterVolumeChunk.UNITS_PER_BLOCK, WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED), true, false);
        helper.assertTrue(!CanonicalWater.isTracked(level, target)
                && level.getBlockState(target).is(Blocks.WATER), "Legacy fixture was already converted");

        WildernessFluidRegistry.tickCell(level, source);

        helper.assertTrue(level.getBlockState(target).is(Blocks.WATER)
                && level.getFluidState(target).isSource() && !CanonicalWater.isTracked(level, target),
                "Finite flow replaced or implicitly converted the full legacy source");
        helper.assertTrue(CanonicalWater.get(level, source).volumeUnits() == WaterVolumeChunk.UNITS_PER_BLOCK,
                "A rejected legacy destination still debited the finite source");
        helper.succeed();
    }

    @GameTest(template = "water_flow", batch = "water_audit_flow")
    public static void establishedAuthorityFlowsAfterStartupOptionChanges(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(WildernessWaterRules.isEnabled(level), "Fixture requires persisted enabled authority");
        BlockPos source = helper.absolutePos(new BlockPos(5, 4, 5));
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlock(source.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(source.offset(dx, 0, dz), Math.abs(dx) + Math.abs(dz) > 1
                        ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
        }
        CanonicalWater.placeBucket(level, source);
        boolean startup = WaterSimulationConfig.ENABLE_WILDERNESS_ODYSSEY_WATER.get();
        int cells = WaterSimulationConfig.LOCAL_FLOW_CELLS_PER_TICK.get();
        double millis = WaterSimulationConfig.LOCAL_FLOW_BUDGET_MILLIS.get();
        try {
            WaterSimulationConfig.ENABLE_WILDERNESS_ODYSSEY_WATER.set(false);
            WaterSimulationConfig.LOCAL_FLOW_CELLS_PER_TICK.set(1024);
            WaterSimulationConfig.LOCAL_FLOW_BUDGET_MILLIS.set(25.0);
            helper.assertTrue(WildernessWaterRules.isEnabled(level), "Startup edit changed the persisted authority");
            for (int pass = 0; pass < 8 && CanonicalWater.get(level, source).volumeUnits()
                    == WaterVolumeChunk.UNITS_PER_BLOCK; pass++) {
                WildernessFluidRegistry.onServerLevelTick(new LevelTickEvent.Post(() -> true, level));
            }
            helper.assertTrue(CanonicalWater.get(level, source).volumeUnits() < WaterVolumeChunk.UNITS_PER_BLOCK,
                    "Persisted enabled water stopped flowing after the startup option changed");
            helper.assertTrue(WaterSimulationConfig.vanillaBucketCompatEnabled(),
                    "The startup option disabled established bucket compatibility");
            helper.assertTrue(WaterSimulationConfig.weatherWaterCouplingEnabled()
                            == WaterSimulationConfig.ENABLE_WEATHER_WATER_COUPLING.get()
                    && WaterSimulationConfig.watershedSimulationEnabled()
                            == WaterSimulationConfig.ENABLE_WATERSHED_SIMULATION.get(),
                    "The startup option disabled established weather/hydrology features");
            int total = CanonicalWater.get(level, source).volumeUnits();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                total += CanonicalWater.get(level, source.relative(direction)).volumeUnits();
            }
            helper.assertTrue(total == WaterVolumeChunk.UNITS_PER_BLOCK,
                    "Established flow lost or created water after the startup edit");
        } finally {
            WaterSimulationConfig.ENABLE_WILDERNESS_ODYSSEY_WATER.set(startup);
            WaterSimulationConfig.LOCAL_FLOW_CELLS_PER_TICK.set(cells);
            WaterSimulationConfig.LOCAL_FLOW_BUDGET_MILLIS.set(millis);
        }
        helper.succeed();
    }
}
