package com.thunder.wildernessodysseyapi.worldgen.spawn;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.worldgen.processor.BunkerPlacementProcessor;
import com.thunder.wildernessodysseyapi.worldgen.structure.NBTStructurePlacer;
import com.thunder.wildernessodysseyapi.worldgen.structure.StarterStructureSpawnGuard;
import com.thunder.wildernessodysseyapi.worldgen.structure.TerrainReplacerEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.List;
import java.util.Set;

/**
 * Places the starter bunker during world creation using vanilla structure templates rather than WorldEdit
 * or Starter Structure hooks.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public final class SpawnBunkerPlacer {
    private static final ResourceLocation BUNKER_ID = ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "bunker");
    private static final int OCEAN_SEARCH_RADIUS = 4096;
    private static final int OCEAN_SEARCH_STEP = 32;
    private static final int MAX_ANCHOR_CANDIDATES = 2048;
    private static final int FAST_OCEAN_SAMPLE_OFFSET = 48;
    private static final int OCEAN_FALLBACK_SEARCH_STEP = 64;
    private static final int OCEAN_REGION_RADIUS = 112;
    private static final int OCEAN_REGION_STEP = 16;
    private static final int FOOTPRINT_SAMPLE_STEP = 4;
    private static final int MIN_OCEAN_WATER_DEPTH = 8;
    private static final int MAX_SEAFLOOR_VARIANCE = 18;
    private static final int ISLAND_PLATFORM_PADDING = 10;
    private static final int ISLAND_SHORE_RADIUS_PADDING = 24;
    private static final int ISLAND_SLOPE_DEPTH = 7;
    private static final Set<ResourceKey<Biome>> BLACKLISTED_OCEAN_BIOMES = Set.of(
            Biomes.RIVER,
            Biomes.FROZEN_RIVER,
            Biomes.SWAMP,
            Biomes.MANGROVE_SWAMP);
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
        Vec3i bunkerSize = BUNKER_PLACER.peekSize(level);
        prepareStarterIsland(level, anchor, bunkerSize);
        return BUNKER_PLACER.placeAnchored(level, anchor);
    }

    private static BlockPos resolveAnchor(ServerLevel level) {
        BlockPos baseSpawn = level.getSharedSpawnPos();
        Vec3i bunkerSize = BUNKER_PLACER.peekSize(level);
        return findOceanAnchor(level, baseSpawn, bunkerSize);
    }

    private static BlockPos findOceanAnchor(ServerLevel level, BlockPos baseSpawn, Vec3i bunkerSize) {
        BlockPos anchor = toOceanAnchor(level, baseSpawn);
        if (isViableOceanAnchor(level, anchor, bunkerSize)) {
            return anchor;
        }

        int baseX = baseSpawn.getX();
        int baseZ = baseSpawn.getZ();
        int evaluated = 1;
        for (int radius = OCEAN_SEARCH_STEP; radius <= OCEAN_SEARCH_RADIUS; radius += OCEAN_SEARCH_STEP) {
            for (int dx = -radius; dx <= radius; dx += OCEAN_SEARCH_STEP) {
                for (int dz = -radius; dz <= radius; dz += OCEAN_SEARCH_STEP) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    BlockPos candidate = toOceanAnchor(level, new BlockPos(baseX + dx, level.getMinBuildHeight(), baseZ + dz));
                    if (isViableOceanAnchor(level, candidate, bunkerSize)) {
                        return candidate;
                    }
                    evaluated++;
                    if (evaluated >= MAX_ANCHOR_CANDIDATES) {
                        BlockPos forcedOceanAnchor = findAnyOceanAnchor(level, baseSpawn);
                        ModConstants.LOGGER.warn(
                                "Hit spawn bunker ocean-search budget ({} candidates) near {}; using fallback {}.",
                                MAX_ANCHOR_CANDIDATES, baseSpawn, forcedOceanAnchor);
                        return forcedOceanAnchor;
                    }
                }
            }
        }

        BlockPos forcedOceanAnchor = findAnyOceanAnchor(level, baseSpawn);
        ModConstants.LOGGER.warn("Unable to locate a fully open ocean spawn region within {} blocks of {}; using best-effort ocean fallback.",
                OCEAN_SEARCH_RADIUS, baseSpawn);
        return forcedOceanAnchor;
    }

    private static BlockPos toOceanAnchor(ServerLevel level, BlockPos pos) {
        int anchorY = Math.min(level.getMaxBuildHeight() - 1, level.getSeaLevel() + 2);
        return new BlockPos(pos.getX(), anchorY, pos.getZ());
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

    private static boolean isViableOceanAnchor(ServerLevel level, BlockPos anchor, Vec3i bunkerSize) {
        if (!fastIsProbablyOceanAnchor(level, anchor)) {
            return false;
        }
        if (!isOceanBiome(level, anchor)) {
            return false;
        }
        if (!hasOceanFootprint(level, anchor, bunkerSize)) {
            return false;
        }
        if (!isSurroundedByOcean(level, anchor)) {
            return false;
        }
        return hasStableSeafloor(level, anchor, bunkerSize);
    }

    private static boolean fastIsProbablyOceanAnchor(ServerLevel level, BlockPos anchor) {
        if (!isOceanWaterSample(level, anchor)) {
            return false;
        }
        return isOceanWaterSample(level, anchor.offset(FAST_OCEAN_SAMPLE_OFFSET, 0, 0))
                && isOceanWaterSample(level, anchor.offset(-FAST_OCEAN_SAMPLE_OFFSET, 0, 0))
                && isOceanWaterSample(level, anchor.offset(0, 0, FAST_OCEAN_SAMPLE_OFFSET))
                && isOceanWaterSample(level, anchor.offset(0, 0, -FAST_OCEAN_SAMPLE_OFFSET));
    }

    private static boolean isOceanWaterSample(ServerLevel level, BlockPos pos) {
        if (!isOceanBiome(level, pos)) {
            return false;
        }
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, pos);
        return !level.getFluidState(surface).isEmpty();
    }

    private static BlockPos findAnyOceanAnchor(ServerLevel level, BlockPos baseSpawn) {
        BlockPos baseAnchor = toOceanAnchor(level, baseSpawn);
        if (isOceanWaterSample(level, baseAnchor)) {
            return baseAnchor;
        }

        int baseX = baseSpawn.getX();
        int baseZ = baseSpawn.getZ();
        for (int radius = OCEAN_FALLBACK_SEARCH_STEP; radius <= OCEAN_SEARCH_RADIUS; radius += OCEAN_FALLBACK_SEARCH_STEP) {
            for (int dx = -radius; dx <= radius; dx += OCEAN_FALLBACK_SEARCH_STEP) {
                for (int dz = -radius; dz <= radius; dz += OCEAN_FALLBACK_SEARCH_STEP) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    BlockPos candidate = toOceanAnchor(level, new BlockPos(baseX + dx, level.getMinBuildHeight(), baseZ + dz));
                    if (isOceanWaterSample(level, candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return baseAnchor;
    }

    private static boolean isOceanBiome(ServerLevel level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        if (!biome.is(BiomeTags.IS_OCEAN)) {
            return false;
        }
        for (ResourceKey<Biome> key : BLACKLISTED_OCEAN_BIOMES) {
            if (biome.is(key)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasOceanFootprint(ServerLevel level, BlockPos anchor, Vec3i bunkerSize) {
        BlockPos origin = getPlacementOrigin(level, anchor);
        int minX = origin.getX();
        int minZ = origin.getZ();
        int maxX = minX + Math.max(1, bunkerSize.getX()) - 1;
        int maxZ = minZ + Math.max(1, bunkerSize.getZ()) - 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x = nextSampleCoordinate(x, maxX, FOOTPRINT_SAMPLE_STEP)) {
            for (int z = minZ; z <= maxZ; z = nextSampleCoordinate(z, maxZ, FOOTPRINT_SAMPLE_STEP)) {
                cursor.set(x, anchor.getY(), z);
                if (!isOceanBiome(level, cursor)) {
                    return false;
                }

                BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, cursor);
                if (level.getFluidState(surface).isEmpty()) {
                    return false;
                }

                int seafloorY = TerrainReplacerEngine.sampleSurface(level, cursor).y();
                int waterDepth = surface.getY() - seafloorY;
                if (waterDepth < MIN_OCEAN_WATER_DEPTH) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isSurroundedByOcean(ServerLevel level, BlockPos anchor) {
        int baseX = anchor.getX();
        int baseZ = anchor.getZ();
        for (int dx = -OCEAN_REGION_RADIUS; dx <= OCEAN_REGION_RADIUS; dx += OCEAN_REGION_STEP) {
            for (int dz = -OCEAN_REGION_RADIUS; dz <= OCEAN_REGION_RADIUS; dz += OCEAN_REGION_STEP) {
                BlockPos samplePos = new BlockPos(baseX + dx, anchor.getY(), baseZ + dz);
                if (!isOceanBiome(level, samplePos)) {
                    return false;
                }

                BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, samplePos);
                if (level.getFluidState(surface).isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasStableSeafloor(ServerLevel level, BlockPos anchor, Vec3i bunkerSize) {
        BlockPos origin = getPlacementOrigin(level, anchor);
        int minX = origin.getX() - ISLAND_PLATFORM_PADDING;
        int minZ = origin.getZ() - ISLAND_PLATFORM_PADDING;
        int maxX = origin.getX() + Math.max(1, bunkerSize.getX()) - 1 + ISLAND_PLATFORM_PADDING;
        int maxZ = origin.getZ() + Math.max(1, bunkerSize.getZ()) - 1 + ISLAND_PLATFORM_PADDING;

        int minFloorY = Integer.MAX_VALUE;
        int maxFloorY = Integer.MIN_VALUE;
        for (int x = minX; x <= maxX; x = nextSampleCoordinate(x, maxX, FOOTPRINT_SAMPLE_STEP)) {
            for (int z = minZ; z <= maxZ; z = nextSampleCoordinate(z, maxZ, FOOTPRINT_SAMPLE_STEP)) {
                int floorY = TerrainReplacerEngine.sampleSurface(level, new BlockPos(x, anchor.getY(), z)).y();
                minFloorY = Math.min(minFloorY, floorY);
                maxFloorY = Math.max(maxFloorY, floorY);
                if (maxFloorY - minFloorY > MAX_SEAFLOOR_VARIANCE) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void prepareStarterIsland(ServerLevel level, BlockPos anchor, Vec3i bunkerSize) {
        BlockPos origin = getPlacementOrigin(level, anchor);
        int sizeX = Math.max(1, bunkerSize.getX());
        int sizeZ = Math.max(1, bunkerSize.getZ());
        int centerX = origin.getX() + (sizeX / 2);
        int centerZ = origin.getZ() + (sizeZ / 2);
        int flatRadius = Math.max(sizeX, sizeZ) / 2 + ISLAND_PLATFORM_PADDING;
        int shoreRadius = flatRadius + ISLAND_SHORE_RADIUS_PADDING;
        int islandTopY = anchor.getY() - 1;
        int seaLevel = level.getSeaLevel();

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = centerX - shoreRadius; x <= centerX + shoreRadius; x++) {
            for (int z = centerZ - shoreRadius; z <= centerZ + shoreRadius; z++) {
                double dx = x - centerX;
                double dz = z - centerZ;
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > shoreRadius) {
                    continue;
                }

                int seafloorY = TerrainReplacerEngine.sampleSurface(level, new BlockPos(x, anchor.getY(), z)).y();
                int targetTopY = resolveIslandTopY(distance, flatRadius, shoreRadius, islandTopY, seaLevel);
                if (targetTopY <= seafloorY) {
                    targetTopY = Math.min(islandTopY, seafloorY + 1);
                }
                if (targetTopY <= seafloorY) {
                    continue;
                }

                for (int y = seafloorY; y <= targetTopY; y++) {
                    cursor.set(x, y, z);
                    level.setBlock(cursor, selectIslandBlock(distance, flatRadius, shoreRadius, targetTopY, y, seaLevel), 2);
                }

                if (targetTopY >= seaLevel) {
                    for (int y = targetTopY + 1; y <= seaLevel; y++) {
                        cursor.set(x, y, z);
                        if (!level.getBlockState(cursor).isAir() || !level.getFluidState(cursor).isEmpty()) {
                            level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
    }

    private static int resolveIslandTopY(double distance,
                                         int flatRadius,
                                         int shoreRadius,
                                         int islandTopY,
                                         int seaLevel) {
        if (distance <= flatRadius) {
            return islandTopY;
        }

        double slopeProgress = (distance - flatRadius) / Math.max(1.0D, shoreRadius - flatRadius);
        int drop = (int) Math.round(slopeProgress * ISLAND_SLOPE_DEPTH);
        return Math.max(seaLevel - 2, islandTopY - drop);
    }

    private static BlockState selectIslandBlock(double distance,
                                                int flatRadius,
                                                int shoreRadius,
                                                int targetTopY,
                                                int y,
                                                int seaLevel) {
        if (y == targetTopY) {
            if (targetTopY <= seaLevel || distance >= shoreRadius - 4) {
                return Blocks.SAND.defaultBlockState();
            }
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }

        if (y >= targetTopY - 3) {
            if (distance >= flatRadius || targetTopY <= seaLevel) {
                return Blocks.SANDSTONE.defaultBlockState();
            }
            return Blocks.DIRT.defaultBlockState();
        }

        return Blocks.STONE.defaultBlockState();
    }

    private static BlockPos getPlacementOrigin(ServerLevel level, BlockPos surface) {
        BlockPos levelingOffset = BUNKER_PLACER.peekLevelingOffset(level);
        return levelingOffset == null ? surface : surface.subtract(levelingOffset);
    }

    private static int nextSampleCoordinate(int current, int max, int step) {
        if (current >= max) {
            return max + 1;
        }

        int next = current + step;
        return Math.min(next, max);
    }
}
