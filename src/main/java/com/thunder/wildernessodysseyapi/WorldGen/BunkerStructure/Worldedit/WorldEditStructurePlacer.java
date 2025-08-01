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
import com.thunder.wildernessodysseyapi.WorldGen.schematic.SchematicManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.io.InputStream;

/**
 * The type World edit structure placer.
 */
public class WorldEditStructurePlacer {
    private final ResourceLocation id;

    /**
     * Instantiates a new World edit structure placer.
     *
     * @param namespace the namespace
     * @param path      the path
     */
    public WorldEditStructurePlacer(String namespace, String path) {
        this(ResourceLocation.tryBuild(namespace, path.replaceFirst("\\.schem$", "")));
    }

    /**
     * Creates a placer for the given schematic id. The id corresponds to
     * {@code data/<namespace>/structures/<path>.schem} in data packs.
     */
    public WorldEditStructurePlacer(ResourceLocation id) {
        this.id = id;
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
            Clipboard clipboard = SchematicManager.INSTANCE.get(id);
            if (clipboard == null) {
                InputStream schemStream = getClass().getResourceAsStream(
                        "/assets/" + id.getNamespace() + "/schematics/" + id.getPath() + ".schem"
                );
                if (schemStream != null) {
                    ClipboardFormat format = ClipboardFormats.findByAlias("schematic");
                    if (format != null) {
                        try (ClipboardReader reader = format.getReader(schemStream)) {
                            clipboard = reader.read();
                        }
                    }
                }
            }


            if (clipboard == null) {
                System.out.println("Schematic file not found: " + id);
                return null;
            // Detect the Sponge schematic format used by our bundled file
            ClipboardFormat format = ClipboardFormats.findByAlias("schem");
            if (format == null) {
                throw new IllegalArgumentException("Unsupported schematic format!");

            }

            final BlockPos surfacePos = world.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, position);

            BlockVector3 min = clipboard.getRegion().getMinimumPoint();
            BlockVector3 max = clipboard.getRegion().getMaximumPoint();
            AABB bounds = new AABB(
                    min.getBlockX() + surfacePos.getX(), min.getBlockY() + surfacePos.getY(), min.getBlockZ() + surfacePos.getZ(),
                    max.getBlockX() + surfacePos.getX(), max.getBlockY() + surfacePos.getY(), max.getBlockZ() + surfacePos.getZ()
            );

            try (final EditSession editSession = WorldEdit.getInstance().newEditSession((World) world)) {
                ClipboardHolder holder = new ClipboardHolder(clipboard);

                // Iterate through the clipboard's region and replace placeholder blocks
                for (BlockVector3 vec : clipboard.getRegion()) {
                    if (clipboard.getFullBlock(vec).getBlockType().equals(BlockTypes.WHITE_WOOL)) {
                        try {
                            TerrainBlockReplacer.replaceBlockWithTerrainRelative(editSession, world, vec, surfacePos);
                        } catch (MaxChangedBlocksException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                // Paste the structure into the world
                holder.createPaste(editSession)
                        .to(BlockVector3.at(surfacePos.getX(), surfacePos.getY(), surfacePos.getZ()))
                        .ignoreAirBlocks(false)
                        .build();
            }
            return bounds;
        } catch (Exception e) {
            System.out.println("Error placing BunkerStructure:");
            e.printStackTrace();
            return null;
        }
    }
}
