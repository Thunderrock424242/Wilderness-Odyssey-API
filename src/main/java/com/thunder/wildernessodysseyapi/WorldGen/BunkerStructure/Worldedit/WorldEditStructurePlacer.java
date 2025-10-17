package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.Worldedit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.SpawnBlock.CryoSpawnData;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.SpawnBlock.PlayerSpawnHandler;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.TerrainBlockReplacer;
import com.thunder.wildernessodysseyapi.WorldGen.schematic.SchematicManager;
import net.neoforged.fml.ModList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;
import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

/**
 * The type World edit structure placer.
 */
public class WorldEditStructurePlacer {
    private final ResourceLocation id;

    // Cache WorldEdit availability to avoid repeated startup checks and log spam
    private static boolean worldEditReady = false;
    private static boolean missingLogged = false;
    private static final Set<ResourceLocation> missingSchematicsLogged = new HashSet<>();
    private static final Set<ResourceLocation> missingCryoTubeLogged = new HashSet<>();
    private static final ResourceLocation CRYO_TUBE_ID = ResourceLocation.tryBuild(MOD_ID, "cryo_tube");

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
            if (!isWorldEditReady()) {
                if (!missingLogged) {
                    LOGGER.warn("WorldEdit not initialized (block registry not ready); skipping placement of {}", id);
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

            BlockVector3 origin = clipboard.getOrigin();
            List<BlockPos> cryoTubes = null;

            // Ensure bunker schematics contain a cryo tube so we don't place
            // incomplete structures. The previous implementation used the cryo
            // tube as a duplication check which prevented placement if a cryo
            // tube already existed in the world. Instead we now simply verify
            // the schematic includes at least one cryo tube block and capture
            // their world positions for reuse when players spawn.
            if (id.getPath().contains("bunker")) {
                BlockType cryoType = null;
                try {
                    cryoType = BlockTypes.get("wildernessodysseyapi:cryo_tube");
                } catch (Exception ignored) {
                }

                cryoTubes = new ArrayList<>();
                if (cryoType != null) {
                    for (BlockVector3 vec : clipboard.getRegion()) {
                        if (clipboard.getFullBlock(vec).getBlockType().equals(cryoType)) {
                            cryoTubes.add(toWorldPos(surfacePos, origin, vec));
                        }
                    }
                }

                if (cryoTubes.isEmpty()) {
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

                // Paste the structure into the world and ensure the operation is completed
                Operations.complete(
                        holder.createPaste(editSession)
                                .to(BlockVector3.at(surfacePos.getX(), surfacePos.getY(), surfacePos.getZ()))
                                .ignoreAirBlocks(false)
                                .build()
                );
                editSession.flushSession();
            }
            if (cryoTubes != null && !cryoTubes.isEmpty()) {
                if (CRYO_TUBE_ID != null) {
                    Block cryoBlock = BuiltInRegistries.BLOCK.getOptional(CRYO_TUBE_ID).orElse(null);
                    if (cryoBlock != null) {
                        for (BlockPos cryoPos : cryoTubes) {
                            if (!world.getBlockState(cryoPos).is(cryoBlock)) {
                                world.setBlockAndUpdate(cryoPos, cryoBlock.defaultBlockState());
                            }
                        }
                    }
                }
                CryoSpawnData data = CryoSpawnData.get(world);
                data.addAll(cryoTubes);
                PlayerSpawnHandler.setSpawnBlocks(data.getPositions());
            }
            return bounds;
        } catch (Throwable e) {
            LOGGER.error("Error placing BunkerStructure {}", id, e);
            return null;
        }
    }

    /**
     * Quickly checks if WorldEdit's block registry is ready without blocking the server thread.
     */
    public static boolean isWorldEditReady() {
        if (worldEditReady) {
            return true;
        }
        if (!ModList.get().isLoaded("worldedit")) {
            return false;
        }
        if (isBlockRegistryReady()) {
            worldEditReady = true;
        }
        return worldEditReady;
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

    private static BlockPos toWorldPos(BlockPos surfacePos, BlockVector3 origin, BlockVector3 vec) {
        int ox = origin == null ? 0 : origin.x();
        int oy = origin == null ? 0 : origin.y();
        int oz = origin == null ? 0 : origin.z();
        return new BlockPos(
                surfacePos.getX() + vec.x() - ox,
                surfacePos.getY() + vec.y() - oy,
                surfacePos.getZ() + vec.z() - oz
        );
    }
}
