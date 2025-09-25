package com.thunder.wildernessodysseyapi.WorldGen.worldgen.structures;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.BunkerProtectionHandler;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.StructureSpawnTracker;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.Worldedit.WorldEditStructurePlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;

import java.util.Optional;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;
import static com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.BunkerStructureGenerator.scheduleDeferredPlacement;

/****
 * MeteorStructureSpawner for the Wilderness Odyssey API mod.
 */
public class MeteorStructureSpawner {
    private static final ResourceLocation METEOR_TEMPLATE_ID = ResourceLocation.tryBuild(MOD_ID, "meteor_bunker");
    private static final WorldEditStructurePlacer METEOR_SITE_PLACER =
            new WorldEditStructurePlacer(ModConstants.MOD_ID, "meteor_site.schem");
    private static final WorldEditStructurePlacer BUNKER_PLACER =
            new WorldEditStructurePlacer(ModConstants.MOD_ID, "bunker.schem");

    private static boolean placed = false;

    public static void tryPlace(ServerLevel level) {
        // Only place once in the entire world
        if (placed) {
            return;
        }

        BlockPos impactOrigin = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, BlockPos.ZERO);
        BlockPos impactPos = placeMeteorSite(level, impactOrigin);
        MeteorImpactData.get(level).setImpactPos(impactPos);

        placeBunker(level, impactPos);

        placed = true;
    }

    private static BlockPos placeMeteorSite(ServerLevel level, BlockPos origin) {
        if (ModList.get().isLoaded("worldedit")) {
            AABB bounds = METEOR_SITE_PLACER.placeStructure(level, origin);
            if (bounds != null) {
                return BlockPos.containing(bounds.getCenter());
            }
        }

        if (METEOR_TEMPLATE_ID != null) {
            StructureTemplateManager manager = level.getStructureManager();
            Optional<StructureTemplate> templateOpt = manager.get(METEOR_TEMPLATE_ID);
            if (templateOpt.isPresent()) {
                StructureTemplate template = templateOpt.get();
                StructurePlaceSettings settings = new StructurePlaceSettings();
                template.placeInWorld(level, spawnPos, spawnPos, settings, level.random, 2);

                // place bunker two chunks east of the meteor
                BlockPos bunkerPos = spawnPos.offset(32, 0, 0);
                bunkerPos = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, bunkerPos);
                // Prefer schematics from data packs but fall back to bundled resource
                WorldEditStructurePlacer placer = new WorldEditStructurePlacer(ModConstants.MOD_ID, "bunker.schem");
                StructureSpawnTracker tracker = StructureSpawnTracker.get(level);
                if (!tracker.hasSpawnedAt(bunkerPos)) {
                    if (!WorldEditStructurePlacer.isWorldEditReady()) {
                        scheduleDeferredPlacement(level, bunkerPos, tracker, 0);
                    } else {
                        var bounds = placer.placeStructure(level, bunkerPos);
                        if (bounds != null) {
                            BunkerProtectionHandler.addBunkerBounds(bounds);
                            tracker.addSpawnPos(bunkerPos);
                        }
                    }
                    template.placeInWorld(level, origin, origin, settings, level.random, 2);
                }
            }

            return origin;
        }

        private static void placeBunker (ServerLevel level, BlockPos impactPos){
            if (!ModList.get().isLoaded("worldedit")) {
                return;
            }

            BlockPos bunkerPos = impactPos.offset(32, 0, 0);
            bunkerPos = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, bunkerPos);

            StructureSpawnTracker tracker = StructureSpawnTracker.get(level);
            if (tracker.hasSpawnedAt(bunkerPos)) {
                return;
            }

            AABB bounds = BUNKER_PLACER.placeStructure(level, bunkerPos);
            if (bounds != null) {
                BunkerProtectionHandler.addBunkerBounds(bounds);
                tracker.addSpawnPos(bunkerPos);
            }
        }

        private static void scheduleDeferredPlacement (ServerLevel level, BlockPos bunkerPos,
                StructureSpawnTracker tracker,int attempt){
            level.getServer().execute(() -> {
                if (tracker.hasSpawnedAt(bunkerPos)) {
                    return;
                }
                if (!WorldEditStructurePlacer.isWorldEditReady()) {
                    if (attempt == 40) {
                        ModConstants.LOGGER.warn("WorldEdit still initializing; meteor bunker placement at {} delayed", bunkerPos);
                    }
                    if (attempt < 100) {
                        scheduleDeferredPlacement(level, bunkerPos, tracker, attempt + 1);
                    }
                    return;
                }
                WorldEditStructurePlacer placer = new WorldEditStructurePlacer(ModConstants.MOD_ID, "bunker.schem");
                var bounds = placer.placeStructure(level, bunkerPos);
                if (bounds != null) {
                    BunkerProtectionHandler.addBunkerBounds(bounds);
                    tracker.addSpawnPos(bunkerPos);
                }
            });
        }
    }
}
