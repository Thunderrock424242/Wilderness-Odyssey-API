package com.thunder.wildernessodysseyapi.watersystem.water.fluid;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** World-backed conservation checks for the disturbed finite-water ticker. */
@GameTestHolder(ModConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CanonicalWaterFlowGameTests {

    private static final int PROJECTED_FLAGS = WaterVolumeChunk.FLAG_COMPATIBILITY_PROJECTED;

    private CanonicalWaterFlowGameTests() {
    }

    /** Gravity fills the exact available capacity and leaves every other unit at the source. */
    @GameTest(template = "empty")
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
                sourceAfter == 5
                        && targetAfter == WaterVolumeChunk.UNITS_PER_BLOCK
                        && sourceAfter + targetAfter == sourceUnits + targetUnits,
                "Downward flow did not conserve the exact canonical volume"
        );
        helper.succeed();
    }

    /** Four identical lateral outlets receive equal requests from one source snapshot. */
    @GameTest(template = "empty")
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
    @GameTest(template = "empty")
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
    @GameTest(template = "empty")
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
}
