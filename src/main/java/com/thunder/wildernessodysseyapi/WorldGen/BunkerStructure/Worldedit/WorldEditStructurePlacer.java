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
import net.neoforged.fml.ModList;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.TerrainBlockReplacer;
import com.thunder.wildernessodysseyapi.WorldGen.schematic.SchematicManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;

/**
 * The type World edit structure placer.
 */
public class WorldEditStructurePlacer {
    private final ResourceLocation id;

    // Cache WorldEdit availability to avoid repeated startup checks and log spam
    private static Boolean worldEditReady = null;
    private static boolean missingLogged = false;
    private static final Set<ResourceLocation> missingSchematicsLogged = new HashSet<>();
    private static final Set<ResourceLocation> missingCryoTubeLogged = new HashSet<>();

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
            // Wait for WorldEdit to finish booting so BlockTypes are populated.
            if (!waitForWorldEdit()) {
                if (!missingLogged) {
                    LOGGER.warn("WorldEdit not initialized (BlockTypes.AIR is null); skipping placement of {}", id);
                    missingLogged = true;
                }
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
                if (missingSchematicsLogged.add(id)) {
                    LOGGER.warn("Schematic file not found: {}", id);
                }
                return null;
            }

            final BlockPos surfacePos = world.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, position);

            BlockVector3 min = clipboard.getRegion().getMinimumPoint();
            BlockVector3 max = clipboard.getRegion().getMaximumPoint();

            // Ensure bunker schematics contain a cryo tube so we don't place
            // incomplete structures. The previous implementation used the cryo
            // tube as a duplication check which prevented placement if a cryo
            // tube already existed in the world. Instead we now simply verify
            // the schematic includes at least one cryo tube block.
            if (id.getPath().contains("bunker")) {
                BlockType cryoType = null;
                try {
                    cryoType = BlockTypes.get("wildernessodysseyapi:cryo_tube");
                } catch (Exception ignored) {
                }

                boolean hasCryoTube = false;
                if (cryoType != null) {
                    for (BlockVector3 vec : clipboard.getRegion()) {
                        if (clipboard.getFullBlock(vec).getBlockType().equals(cryoType)) {
                            hasCryoTube = true;
                            break;
                        }
                    }
                }

                if (!hasCryoTube) {
                    if (missingCryoTubeLogged.add(id)) {
                        LOGGER.warn("Skipping bunker placement: schematic missing cryo tube in {}", id);
                    }
                    return null;
                }
            }

            AABB bounds = new AABB(
                    min.x() + surfacePos.getX(), min.y() + surfacePos.getY(), min.z() + surfacePos.getZ(),
                    max.x() + surfacePos.getX(), max.y() + surfacePos.getY(), max.z() + surfacePos.getZ()
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
        } catch (Throwable e) {
            LOGGER.error("Error placing BunkerStructure {}", id, e);
            return null;
        }
    }

    /**
     * Wait briefly for WorldEdit to populate its block registry. Returns {@code true} when
     * WorldEdit can resolve the {@code minecraft:air} block or {@code false} if WorldEdit is missing
     * or still uninitialized after waiting. Some WorldEdit versions no longer populate the
     * {@code BlockTypes.AIR} constant, so we also fall back to querying the registry directly.
     */
    private static boolean waitForWorldEdit() {
        if (worldEditReady != null) {
            return worldEditReady;
        }
        try {
            if (isBlockRegistryReady()) {
                worldEditReady = true;
                return true;
            }
            if (!ModList.get().isLoaded("worldedit")) {
                worldEditReady = false;
                return false;
            }
            for (int i = 0; i < 50 && !isBlockRegistryReady(); i++) {
                WorldEdit.getInstance();
                try {
                    BlockTypes.get("minecraft:air");
                } catch (Throwable ignored) {
                }
                Thread.sleep(100);
            }
            worldEditReady = isBlockRegistryReady();
            return worldEditReady;
        } catch (Throwable t) {
            worldEditReady = false;
            return false;
        }
    }

    /**
     * Checks if WorldEdit's block registry is ready. Some versions expose {@code BlockTypes.AIR}
     * while others require an explicit lookup.
     */
    private static boolean isBlockRegistryReady() {
        try {
            if (BlockTypes.AIR != null) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            return BlockTypes.get("minecraft:air") != null;
        } catch (Throwable t) {
            return false;
        }
    }
}
