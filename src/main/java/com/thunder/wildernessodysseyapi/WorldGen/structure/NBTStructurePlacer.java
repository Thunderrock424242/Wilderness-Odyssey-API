package com.thunder.wildernessodysseyapi.WorldGen.structure;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.configurable.StructureConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Places vanilla structure templates loaded from NBT files and exposes metadata used by the mod.
 */
public class NBTStructurePlacer {
    private static final String TERRAIN_REPLACER_NAME = "wildernessodysseyapi:terrain_replacer";
    private static final String CRYO_TUBE_NAME = "wildernessodysseyapi:cryo_tube";
    private static final String LEVELING_MARKER_NAME =
            BuiltInRegistries.BLOCK.getKey(Blocks.BLUE_WOOL).toString();

    private final ResourceLocation id;
    private TemplateData cachedData;

    public NBTStructurePlacer(String namespace, String path) {
        this(ResourceLocation.tryBuild(namespace, path));
    }

    public NBTStructurePlacer(ResourceLocation id) {
        this.id = id;
    }

    /**
     * Places the configured structure template at the given origin.
     *
     * @param level  the level to place into
     * @param origin the placement origin
     * @return placement result containing bounds and cryo tube positions, or {@code null} on failure
     */
    public PlacementResult place(ServerLevel level, BlockPos origin) {
        TemplateData data = load(level);
        if (data == null) {
            return null;
        }

        if (!data.hasStructureBlocks()) {
            ModConstants.LOGGER.warn("Skipping placement for {} because the template contains no structure blocks.", id);
            return null;
        }

        BlockPos placementOrigin = origin;
        BlockState levelingReplacement = null;
        BlockPos levelingOffset = data.levelingOffset();
        if (levelingOffset != null) {
            int x = origin.getX() + levelingOffset.getX();
            int z = origin.getZ() + levelingOffset.getZ();
            SurfaceSample sample = wildernessodysseyapi$findSurface(level, x, z);
            int desiredY = sample.y() - levelingOffset.getY();
            int maxDepth = StructureConfig.MAX_LEVELING_DEPTH.get();
            if (maxDepth >= 0) {
                int clampedY = Math.max(desiredY, sample.y() - maxDepth);
                if (clampedY != desiredY) {
                    ModConstants.LOGGER.warn("Clamping leveling depth for structure {}. Desired bury depth {} exceeds limit {} at marker {}.",
                            id, sample.y() - desiredY, maxDepth, levelingOffset);
                    desiredY = clampedY;
                }
            }

            placementOrigin = new BlockPos(origin.getX(), desiredY, origin.getZ());
            levelingReplacement = sample.state();
        }

        LargeStructurePlacementOptimizer.preparePlacement(level, placementOrigin, data.size());
        if (LargeStructurePlacementOptimizer.exceedsStructureBlockLimit(data.size())) {
            int estimated = LargeStructurePlacementOptimizer.estimateAffectedBlocks(data.size());
            ModConstants.LOGGER.warn("Placing structure {} will touch approximately {} blocks, exceeding the recommended limit of {}.",
                    id, estimated, StructureUtils.STRUCTURE_BLOCK_LIMIT);
        }

        boolean terrainReplacerEnabledInConfig = StructureConfig.ENABLE_TERRAIN_REPLACER.get();
        boolean enableTerrainReplacer = terrainReplacerEnabledInConfig
                && !data.disableTerrainReplacement()
                && data.hasMarkerWool();
        List<BlockPos> terrainOffsets = enableTerrainReplacer ? data.terrainOffsets() : List.of();

        List<BlockState> sampledTerrain = new ArrayList<>(terrainOffsets.size());
        if (enableTerrainReplacer) {
            for (BlockPos offset : terrainOffsets) {
                BlockPos worldPos = placementOrigin.offset(offset);
                BlockState state = level.getBlockState(worldPos);
                if (state.isAir()) {
                    state = level.getBlockState(worldPos.below());
                }
                sampledTerrain.add(state);
            }
        } else if (!data.terrainOffsets().isEmpty()) {
            if (!terrainReplacerEnabledInConfig) {
                ModConstants.LOGGER.info("Terrain replacer markers are present in {} but replacement is disabled via config.", id);
            } else if (!data.hasMarkerWool()) {
                ModConstants.LOGGER.info("Terrain replacer markers are present in {} but no wool markers were found; skipping replacement.", id);
            } else {
                ModConstants.LOGGER.info("Terrain replacer markers are present in {} but replacement is disabled due to template safety checks.", id);
            }
        }

        StructurePlaceSettings settings = new StructurePlaceSettings();
        boolean placed = data.template().placeInWorld(level, placementOrigin, placementOrigin, settings, level.random, 2);
        if (!placed) {
            return null;
        }

        for (int i = 0; i < terrainOffsets.size(); i++) {
            BlockPos worldPos = placementOrigin.offset(terrainOffsets.get(i));
            BlockState replacement = sampledTerrain.get(i);
            level.setBlock(worldPos, replacement, 2);
        }

        if (levelingOffset != null && levelingReplacement != null) {
            BlockPos markerWorldPos = placementOrigin.offset(levelingOffset);
            level.setBlock(markerWorldPos, levelingReplacement, 2);
        }

        Vec3i size = data.size();
        AABB bounds = LargeStructurePlacementOptimizer.createBounds(placementOrigin, size);
        List<AABB> chunkSlices = LargeStructurePlacementOptimizer.computeChunkSlices(placementOrigin, size);

        List<BlockPos> cryoPositions = data.cryoOffsets().stream()
                .map(placementOrigin::offset)
                .toList();

        return new PlacementResult(bounds, cryoPositions, List.copyOf(chunkSlices));
    }

    /**
     * Returns the relative cryo tube offsets defined by the structure.
     */
    public List<BlockPos> getCryoOffsets(ServerLevel level) {
        TemplateData data = load(level);
        return data == null ? List.of() : data.cryoOffsets();
    }

    private TemplateData load(ServerLevel level) {
        if (cachedData != null) {
            return cachedData;
        }


        StructureTemplateManager manager = level.getStructureManager();
        StructureTemplate template;
        Optional<StructureTemplate> existing = manager.get(id);
        if (existing.isPresent()) {
            template = existing.get();
        } else {
            template = manager.getOrCreate(id);
        }

        List<BlockPos> cryoOffsets = new ArrayList<>();
        List<BlockPos> terrainOffsets = new ArrayList<>();
        BlockPos levelingOffset = null;
        Vec3i size = template.getSize();

        CollectionResult collectionResult = collectOffsets(template, cryoOffsets, terrainOffsets, size);
        boolean disableTerrainReplacement = collectionResult.disableTerrainReplacement();
        boolean hasStructureBlocks = collectionResult.hasStructureBlocks();
        LevelingMarkerData levelingData = findLevelingMarker(template);
        levelingOffset = levelingData.offset();

        TemplateData data = new TemplateData(template, List.copyOf(cryoOffsets), List.copyOf(terrainOffsets), size,
                levelingOffset, disableTerrainReplacement, levelingData.present(), hasStructureBlocks);
        cachedData = data;
        return data;
    }

    /**
     * Result of placing a structure, exposing the overall bounds, any detected cryo tubes and
     * chunk-aligned slices that callers can use for follow-up processing.
     */
    public record PlacementResult(AABB bounds, List<BlockPos> cryoPositions, List<AABB> chunkSlices) {}

    private record TemplateData(StructureTemplate template,
                                List<BlockPos> cryoOffsets,
                                List<BlockPos> terrainOffsets,
                                Vec3i size,
                                BlockPos levelingOffset,
                                boolean disableTerrainReplacement,
                                boolean hasMarkerWool,
                                boolean hasStructureBlocks) {}

    private record CollectionResult(boolean disableTerrainReplacement, boolean hasStructureBlocks) {}

    private record LevelingMarkerData(BlockPos offset, boolean present) {}

    private record SurfaceSample(int y, BlockState state) {}

    private CollectionResult collectOffsets(StructureTemplate template, List<BlockPos> cryoOffsets, List<BlockPos> terrainOffsets,
                                            Vec3i size) {
        StructurePlaceSettings identitySettings = new StructurePlaceSettings();

        boolean disableTerrainReplacement = false;

        Block cryoTube = resolveBlock(CRYO_TUBE_NAME, "cryo tube");
        if (cryoTube != Blocks.AIR) {
            for (StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, identitySettings, cryoTube)) {
                cryoOffsets.add(info.pos());
            }
        }

        Block terrainReplacer = resolveBlock(TERRAIN_REPLACER_NAME, "terrain replacer");
        if (terrainReplacer != Blocks.AIR) {
            for (StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, identitySettings, terrainReplacer)) {
                terrainOffsets.add(info.pos());
            }

            disableTerrainReplacement = warnIfTerrainReplacerDominates(size, terrainOffsets.size());
        }

        boolean hasStructureBlocks = size.getX() > 0 && size.getY() > 0 && size.getZ() > 0;

        return new CollectionResult(disableTerrainReplacement, hasStructureBlocks);
    }

    private boolean warnIfTerrainReplacerDominates(Vec3i size, int terrainCount) {
        if (terrainCount <= 0) {
            return false;
        }

        double volume = (double) size.getX() * size.getY() * size.getZ();
        if (volume <= 0) {
            return false;
        }

        double ratio = terrainCount / volume;
        double warningThreshold = StructureConfig.TERRAIN_REPLACER_WARNING_THRESHOLD.get();
        if (ratio >= warningThreshold && warningThreshold > 0) {
            double percent = Math.round(ratio * 10000.0D) / 100.0D;
            ModConstants.LOGGER.warn(
                    "Structure {} uses terrain replacer markers for {}% of its volume ({} of ~{} blocks).",
                    id, percent, terrainCount, (int) volume);
            ModConstants.LOGGER.warn(
                    "Skipping terrain replacement for {} to avoid filling the template with sampled ground blocks.", id);
            return true;
        }

        return false;
    }

    private LevelingMarkerData findLevelingMarker(StructureTemplate template) {
        // Multiple wool markers can exist in a template; prefer the deepest one to better match terrain when
        // burying the structure, and fall back to the closest-to-center marker to avoid edge anchors.
        StructurePlaceSettings identitySettings = new StructurePlaceSettings();
        List<StructureBlockInfo> markers = template.filterBlocks(BlockPos.ZERO, identitySettings, Blocks.BLUE_WOOL);
        if (markers.isEmpty()) {
            return new LevelingMarkerData(null, false);
        }

        Vec3i size = template.getSize();
        double centerX = size.getX() / 2.0D;
        double centerZ = size.getZ() / 2.0D;

        return markers.stream()
                .min((a, b) -> {
                    int yCompare = Integer.compare(a.pos().getY(), b.pos().getY());
                    if (yCompare != 0) {
                        return yCompare; // prefer markers lower in the template for ground embedding
                    }

                    double aDx = a.pos().getX() - centerX;
                    double aDz = a.pos().getZ() - centerZ;
                    double bDx = b.pos().getX() - centerX;
                    double bDz = b.pos().getZ() - centerZ;
                    double aDist = (aDx * aDx) + (aDz * aDz);
                    double bDist = (bDx * bDx) + (bDz * bDz);
                    return Double.compare(aDist, bDist);
                })
                .map(StructureBlockInfo::pos)
                .map(pos -> new LevelingMarkerData(pos, true))
                .orElse(new LevelingMarkerData(null, true));
    }

    private Block resolveBlock(String name, String description) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null) {
            ModConstants.LOGGER.warn("Skipping {} lookup due to invalid id: {}", description, name);
            return Blocks.AIR;
        }

        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block == Blocks.AIR && !BuiltInRegistries.BLOCK.containsKey(id)) {
            ModConstants.LOGGER.warn("Unable to locate {} block {} in registry; markers will be ignored.", description, id);
        }

        return block;
    }

    private SurfaceSample wildernessodysseyapi$findSurface(ServerLevel level, int x, int z) {
        int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        int minY = level.getMinBuildHeight();
        int maxY = Math.min(topY, level.getMaxBuildHeight() - 1);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, maxY, z);

        for (int y = maxY; y >= minY; y--) {
            cursor.setY(y);
            BlockState state = level.getBlockState(cursor);
            if (!state.isAir()) {
                return new SurfaceSample(y, state);
            }
        }

        return new SurfaceSample(topY, Blocks.AIR.defaultBlockState());
    }
}
