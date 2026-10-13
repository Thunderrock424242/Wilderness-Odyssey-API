package com.thunder.wildernessodysseyapi.watersystem.water.worldgen;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.compat.neoforge.WorldFluidMutationReconciler;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Reproduces starter island filling and bunker carving without loading the full bunker asset. */
@GameTestHolder(ModConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StarterTerrainWaterGameTests {
    private StarterTerrainWaterGameTests() { }

    @GameTest(template = "water_flow")
    public static void starterIslandFillDoesNotPileOceanWaterAboveTerrain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bottom = helper.absolutePos(new BlockPos(6, 3, 6));
        for (int y = 0; y < 3; y++) generatedWater(level, bottom.above(y));

        WorldFluidMutationReconciler.withStarterTerrainGeneration(level, bounds(bottom), () -> {
            for (int y = 0; y < 3; y++) {
                helper.assertTrue(level.setBlock(bottom.above(y), Blocks.STONE.defaultBlockState(), 2),
                        "Starter terrain fill was refused");
            }
            return null;
        });

        helper.assertTrue(localUnits(level, bottom) == 0,
                "Initial island construction displaced ocean volume into runtime water instead of replacing terrain");
        for (int y = 0; y < 3; y++) {
            var chunk = level.getChunkAt(bottom.above(y));
            helper.assertTrue(chunk.getData(ModAttachments.GENERATED_WATER).spanAt(bottom.above(y)) == null,
                    "Filled starter terrain retained generated ocean metadata");
        }
        helper.succeed();
    }

    @GameTest(template = "water_flow")
    public static void starterBunkerCarvingCannotReleaseHiddenOceanWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos interior = helper.absolutePos(new BlockPos(6, 4, 6));
        level.setBlock(interior, Blocks.STONE.defaultBlockState(), 2);
        CanonicalWater.set(level, interior, WaterVolumeChunk.WaterCell.still(4096,
                WaterVolumeChunk.FLAG_DISPLACEMENT_RESERVOIR | WaterVolumeChunk.FLAG_SLEEPING), false, false);

        WorldFluidMutationReconciler.withStarterTerrainGeneration(level, bounds(interior),
                () -> level.setBlock(interior, Blocks.AIR.defaultBlockState(), 2));

        helper.assertTrue(CanonicalWater.getTracked(level, interior) == null,
                "Bunker carving retained a hidden ocean parcel inside the authored room");
        CanonicalWater.schedule(level, interior);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(level.getBlockState(interior).isAir() && localUnits(level, interior) == 0,
                    "Water reappeared inside the bunker after the terrain generation scope ended");
            helper.succeed();
        });
    }

    @GameTest(template = "water_flow")
    public static void starterTemplateWaterUsesGeneratedAuthority(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pond = helper.absolutePos(new BlockPos(6, 4, 6));
        WorldFluidMutationReconciler.withStarterTerrainGeneration(level, bounds(pond),
                () -> level.setBlock(pond, Blocks.WATER.defaultBlockState(), 2));

        helper.assertTrue(level.getBlockState(pond).is(WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get()),
                "Authored starter water was not mapped through the generation water owner");
        helper.assertTrue(level.getChunkAt(pond).getData(ModAttachments.GENERATED_WATER).spanAt(pond) != null
                        && CanonicalWater.getTracked(level, pond) == null,
                "Authored starter water was treated as a runtime bucket instead of generated water");
        helper.succeed();
    }

    @GameTest(template = "water_flow")
    public static void starterGenerationScopeCannotDisableOrdinaryDisplacement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(6, 4, 6));
        generatedWater(level, source);
        try {
            WorldFluidMutationReconciler.withStarterTerrainGeneration(level, bounds(source), () -> {
                throw new IllegalArgumentException("fixture");
            });
        } catch (IllegalArgumentException expected) {
            helper.assertTrue("fixture".equals(expected.getMessage()), "Unexpected scope failure");
        }
        helper.assertTrue(level.setBlock(source, Blocks.STONE.defaultBlockState(), 2), "Runtime solid write failed");
        helper.assertTrue(localUnits(level, source) == 4096,
                "The generation boundary leaked and disabled ordinary water conservation");
        helper.succeed();
    }

    @GameTest(template = "water_flow")
    public static void starterGenerationDoesNotChangeWaterOutsideItsFootprint(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(6, 4, 6));
        generatedWater(level, source);
        WorldFluidMutationReconciler.withStarterTerrainGeneration(level, BoundingBox.fromCorners(source.east(5), source.east(6)),
                () -> level.setBlock(source, Blocks.STONE.defaultBlockState(), 2));
        helper.assertTrue(localUnits(level, source) == 4096,
                "An unrelated write outside the starter footprint bypassed runtime conservation");
        helper.succeed();
    }

    private static BoundingBox bounds(BlockPos center) {
        return BoundingBox.fromCorners(center.offset(-3, -3, -3), center.offset(3, 4, 3));
    }

    private static void generatedWater(ServerLevel level, BlockPos position) {
        WorldFluidMutationReconciler.setCanonicalProjectionBlock(level, position,
                WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get().defaultBlockState(), 2);
        level.getChunkAt(position).getData(ModAttachments.GENERATED_WATER)
                .recordCell(position, GeneratedWaterChunk.Cell.of(8, false, GeneratedWaterChunk.BodyType.OCEAN));
    }

    private static int localUnits(ServerLevel level, BlockPos center) {
        int units = 0;
        for (BlockPos position : BlockPos.betweenClosed(center.offset(-3, -3, -3), center.offset(3, 5, 3))) {
            units += CanonicalWater.get(level, position).volumeUnits();
        }
        return units;
    }
}
