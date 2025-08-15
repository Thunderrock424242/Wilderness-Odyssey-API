package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.function.pattern.BlockPattern;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.Set;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;

/****
 * TerrainBlockReplacer for the Wilderness Odyssey API mod.
 */
public class TerrainBlockReplacer {

    private static final Set<String> loggedBlockErrors = new HashSet<>();

    /**
     * Replaces a block in a schematic with the terrain block sampled from the given world position.
     *
     * @param editSession The WorldEdit {@link EditSession} for applying changes.
     * @param world       The Minecraft world.
     * @param blockVector The position of the block in the schematic.
     * @param terrainPos  The position in the Minecraft world for terrain sampling.
     */
    public static void replaceBlockWithTerrain(EditSession editSession, ServerLevel world,
                                               BlockVector3 blockVector, BlockPos terrainPos)
            throws MaxChangedBlocksException {
        // Sample the block at the terrain position. If it's air, fall back to the block below
        net.minecraft.world.level.block.state.BlockState terrainBlock = world.getBlockState(terrainPos);
        if (terrainBlock.isAir()) {
            terrainBlock = world.getBlockState(terrainPos.below());
        }

        // Convert Minecraft BlockState to WorldEdit BlockState
        BlockType weBlockState = convertMinecraftToWorldEdit(terrainBlock);

        if (weBlockState != null) {
            // Replace the block in the schematic
            Pattern pattern = new BlockPattern(weBlockState.getDefaultState());
            editSession.setBlock(blockVector, pattern);
        }
    }

    /**
     * Convenience overload that calculates the terrain position relative to a
     * structure's origin in the world.
     *
     * @param editSession The WorldEdit session.
     * @param world       The Minecraft world.
     * @param blockVector The block position within the schematic.
     * @param origin      The world position of the schematic's origin.
     */
    public static void replaceBlockWithTerrainRelative(EditSession editSession, ServerLevel world,
                                                       BlockVector3 blockVector, BlockPos origin)
            throws MaxChangedBlocksException {
        BlockPos terrainPos = origin.offset(blockVector.x(), blockVector.y(), blockVector.z());
        replaceBlockWithTerrain(editSession, world, blockVector, terrainPos);
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
            if (loggedBlockErrors.add(blockId)) {
                LOGGER.warn("Error converting block {}", blockId, e);
            }
            return null;
        }
    }
}
