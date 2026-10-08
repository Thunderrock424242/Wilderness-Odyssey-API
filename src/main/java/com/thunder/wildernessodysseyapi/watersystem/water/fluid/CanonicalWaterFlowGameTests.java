package com.thunder.wildernessodysseyapi.watersystem.water.fluid;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge.WorldFluidMutationReconciler;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WaterSimulationConfig;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.EventPriority;

import java.util.List;
import java.util.function.Consumer;

/** World-backed conservation checks for the disturbed finite-water ticker. */
@GameTestHolder(ModConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CanonicalWaterFlowGameTests {

    private static final int PROJECTED_FLAGS = WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED;

    private CanonicalWaterFlowGameTests() {
    }

    /** An untouched generated source must never create water in a native fluid tick. */
    @GameTest(template = "water_flow")
    public static void generatedNativeTickConservesVolume(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(4, 4, 4));
        WorldFluidMutationReconciler.setCanonicalProjectionBlock(level, source,
                WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get().defaultBlockState(), 3);
        level.getChunkAt(source).getData(ModAttachments.GENERATED_WATER).recordCell(source,
                GeneratedWaterChunk.Cell.of(8, false, GeneratedWaterChunk.BodyType.LAKE));
        helper.assertTrue(!CanonicalWater.isTracked(level, source), "Fixture was already materialized");

        level.getFluidState(source).tick(level, source);

        helper.assertTrue(CanonicalWater.isTracked(level, source),
                "Native fluid tick did not hand generated water to finite authority");
        helper.assertTrue(localUnits(level, source) == WaterVolumeChunk.UNITS_PER_BLOCK,
                "Native fluid tick created volume before finite authority took ownership");
        helper.succeed();
    }

    /** Direct non-player solid writes must conserve water and remove its visible authority. */
    @GameTest(template = "water_flow")
    public static void directSolidWriteDisplacesWaterExactlyOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(4, 4, 4));
        setCanonical(level, source, WaterVolumeChunk.UNITS_PER_BLOCK);
        var replaced = level.getBlockState(source);
        helper.assertTrue(level.setBlock(source, Blocks.STONE.defaultBlockState(), 3), "Solid write failed");
        helper.assertTrue(!WildernessWaterAuthority.sample(level, source).water(),
                "A solid still reports ordinary canonical water");
        helper.assertTrue(WildernessWaterAuthority.removeWaterVolume(level, source, 4096, true) == 0,
                "A solid still exposes drainable ordinary water");
        helper.assertTrue(localUnits(level, source) == WaterVolumeChunk.UNITS_PER_BLOCK,
                "Direct solid displacement did not conserve volume");
        CanonicalWater.displaceForSolidPlacement(level, source, replaced, level.getBlockState(source));
        helper.assertTrue(localUnits(level, source) == WaterVolumeChunk.UNITS_PER_BLOCK,
                "Repeated placement notification duplicated water");
        helper.assertTrue(level.getBlockState(source).is(Blocks.STONE), "Displacement overwrote the solid");
        helper.succeed();
    }

    private static long localUnits(ServerLevel level, BlockPos center) {
        long total = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, -3, -3), center.offset(3, 3, 3))) {
            WaterVolumeChunk.WaterCell tracked = CanonicalWater.getTracked(level, pos);
            total += tracked == null ? WildernessWaterAuthority.sample(level, pos).volumeUnits() : tracked.volumeUnits();
        }
        return total;
    }

    /** No outlet means a hidden, undrainable parcel until the enclosure opens. */
    @GameTest(template = "water_flow")
    public static void enclosedSolidWriteRetainsDisplacementReservoir(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(4, 4, 4));
        for (BlockPos pos : BlockPos.betweenClosed(source.offset(-2, -1, -2), source.offset(2, 4, 2))) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        }
        setCanonical(level, source, WaterVolumeChunk.UNITS_PER_BLOCK);
        level.setBlock(source, Blocks.STONE.defaultBlockState(), 3);
        WaterVolumeChunk.WaterCell retained = CanonicalWater.getTracked(level, source);
        helper.assertTrue(retained != null && retained.displacementReservoir()
                && retained.volumeUnits() == WaterVolumeChunk.UNITS_PER_BLOCK,
                "Enclosed displacement did not retain its exact parcel: " + retained);
        helper.assertTrue(!WildernessWaterAuthority.sample(level, source).water(),
                "Hidden displacement became gameplay water inside a solid");
        helper.assertTrue(WildernessWaterAuthority.removeWaterVolume(level, source, 4096, true) == 0,
                "Hidden displacement exposed drainable water inside a solid");
        level.setBlock(source, Blocks.AIR.defaultBlockState(), 3);
        WildernessFluidRegistry.notifyTerrainChanged(level, source);
        WildernessFluidRegistry.tickCell(level, source);
        helper.assertTrue(localUnits(level, source) == WaterVolumeChunk.UNITS_PER_BLOCK,
                "Opening the enclosure lost its displacement parcel");
        helper.assertTrue(WildernessWaterAuthority.sample(level, source).water(),
                "Opening the enclosure failed to resume ordinary water");
        helper.succeed();
    }

    /** Snapshot capture/restoration must not commit the water side of a cancelled placement. */
    @GameTest(template = "water_flow")
    public static void provisionalSolidWriteLeavesCanonicalWaterUntouched(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(4, 4, 4));
        setCanonical(level, source, WaterVolumeChunk.UNITS_PER_BLOCK);
        var original = level.getBlockState(source);
        boolean capture = level.captureBlockSnapshots;
        boolean restoring = level.restoringBlockSnapshots;
        int snapshotCount = level.capturedBlockSnapshots.size();
        try {
            level.captureBlockSnapshots = true;
            level.setBlock(source, Blocks.STONE.defaultBlockState(), 3);
            WaterVolumeChunk.WaterCell cell = CanonicalWater.getTracked(level, source);
            helper.assertTrue(cell != null && !cell.displacementReservoir() && cell.volumeUnits() == 4096,
                    "Provisional placement committed canonical displacement");
            helper.assertTrue(localUnits(level, source) == 4096, "Provisional placement changed inventory");
        } finally {
            level.captureBlockSnapshots = false;
            level.restoringBlockSnapshots = true;
            level.setBlock(source, original, 3);
            level.captureBlockSnapshots = capture;
            level.restoringBlockSnapshots = restoring;
            while (level.capturedBlockSnapshots.size() > snapshotCount) {
                level.capturedBlockSnapshots.remove(level.capturedBlockSnapshots.size() - 1);
            }
        }
        helper.assertTrue(WildernessWaterAuthority.sample(level, source).volumeUnits() == 4096,
                "Cancelled placement restoration changed canonical inventory");
        helper.succeed();
    }

    /** Turning off machine capability bridging cannot disable solid displacement conservation. */
    @GameTest(template = "water_flow")
    public static void solidWriteConservesWithFluidHandlerCompatibilityDisabled(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(4, 4, 4));
        setCanonical(level, source, WaterVolumeChunk.UNITS_PER_BLOCK);
        boolean enabled = WaterSimulationConfig.ENABLE_FLUID_HANDLER_COMPAT.get();
        try {
            WaterSimulationConfig.ENABLE_FLUID_HANDLER_COMPAT.set(false);
            level.setBlock(source, Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(!WildernessWaterAuthority.sample(level, source).water(),
                    "Disabling the machine bridge left phantom water in a solid");
            helper.assertTrue(localUnits(level, source) == 4096,
                    "Disabling the machine bridge lost displaced water");
        } finally {
            WaterSimulationConfig.ENABLE_FLUID_HANDLER_COMPAT.set(enabled);
        }
        helper.succeed();
    }

    /** A final LOWEST cancellation must leave both single and multi-placement inventories unchanged. */
    @GameTest(template = "water_flow")
    public static void latePlacementCancellationDoesNotDisplaceWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos first = helper.absolutePos(new BlockPos(2, 4, 2));
        BlockPos second = helper.absolutePos(new BlockPos(6, 4, 6));
        setCanonical(level, first, 4096);
        setCanonical(level, second, 4096);
        BlockSnapshot firstSnapshot = BlockSnapshot.create(level.dimension(), level, first);
        BlockSnapshot secondSnapshot = BlockSnapshot.create(level.dimension(), level, second);
        Consumer<BlockEvent.EntityPlaceEvent> cancel = event -> {
            if (event.getLevel() == level && (event.getPos().equals(first) || event.getPos().equals(second))) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, cancel);
        boolean capture = level.captureBlockSnapshots;
        int count = level.capturedBlockSnapshots.size();
        try {
            level.captureBlockSnapshots = true;
            level.setBlock(first, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(second, Blocks.STONE.defaultBlockState(), 3);
            level.captureBlockSnapshots = false;
            helper.assertTrue(EventHooks.onBlockPlace(null, firstSnapshot, Direction.UP), "Single cancellation was ignored");
            helper.assertTrue(CanonicalWater.getTracked(level, first).volumeUnits() == 4096,
                    "A later LOWEST cancellation happened after water displacement");
            helper.assertTrue(EventHooks.onMultiBlockPlace(null, List.of(firstSnapshot, secondSnapshot), Direction.UP),
                    "Multi-placement cancellation was ignored");
            helper.assertTrue(CanonicalWater.getTracked(level, second).volumeUnits() == 4096,
                    "Cancelled multi-placement displaced water");
        } finally {
            NeoForge.EVENT_BUS.unregister(cancel);
            level.captureBlockSnapshots = false;
            WorldFluidMutationReconciler.setCanonicalProjectionBlock(level, first, firstSnapshot.getState(), 3);
            WorldFluidMutationReconciler.setCanonicalProjectionBlock(level, second, secondSnapshot.getState(), 3);
            level.captureBlockSnapshots = capture;
            while (level.capturedBlockSnapshots.size() > count) level.capturedBlockSnapshots.remove(level.capturedBlockSnapshots.size() - 1);
        }
        helper.succeed();
    }

    /** Final accepted single and multi-placement hooks conserve their source parcels once. */
    @GameTest(template = "water_flow")
    public static void acceptedPlacementDisplacesWaterAfterFinalEventResult(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(4, 4, 4));
        setCanonical(level, source, 4096);
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, source);
        boolean capture = level.captureBlockSnapshots;
        int count = level.capturedBlockSnapshots.size();
        try {
            level.captureBlockSnapshots = true;
            level.setBlock(source, Blocks.STONE.defaultBlockState(), 3);
            level.captureBlockSnapshots = false;
            helper.assertTrue(!EventHooks.onBlockPlace(null, snapshot, Direction.UP), "Placement unexpectedly cancelled");
            helper.assertTrue(!WildernessWaterAuthority.sample(level, source).water(), "Accepted placement retained phantom water");
            helper.assertTrue(localUnits(level, source) == 4096, "Accepted single placement changed inventory");
            helper.assertTrue(!EventHooks.onMultiBlockPlace(null, List.of(snapshot), Direction.UP), "Multi-placement unexpectedly cancelled");
            helper.assertTrue(localUnits(level, source) == 4096, "Repeated multi-placement hook duplicated water");
        } finally {
            level.captureBlockSnapshots = capture;
            while (level.capturedBlockSnapshots.size() > count) level.capturedBlockSnapshots.remove(level.capturedBlockSnapshots.size() - 1);
        }
        helper.succeed();
    }

    /** Gravity fills the exact available capacity and leaves every other unit at the source. */
    @GameTest(template = "water_flow")
    public static void downwardTransferConservesExactVolume(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos target = source.below();
        int sourceUnits = 8;
        int targetUnits = WaterVolumeChunk.UNITS_PER_BLOCK - 3;

        setCanonical(level, source, sourceUnits);
        setCanonical(level, target, targetUnits);
        WildernessFluidRegistry.tickCell(level, source);

        int sourceAfter = CanonicalWater.get(level, source).volumeUnits();
        int targetAfter = CanonicalWater.get(level, target).volumeUnits();
        helper.assertTrue(
                sourceAfter < sourceUnits
                        && targetAfter > targetUnits
                        && targetAfter <= WaterVolumeChunk.UNITS_PER_BLOCK
                        && sourceAfter + targetAfter == sourceUnits + targetUnits,
                "Downward flow did not conserve the exact canonical volume"
        );
        helper.succeed();
    }

    /** Four identical lateral outlets receive equal requests from one source snapshot. */
    @GameTest(template = "water_flow")
    public static void symmetricLateralTransferHasNoDirectionBias(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(4, 3, 4));
        level.setBlock(source.below(), Blocks.STONE.defaultBlockState(), 3);
        setCanonical(level, source, WaterVolumeChunk.UNITS_PER_BLOCK);

        WildernessFluidRegistry.tickCell(level, source);

        int firstTarget = -1;
        int total = CanonicalWater.get(level, source).volumeUnits();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int target = CanonicalWater.get(level, source.relative(direction)).volumeUnits();
            if (firstTarget < 0) {
                firstTarget = target;
            }
            helper.assertTrue(target == firstTarget, "Equal outlets received unequal transfer requests");
            total += target;
        }
        helper.assertTrue(
                firstTarget > 0 && total == WaterVolumeChunk.UNITS_PER_BLOCK,
                "Lateral flow lost or created canonical volume"
        );
        helper.succeed();
    }

    /** The custom fluid type stays finite even when vanilla source conversion is enabled. */
    @GameTest(template = "water_flow")
    public static void wildernessWaterNeverConvertsToInfiniteSource(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 2));
        FluidState state = WildernessFluidRegistry.WILDERNESS_WATER.get().defaultFluidState();

        helper.assertTrue(
                !WildernessFluidRegistry.WILDERNESS_WATER_TYPE.get()
                        .canConvertToSource(state, level, position),
                "Wilderness water still inherits vanilla infinite-source conversion"
        );
        helper.succeed();
    }

    /** A placed bucket immediately enters finite lateral flow on supported ground. */
    @GameTest(template = "water_flow")
    public static void bucketPlacementSpreadsAsConservedWildernessWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(4, 3, 4));
        level.setBlock(source.below(), Blocks.STONE.defaultBlockState(), 3);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            level.setBlock(source.relative(direction).below(), Blocks.STONE.defaultBlockState(), 3);
        }

        CanonicalWater.placeBucket(level, source);
        WildernessFluidRegistry.tickCell(level, source);

        int total = CanonicalWater.get(level, source).volumeUnits();
        int wetNeighbours = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int neighbourVolume = CanonicalWater.get(
                    level,
                    source.relative(direction)
            ).volumeUnits();
            total += neighbourVolume;
            if (neighbourVolume > 0) {
                wetNeighbours++;
            }
        }
        helper.assertTrue(
                wetNeighbours == 4
                        && CanonicalWater.get(level, source).volumeUnits()
                        < WaterVolumeChunk.UNITS_PER_BLOCK,
                "Bucket water stayed as one sleeping source instead of entering finite flow"
        );
        helper.assertTrue(
                total == WaterVolumeChunk.UNITS_PER_BLOCK,
                "Bucket flow did not conserve exactly one canonical bucket"
        );
        helper.succeed();
    }

    private static void setCanonical(ServerLevel level, BlockPos position, int volumeUnits) {
        level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        CanonicalWater.set(
                level,
                position,
                WaterVolumeChunk.WaterCell.still(volumeUnits, PROJECTED_FLAGS),
                true,
                false
        );
    }

    /** Environmental sediment placement must conserve the same units as player solid displacement. */
    @GameTest(template = "water_flow")
    public static void sedimentPlacementConservesShallowWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos deposit = helper.absolutePos(new BlockPos(4, 3, 4));
        level.setBlock(deposit.below(), Blocks.STONE.defaultBlockState(), 3);
        setCanonical(level, deposit, WaterVolumeChunk.UNITS_PER_BLOCK);
        var previous = level.getBlockState(deposit);
        var placed = Blocks.CLAY.defaultBlockState();
        helper.assertTrue(level.setBlock(deposit, placed, 3), "Sediment was not placed");
        CanonicalWater.displaceForSolidPlacement(level, deposit, previous, placed);
        WildernessFluidRegistry.notifyTerrainChanged(level, deposit);
        int total = 0;
        for (BlockPos pos : BlockPos.betweenClosed(deposit.offset(-3, -1, -3), deposit.offset(3, 3, 3))) {
            total += CanonicalWater.get(level, pos).volumeUnits();
        }
        helper.assertTrue(total == WaterVolumeChunk.UNITS_PER_BLOCK, "Sediment placement lost or created water");
        helper.assertTrue(level.getBlockState(deposit).is(Blocks.CLAY), "Displacement replaced the sediment");
        helper.succeed();
    }
}
