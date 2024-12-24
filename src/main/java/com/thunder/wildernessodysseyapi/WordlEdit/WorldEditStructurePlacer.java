package com.thunder.wildernessodysseyapi.WordlEdit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.io.InputStream;

public class WorldEditStructurePlacer {
    private final String namespace;
    private final String path;

    public WorldEditStructurePlacer(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    public boolean placeStructure(ServerLevel world, BlockPos position) {
        try {
            InputStream schemStream = getClass().getResourceAsStream(
                    "/assets/" + namespace + "/structures/" + path
            );

            if (schemStream == null) {
                System.out.println("Schematic file not found: " + namespace + "/" + path);
                return false;
            }

            ClipboardFormat format = ClipboardFormats.findByAlias("schematic");
            if (format == null) {
                throw new IllegalArgumentException("Unsupported schematic format!");
            }

            try (ClipboardReader reader = format.getReader(schemStream)) {
                Clipboard clipboard = reader.read();
                BlockPos surfacePos = world.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, position);

                try (EditSession editSession = WorldEdit.getInstance().newEditSession((World) world)) {
                    ClipboardHolder holder = new ClipboardHolder(clipboard);

                    // Iterate through the clipboard's region
                    clipboard.getRegion().forEach(blockVector -> {
                        if (clipboard.getFullBlock(blockVector).getBlockType().equals(BlockTypes.WHITE_WOOL)) {
                            BlockPos terrainPos = new BlockPos(
                                    blockVector.getBlockX() + surfacePos.getX(),
                                    blockVector.getBlockY() + surfacePos.getY(),
                                    blockVector.getBlockZ() + surfacePos.getZ()
                            );

                            // Delegate block replacement to the utility class
                            TerrainBlockReplacer.replaceBlockWithTerrain(editSession, world, blockVector, terrainPos);
                        }
                    });

                    // Paste the BunkerStructure into the world
                    holder.createPaste(editSession)
                            .to(BlockVector3.at(surfacePos.getX(), surfacePos.getY(), surfacePos.getZ()))
                            .ignoreAirBlocks(false)
                            .build();
                }
            }

            return true;
        } catch (Exception e) {
            System.out.println("Error placing BunkerStructure:");
            e.printStackTrace();
            return false;
        }
    }
}
