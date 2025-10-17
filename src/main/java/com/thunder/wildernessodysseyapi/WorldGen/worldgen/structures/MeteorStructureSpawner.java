package com.thunder.wildernessodysseyapi.WorldGen.worldgen.structures;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.BunkerProtectionHandler;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.StructureSpawnTracker;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.Worldedit.WorldEditStructurePlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

/****
 * MeteorStructureSpawner for the Wilderness Odyssey API mod.
 */
public class MeteorStructureSpawner {
    private static final int IMPACT_SITE_COUNT = 3;
    private static final int MIN_CHUNK_SEPARATION = 1000;
    private static final int MIN_BLOCK_SEPARATION = MIN_CHUNK_SEPARATION * 16;
    private static final int POSITION_ATTEMPTS = 64;

    private static final ResourceLocation METEOR_TEMPLATE_ID = ResourceLocation.tryBuild(MOD_ID, "impact_zone");
    private static final WorldEditStructurePlacer METEOR_SITE_PLACER =
            new WorldEditStructurePlacer(ModConstants.MOD_ID, "impact_zone.schem");
    private static final WorldEditStructurePlacer BUNKER_PLACER =
            new WorldEditStructurePlacer(ModConstants.MOD_ID, "bunker.schem");

    private static boolean placed = false;

    public static void tryPlace(ServerLevel level) {
        if (placed) {
            return;
        }

        MeteorImpactData impactData = MeteorImpactData.get(level);
        RandomSource random = RandomSource.create(level.getSeed());
        BlockPos spawn = level.getSharedSpawnPos();

        List<BlockPos> storedSites = new ArrayList<>(impactData.getImpactPositions());
        int originalCount = storedSites.size();

        for (int i = storedSites.size(); i < IMPACT_SITE_COUNT; i++) {
            BlockPos origin = findImpactOrigin(level, random, spawn, storedSites);
            BlockPos impactPos = placeMeteorSite(level, origin);
            storedSites.add(impactPos);
        }

        if (storedSites.size() != originalCount) {
            impactData.setImpactPositions(storedSites);
        }

        if (impactData.getBunkerPos() == null && !storedSites.isEmpty()) {
            BlockPos bunkerAnchor = storedSites.get(random.nextInt(storedSites.size()));
            placeBunker(level, bunkerAnchor, impactData);
        }

        placed = true;
    }

    private static BlockPos placeMeteorSite(ServerLevel level, BlockPos origin) {
        if (ModList.get().isLoaded("worldedit")) {
            AABB bounds = METEOR_SITE_PLACER.placeStructure(level, origin);
            if (bounds != null) {
                return BlockPos.containing(bounds.getCenter());
            }
        }

        if (METEOR_TEMPLATE_ID == null) {
            return origin;
        }

        StructureTemplateManager manager = level.getStructureManager();
        Optional<StructureTemplate> templateOpt = manager.get(METEOR_TEMPLATE_ID);
        if (templateOpt.isEmpty()) {
            return origin;
        }

        StructureTemplate template = templateOpt.get();
        StructurePlaceSettings settings = new StructurePlaceSettings();
        BlockPos spawnPos = origin;
        template.placeInWorld(level, spawnPos, spawnPos, settings, level.random, 2);
        return spawnPos;
    }

    private static void placeBunker(ServerLevel level, BlockPos impactPos, MeteorImpactData impactData) {
        if (!ModList.get().isLoaded("worldedit")) {
            return;
        }

        BlockPos bunkerPos = impactPos.offset(32, 0, 0);
        bunkerPos = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, bunkerPos);

        StructureSpawnTracker tracker = StructureSpawnTracker.get(level);
        if (tracker.hasSpawnedAt(bunkerPos)) {
            impactData.setBunkerPos(bunkerPos);
            return;
        }

        if (!WorldEditStructurePlacer.isWorldEditReady()) {
            impactData.setBunkerPos(bunkerPos);
            scheduleDeferredPlacement(level, bunkerPos, tracker, impactData, 0);
            return;
        }

        AABB bounds = BUNKER_PLACER.placeStructure(level, bunkerPos);
        if (bounds != null) {
            BunkerProtectionHandler.addBunkerBounds(bounds);
            tracker.addSpawnPos(bunkerPos);
            impactData.setBunkerPos(bunkerPos);
        }
    }

    private static void scheduleDeferredPlacement(ServerLevel level, BlockPos bunkerPos,
                                                  StructureSpawnTracker tracker,
                                                  MeteorImpactData impactData, int attempt) {
        level.getServer().execute(() -> {
            if (tracker.hasSpawnedAt(bunkerPos)) {
                impactData.setBunkerPos(bunkerPos);
                return;
            }

            if (!WorldEditStructurePlacer.isWorldEditReady()) {
                if (attempt == 40) {
                    ModConstants.LOGGER.warn(
                            "WorldEdit still initializing; meteor bunker placement at {} delayed",
                            bunkerPos
                    );
                }
                if (attempt < 100) {
                    scheduleDeferredPlacement(level, bunkerPos, tracker, impactData, attempt + 1);
                }
                return;
            }

            AABB bounds = BUNKER_PLACER.placeStructure(level, bunkerPos);
            if (bounds != null) {
                BunkerProtectionHandler.addBunkerBounds(bounds);
                tracker.addSpawnPos(bunkerPos);
                impactData.setBunkerPos(bunkerPos);
            }
        });
    }

    private static BlockPos findImpactOrigin(ServerLevel level, RandomSource random, BlockPos reference,
                                             List<BlockPos> existing) {
        long minDistanceSq = (long) MIN_BLOCK_SEPARATION * (long) MIN_BLOCK_SEPARATION;

        for (int attempt = 0; attempt < POSITION_ATTEMPTS; attempt++) {
            int distance = MIN_BLOCK_SEPARATION + random.nextInt(MIN_BLOCK_SEPARATION / 2);
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int x = reference.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = reference.getZ() + (int) Math.round(Math.sin(angle) * distance);

            BlockPos candidate = new BlockPos(x, 0, z);
            boolean farEnough = true;
            for (BlockPos existingPos : existing) {
                long dx = (long) candidate.getX() - existingPos.getX();
                long dz = (long) candidate.getZ() - existingPos.getZ();
                long distSq = dx * dx + dz * dz;
                if (distSq < minDistanceSq) {
                    farEnough = false;
                    break;
                }
            }

            if (farEnough) {
                return level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, candidate);
            }
        }

        BlockPos fallback = reference.offset(MIN_BLOCK_SEPARATION * (existing.size() + 1), 0, 0);
        return level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, fallback);
    }
}
