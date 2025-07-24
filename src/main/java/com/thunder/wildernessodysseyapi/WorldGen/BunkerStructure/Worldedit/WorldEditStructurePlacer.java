package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.Worldedit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.TerrainBlockReplacer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.io.InputStream;

/**
 * The type World edit structure placer.
 */
public class WorldEditStructurePlacer {
    private final String namespace;
    private final String path;

    /**
     * Instantiates a new World edit structure placer.
     *
     * @param namespace the namespace
     * @param path      the path
     */
    public WorldEditStructurePlacer(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    /**
     * Places the schematic at the given position and returns its world bounds.
     *
     * @param world    the world
     * @param position the position
     * @return the bounding box of the pasted structure or {@code null} on error
     */
    public AABB placeStructure(ServerLevel world, BlockPos position) {
        try {
            InputStream schemStream = getClass().getResourceAsStream(
                    "/assets/" + namespace + "/schematics/" + path
            );

            if (schemStream == null) {
                System.out.println("Schematic file not found: " + namespace + "/" + path);
                return null;
            }

            ClipboardFormat format = ClipboardFormats.findByAlias("schematic");
            if (format == null) {
                throw new IllegalArgumentException("Unsupported schematic format!");
            }

            try (ClipboardReader reader = format.getReader(schemStream)) {
                Clipboard clipboard = reader.read();
                BlockPos surfacePos = world.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, position);

                BlockVector3 min = clipboard.getRegion().getMinimumPoint();
                BlockVector3 max = clipboard.getRegion().getMaximumPoint();
                AABB bounds = new AABB(
                        min.getBlockX() + surfacePos.getX(), min.getBlockY() + surfacePos.getY(), min.getBlockZ() + surfacePos.getZ(),
                        max.getBlockX() + surfacePos.getX(), max.getBlockY() + surfacePos.getY(), max.getBlockZ() + surfacePos.getZ()
                );

                try (EditSession editSession = WorldEdit.getInstance().newEditSession((World) world)) {
                    ClipboardHolder holder = new ClipboardHolder(clipboard);

                    // Iterate through the clipboard's region
                    clipboard.getRegion().forEach(blockVector -> {
                        if (clipboard.getFullBlock(blockVector).getBlockType().equals(BlockTypes.WHITE_WOOL)) {
                            BlockPos terrainPos = new BlockPos(
                                    blockVector.x() + surfacePos.getX(),
                                    blockVector.y() + surfacePos.getY(),
                                    blockVector.z() + surfacePos.getZ()
                            );

                            // Delegate block replacement to the utility class
                            try {
                                TerrainBlockReplacer.replaceBlockWithTerrain(editSession, world, blockVector, terrainPos);
                            } catch (MaxChangedBlocksException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });


                    // Paste the BunkerStructure into the world
                    holder.createPaste(editSession)
                            .to(BlockVector3.at(surfacePos.getX(), surfacePos.getY(), surfacePos.getZ()))
                            .ignoreAirBlocks(false)
                            .build();
                }
                return bounds;
            }
        } catch (Exception e) {
            System.out.println("Error placing BunkerStructure:");
            e.printStackTrace();
            return null;
        }
    }
}
