package com.thunder.wildernessodysseyapi.gametest;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.blocks.CryoTubeBlock;
import com.thunder.wildernessodysseyapi.WorldGen.spawn.SpawnBunkerPlacer;
import com.thunder.wildernessodysseyapi.WorldGen.structure.NBTStructurePlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * GameTests that verify the spawn bunker can be pasted with vanilla template mechanics.
 */
@GameTestHolder(ModConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class SpawnBunkerGameTests {
    private static final String BATCH = "bunker";

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void bunkerSpawnsBlocks(GameTestHelper helper) {
        NBTStructurePlacer.PlacementResult result = placeBunker(helper);
        if (result == null) {
            return; // placeBunker already reported failure
        }

        helper.runAtTickTime(2, () -> {
            boolean hasBlocks = hasAnyBlock(helper.getLevel(), result);
            helper.assertTrue(hasBlocks, "Bunker paste did not produce any non-air blocks in its footprint.");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void bunkerContainsCryoTubes(GameTestHelper helper) {
        NBTStructurePlacer.PlacementResult result = placeBunker(helper);
        if (result == null) {
            return; // placeBunker already reported failure
        }

        helper.runAtTickTime(2, () -> {
            boolean foundCryo = containsCryoTube(helper.getLevel(), result);
            helper.assertTrue(foundCryo, "No cryo tubes were found inside the pasted bunker footprint.");
            helper.succeed();
        });
    }

    private static NBTStructurePlacer.PlacementResult placeBunker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO.above(64));
        NBTStructurePlacer.PlacementResult result = SpawnBunkerPlacer.placeBunker(level, anchor);
        if (result == null) {
            helper.fail(" Bunker template '" + ModConstants.MOD_ID + ":bunker' is missing or empty.");
            return null;
        }

        return result;
    }

    private static boolean hasAnyBlock(ServerLevel level, NBTStructurePlacer.PlacementResult result) {
        BlockPos min = new BlockPos(
                (int) Math.floor(result.bounds().minX),
                (int) Math.floor(result.bounds().minY),
                (int) Math.floor(result.bounds().minZ));
        BlockPos max = new BlockPos(
                (int) Math.ceil(result.bounds().maxX) - 1,
                (int) Math.ceil(result.bounds().maxY) - 1,
                (int) Math.ceil(result.bounds().maxZ) - 1);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    cursor.set(x, y, z);
                    if (!level.getBlockState(cursor).isAir()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean containsCryoTube(ServerLevel level, NBTStructurePlacer.PlacementResult result) {
        BlockPos min = new BlockPos(
                (int) Math.floor(result.bounds().minX),
                (int) Math.floor(result.bounds().minY),
                (int) Math.floor(result.bounds().minZ));
        BlockPos max = new BlockPos(
                (int) Math.ceil(result.bounds().maxX) - 1,
                (int) Math.ceil(result.bounds().maxY) - 1,
                (int) Math.ceil(result.bounds().maxZ) - 1);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    cursor.set(x, y, z);
                    if (level.getBlockState(cursor).is(CryoTubeBlock.CRYO_TUBE.get())) {
                        return true;
                    }
                    BlockEntity be = level.getBlockEntity(cursor);
                    if (be != null && be.getType() == CryoTubeBlock.CRYO_TUBE_ENTITY.get()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
