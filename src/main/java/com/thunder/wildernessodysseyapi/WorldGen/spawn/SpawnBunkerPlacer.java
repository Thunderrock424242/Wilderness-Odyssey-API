package com.thunder.wildernessodysseyapi.WorldGen.spawn;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.processor.BunkerPlacementProcessor;
import com.thunder.wildernessodysseyapi.WorldGen.structure.NBTStructurePlacer;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StarterStructureSpawnGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.Vec3i;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.List;

/**
 * Places the starter bunker during world creation using vanilla structure templates rather than WorldEdit
 * or Starter Structure hooks.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class SpawnBunkerPlacer {
    private static final ResourceLocation BUNKER_ID = ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "bunker");
    private static final int LAND_SEARCH_RADIUS = 512;
    private static final int LAND_SEARCH_STEP = 16;
    private static final int OCEAN_BUFFER_RADIUS = 128;
    private static final int OCEAN_BUFFER_STEP = 16;
    private static final int WATER_SAMPLE_STEP = 4;
    private static final NBTStructurePlacer BUNKER_PLACER = new NBTStructurePlacer(
            BUNKER_ID,
            List.of(new BunkerPlacementProcessor()));

    private SpawnBunkerPlacer() {
    }

    @SubscribeEvent
    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        BlockPos anchor = resolveAnchor(level);
        NBTStructurePlacer.PlacementResult result = placeBunker(level, anchor);
        if (result == null) {
            ModConstants.LOGGER.warn("Spawn bunker placement failed at {}; falling back to vanilla spawn selection.", anchor);
            return;
        }

        applySpawnData(level, result);
        event.setCanceled(true);
    }

    /**
     * Places the spawn bunker so that its leveling marker (or template origin) is anchored at the supplied position.
     */
    public static NBTStructurePlacer.PlacementResult placeBunker(ServerLevel level, BlockPos anchor) {
        return BUNKER_PLACER.placeAnchored(level, anchor);
    }

    private static BlockPos resolveAnchor(ServerLevel level) {
        BlockPos baseSpawn = level.getSharedSpawnPos();
        Vec3i bunkerSize = BUNKER_PLACER.peekSize(level);
        return findLandAnchor(level, baseSpawn, bunkerSize);
    }

    private static BlockPos findLandAnchor(ServerLevel level, BlockPos baseSpawn, Vec3i bunkerSize) {
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(baseSpawn.getX(), level.getMinBuildHeight(), baseSpawn.getZ()));
        if (isViableAnchor(level, surface, bunkerSize)) {
            return surface;
        }

        int baseX = baseSpawn.getX();
        int baseZ = baseSpawn.getZ();
        for (int radius = LAND_SEARCH_STEP; radius <= LAND_SEARCH_RADIUS; radius += LAND_SEARCH_STEP) {
            for (int dx = -radius; dx <= radius; dx += LAND_SEARCH_STEP) {
                for (int dz = -radius; dz <= radius; dz += LAND_SEARCH_STEP) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    int x = baseX + dx;
                    int z = baseZ + dz;
                    BlockPos candidate = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            new BlockPos(x, level.getMinBuildHeight(), z));
                    if (isViableAnchor(level, candidate, bunkerSize)) {
                        return candidate;
                    }
                }
            }
        }

        ModConstants.LOGGER.warn("Unable to locate a land biome within {} blocks of {}; using base spawn.",
                LAND_SEARCH_RADIUS, baseSpawn);
        return surface;
    }

    private static void applySpawnData(ServerLevel level, NBTStructurePlacer.PlacementResult result) {
        List<BlockPos> cryoPositions = result.cryoPositions();
        if (!cryoPositions.isEmpty()) {
            CryoSpawnData data = CryoSpawnData.get(level);
            data.replaceAll(cryoPositions);
            PlayerSpawnHandler.setSpawnBlocks(cryoPositions);
            WorldSpawnHandler.refreshWorldSpawn(level);
        } else {
            PlayerSpawnHandler.setSpawnBlocks(List.of());
        }

        BlockPos spawnTarget = pickSpawnTarget(result);
        level.setDefaultSpawnPos(spawnTarget, 0.0F);

        StarterStructureSpawnGuard.registerSpawnDenyZone(level, result.bounds());

        ModConstants.LOGGER.info("Placed spawn bunker {} at {} with {} cryo tubes.", BUNKER_ID, result.origin(), cryoPositions.size());
    }

    private static BlockPos pickSpawnTarget(NBTStructurePlacer.PlacementResult result) {
        if (!result.cryoPositions().isEmpty()) {
            return result.cryoPositions().get(0);
        }
        return BlockPos.containing(result.bounds().getCenter());
    }

    private static boolean isLandBiome(ServerLevel level, BlockPos pos) {
        return !level.getBiome(pos).is(BiomeTags.IS_OCEAN);
    }

    private static boolean isViableAnchor(ServerLevel level, BlockPos surface, Vec3i bunkerSize) {
        if (!isLandBiome(level, surface)) {
            return false;
        }
        if (!isDrySurface(level, surface)) {
            return false;
        }
        if (isNearOcean(level, surface, OCEAN_BUFFER_RADIUS, OCEAN_BUFFER_STEP)) {
            return false;
        }
        return isAreaDry(level, surface, bunkerSize);
    }

    private static boolean isDrySurface(ServerLevel level, BlockPos surface) {
        return level.getFluidState(surface).isEmpty();
    }

    private static boolean isNearOcean(ServerLevel level, BlockPos surface, int radius, int step) {
        int baseX = surface.getX();
        int baseZ = surface.getZ();
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                BlockPos sample = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        new BlockPos(baseX + dx, level.getMinBuildHeight(), baseZ + dz));
                if (level.getBiome(sample).is(BiomeTags.IS_OCEAN)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isAreaDry(ServerLevel level, BlockPos surface, Vec3i bunkerSize) {
        if (bunkerSize.getX() <= 0 || bunkerSize.getZ() <= 0) {
            return true;
        }
        BlockPos levelingOffset = BUNKER_PLACER.peekLevelingOffset(level);
        BlockPos origin = levelingOffset == null ? surface : surface.subtract(levelingOffset);
        int minX = origin.getX();
        int minZ = origin.getZ();
        int maxX = minX + bunkerSize.getX() - 1;
        int maxZ = minZ + bunkerSize.getZ() - 1;
        int minY = origin.getY();
        int maxY = origin.getY() + bunkerSize.getY() - 1;
        int clampedMinY = Math.max(minY, level.getMinBuildHeight());
        int clampedMaxY = Math.min(maxY, level.getMaxBuildHeight() - 1);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x += WATER_SAMPLE_STEP) {
            for (int z = minZ; z <= maxZ; z += WATER_SAMPLE_STEP) {
                for (int y = clampedMinY; y <= clampedMaxY; y++) {
                    cursor.set(x, y, z);
                    if (!level.getFluidState(cursor).isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
