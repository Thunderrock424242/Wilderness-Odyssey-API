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
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.TerrainBlockReplacer;
import com.thunder.wildernessodysseyapi.WorldGen.schematic.SchematicManager;
import com.thunder.wildernessodysseyapi.WorldGen.blocks.CryoTubeBlock;
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
            // WorldEdit's block registry is not initialized until the mod fully loads.
            // If another mod calls into WorldEdit too early, static entries like
            // BlockTypes.AIR remain null and cause NPEs when reading schematics.
            if (BlockTypes.AIR == null) {
                System.out.println("WorldEdit not initialized (BlockTypes.AIR is null); skipping placement of " + id);
                return null;
            }
            Clipboard clipboard = SchematicManager.INSTANCE.get(id);
            if (clipboard == null) {
                InputStream schemStream = getClass().getResourceAsStream(
                        "/assets/" + id.getNamespace() + "/schematics/" + id.getPath() + ".schem"
                );
                if (schemStream != null) {
                    ClipboardFormat format = ClipboardFormats.findByAlias("schem");
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
            }

            final BlockPos surfacePos = world.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, position);

            BlockVector3 min = clipboard.getRegion().getMinimumPoint();
            BlockVector3 max = clipboard.getRegion().getMaximumPoint();

            // Prevent duplicating bunker structures by checking for a key block
            if (id.getPath().contains("bunker")) {
                BlockType cryoType = null;
                try {
                    cryoType = BlockTypes.get("wildernessodysseyapi:cryo_tube");
                } catch (Exception ignored) {
                }
                if (cryoType != null) {
                    for (BlockVector3 vec : clipboard.getRegion()) {
                        if (clipboard.getFullBlock(vec).getBlockType().equals(cryoType)) {
                            BlockPos checkPos = surfacePos.offset(
                                    vec.getBlockX() - min.getBlockX(),
                                    vec.getBlockY() - min.getBlockY(),
                                    vec.getBlockZ() - min.getBlockZ()
                            );
                            if (world.getBlockState(checkPos).is(CryoTubeBlock.CRYO_TUBE.get())) {
                                System.out.println("Bunker structure already present at " + checkPos + ", skipping placement.");
                                return null;
                            }
                            break;
                        }
                    }
                }
            }

            AABB bounds = new AABB(
                    min.getBlockX() + surfacePos.getX(), min.getBlockY() + surfacePos.getY(), min.getBlockZ() + surfacePos.getZ(),
                    max.getBlockX() + surfacePos.getX(), max.getBlockY() + surfacePos.getY(), max.getBlockZ() + surfacePos.getZ()
            );

            try (final EditSession editSession = WorldEdit.getInstance().newEditSession((World) world)) {
                ClipboardHolder holder = new ClipboardHolder(clipboard);

                // Iterate through the clipboard's region and replace placeholder blocks
                BlockType terrainReplacerType = null;
                try {
                    terrainReplacerType = BlockTypes.get("wildernessodysseyapi:terrain_replacer");
                } catch (Exception ignored) {
                }

                for (BlockVector3 vec : clipboard.getRegion()) {
                    if (terrainReplacerType != null &&
                            clipboard.getFullBlock(vec).getBlockType().equals(terrainReplacerType)) {
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
