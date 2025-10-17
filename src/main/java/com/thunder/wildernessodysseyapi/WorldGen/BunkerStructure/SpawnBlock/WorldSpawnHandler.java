package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.SpawnBlock;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.StructureSpawnTracker;
import com.thunder.wildernessodysseyapi.WorldGen.schematic.SchematicManager;
import com.thunder.wildernessodysseyapi.WorldGen.worldgen.configurable.StructureConfig;
import com.thunder.wildernessodysseyapi.WorldGen.worldgen.structures.MeteorImpactData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.LOGGER;
import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

/**
 * Handles selecting a world spawn location based on cryo tube placements.
 */
@EventBusSubscriber(modid = MOD_ID)
public class WorldSpawnHandler {

    private static final ResourceLocation BUNKER_ID = ResourceLocation.tryBuild(MOD_ID, "bunker");

    /**
     * On world load.
     *
     * @param event the event
     */
    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel world)) {
            return;
        }

        boolean ignoreCryo = StructureConfig.DEBUG_IGNORE_CRYO_TUBE.get();

        List<BlockPos> spawnBlockPositions = new ArrayList<>(CryoSpawnData.get(world).getPositions());

        if (spawnBlockPositions.isEmpty()) {
            spawnBlockPositions = rebuildSpawnCache(world);
            if (!spawnBlockPositions.isEmpty()) {
                CryoSpawnData.get(world).replaceAll(spawnBlockPositions);
            }
        }

        if (!spawnBlockPositions.isEmpty()) {
            MeteorImpactData impactData = MeteorImpactData.get(world);
            BlockPos meteorPos = impactData.getBunkerPos();
            if (meteorPos == null) {
                List<BlockPos> impactSites = impactData.getImpactPositions();
                if (!impactSites.isEmpty()) {
                    meteorPos = impactSites.get(0);
                }
            }
            BlockPos spawnBlockPos;
            List<BlockPos> filteredPositions = spawnBlockPositions;
            if (meteorPos != null) {
                final BlockPos targetPos = meteorPos;
                BlockPos closest = spawnBlockPositions.stream()
                        .min((a, b) -> Double.compare(a.distSqr(targetPos), b.distSqr(targetPos)))
                        .orElse(spawnBlockPositions.get(0));
                final double maxDist = 400.0; // radius squared (20 blocks)
                filteredPositions = spawnBlockPositions.stream()
                        .filter(p -> p.distSqr(closest) <= maxDist)
                        .collect(Collectors.toList());
                spawnBlockPos = closest;
            } else {
                Random random = new Random();
                spawnBlockPos = spawnBlockPositions.get(random.nextInt(spawnBlockPositions.size()));
            }

            if (!ignoreCryo) {
                PlayerSpawnHandler.setSpawnBlocks(filteredPositions);
                world.setDefaultSpawnPos(spawnBlockPos.above(), 0.0F);
            } else {
                PlayerSpawnHandler.setSpawnBlocks(spawnBlockPositions);
                BlockPos debugSpawnAnchor = meteorPos != null ? meteorPos : spawnBlockPos;
                world.setDefaultSpawnPos(debugSpawnAnchor.above(), 0.0F);
            }
        } else {
            PlayerSpawnHandler.setSpawnBlocks(Collections.emptyList());
            // Log a warning or handle cases where no spawn blocks are found
            LOGGER.warn("No Cryo Tube Blocks found in the world!");
        }
    }

    private static List<BlockPos> rebuildSpawnCache(ServerLevel world) {
        if (!ModList.get().isLoaded("worldedit")) {
            return Collections.emptyList();
        }

        StructureSpawnTracker tracker = StructureSpawnTracker.get(world);
        if (!tracker.hasSpawned()) {
            return Collections.emptyList();
        }

        Clipboard clipboard = loadBunkerClipboard();
        if (clipboard == null) {
            return Collections.emptyList();
        }

        BlockType cryoType;
        try {
            cryoType = BlockTypes.get(MOD_ID + ":cryo_tube");
        } catch (Exception e) {
            return Collections.emptyList();
        }

        BlockVector3 origin = clipboard.getOrigin();
        List<BlockPos> relative = new ArrayList<>();
        for (BlockVector3 vec : clipboard.getRegion()) {
            if (clipboard.getFullBlock(vec).getBlockType().equals(cryoType)) {
                relative.add(new BlockPos(
                        vec.x() - origin.x(),
                        vec.y() - origin.y(),
                        vec.z() - origin.z()
                ));
            }
        }

        if (relative.isEmpty()) {
            return Collections.emptyList();
        }

        List<BlockPos> absolute = new ArrayList<>();
        for (BlockPos base : tracker.getSpawnPositions()) {
            for (BlockPos offset : relative) {
                absolute.add(base.offset(offset.getX(), offset.getY(), offset.getZ()));
            }
        }
        return absolute;
    }

    private static Clipboard loadBunkerClipboard() {
        Clipboard clipboard = SchematicManager.INSTANCE.get(BUNKER_ID);
        if (clipboard != null) {
            return clipboard;
        }

        try (InputStream schemStream = WorldSpawnHandler.class.getResourceAsStream(
                "/assets/" + BUNKER_ID.getNamespace() + "/schematics/" + BUNKER_ID.getPath() + ".schem")) {
            if (schemStream == null) {
                return null;
            }
            ClipboardFormat format = ClipboardFormats.findByAlias("schem");
            if (format == null) {
                return null;
            }
            try (ClipboardReader reader = format.getReader(schemStream)) {
                return reader.read();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load bunker schematic for spawn reconstruction", e);
            return null;
        }
    }
}
