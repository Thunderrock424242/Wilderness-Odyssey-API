package com.thunder.wildernessodysseyapi.WordlEdit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

public class TerrainBlockReplacer {

    /**
     * Replaces specified blocks in the schematic with terrain-generated blocks.
     *
     * @param editSession The WorldEdit EditSession for applying changes.
     * @param world       The Minecraft ServerLevel (world).
     * @param blockVector The position of the block in the schematic.
     * @param terrainPos  The position in the Minecraft world for terrain sampling.
     */
    public static void replaceBlockWithTerrain(EditSession editSession, ServerLevel world, BlockVector3 blockVector, BlockPos terrainPos) throws MaxChangedBlocksException {
        // Get terrain block from Minecraft world
        net.minecraft.world.level.block.state.BlockState terrainBlock = world.getBlockState(terrainPos.below());

        // Convert Minecraft BlockState to WorldEdit BlockState
        BlockType weBlockState = convertMinecraftToWorldEdit(terrainBlock);

        if (weBlockState != null) {
            // Replace the block in the schematic
            editSession.setBlock(blockVector, (Pattern) weBlockState);
        }
    }

    /**
     * Converts a Minecraft BlockState to a WorldEdit BlockState.
     *
     * @param minecraftBlockState The Minecraft BlockState.
     * @return The WorldEdit-compatible BlockState.
     */
    private static BlockType convertMinecraftToWorldEdit(net.minecraft.world.level.block.state.BlockState minecraftBlockState) {
        // Get the block ID using the BuiltInRegistries
        String blockId = BuiltInRegistries.BLOCK.getKey(minecraftBlockState.getBlock()).toString();

        try {
            return BlockTypes.get(blockId);
        } catch (Exception e) {
            System.out.println("Error converting block: " + blockId);
            e.printStackTrace();
            return null;
        }
    }
}
