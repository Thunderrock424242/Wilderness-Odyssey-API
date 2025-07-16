package com.thunder.wildernessodysseyapi.WorldGen.SecretOrderVillage;

import com.sk89q.worldedit.function.pattern.BlockPattern;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.InputStream;
import java.util.Random;

public class SecretOrderVillagePlacer {
    private static final String NAMESPACE = "wildernessodyssey";
    private static final String PATH = "schematics/secret_order_village.schem";

    public static void tryPlace(ServerLevel level, LevelChunk chunk) {
        BlockPos chunkPos = chunk.getPos().getWorldPosition();
        Random rand = new Random(chunkPos.asLong());

        if (rand.nextFloat() > 0.001f) return; // Rare spawn chance

        BlockPos surfacePos = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, chunkPos);
        if (level.getBiome(surfacePos).is(BiomeTags.IS_OCEAN)) return;

        if (level.getBiome(surfacePos).is(BiomeTags.IS_OCEAN)) return;

        placeStructure(level, surfacePos);
    }

    public static boolean placeStructure(ServerLevel world, BlockPos position) {
        try {
            InputStream schemStream = SecretOrderVillagePlacer.class.getResourceAsStream(
                    "/assets/" + NAMESPACE + "/" + PATH
            );

            if (schemStream == null) {
                System.err.println("Schematic not found: " + PATH);
                return false;
            }

            ClipboardFormat format = ClipboardFormats.findByAlias("schematic");
            if (format == null) {
                System.err.println("Unsupported schematic format!");
                return false;
            }

            try (ClipboardReader reader = format.getReader(schemStream)) {
                Clipboard clipboard = reader.read();
                BlockPos basePos = world.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, position);

                try (EditSession editSession = WorldEdit.getInstance().newEditSession((World) world)) {
                    ClipboardHolder holder = new ClipboardHolder(clipboard);
                    holder.createPaste(editSession)
                            .to(BlockVector3.at(basePos.getX(), basePos.getY(), basePos.getZ()))
                            .ignoreAirBlocks(false)
                            .build();

                    // Replace all white wool with terrain block beneath
                    for (int x = 0; x < clipboard.getDimensions().getX(); x++) {
                        for (int z = 0; z < clipboard.getDimensions().getZ(); z++) {
                            BlockVector3 local = BlockVector3.at(x, clipboard.getDimensions().getY() - 1, z);
                            BlockVector3 worldVec = local.add(clipboard.getOrigin()).add(BlockVector3.at(position.getX(), position.getY(), position.getZ()));
                            if (clipboard.getBlock(local).getBlockType().equals(BlockTypes.WHITE_WOOL)) {
                                BlockPos worldPos = new BlockPos(worldVec.getX(), worldVec.getY(), worldVec.getZ());
                                BlockPos below = worldPos.below();
                                net.minecraft.world.level.block.state.BlockState terrainBlock = world.getBlockState(below);
                                BlockType blockType = BlockTypes.get(terrainBlock.getBlock().builtInRegistryHolder().key().location().toString());
                                Pattern pattern = new BlockPattern(blockType.getDefaultState());
                                editSession.setBlock(worldVec, pattern);
                            }
                        }
                    }
                }
            }

            return true;
        } catch (Exception e) {
            System.err.println("Error placing secret order village:");
            e.printStackTrace();
            return false;
        }
    }
}