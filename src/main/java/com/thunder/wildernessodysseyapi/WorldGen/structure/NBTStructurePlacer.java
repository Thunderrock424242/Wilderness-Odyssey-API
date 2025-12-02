package com.thunder.wildernessodysseyapi.WorldGen.structure;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
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

import com.thunder.wildernessodysseyapi.util.NbtParsingUtils;

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

        BlockPos placementOrigin = origin;
        BlockState levelingReplacement = null;
        BlockPos levelingOffset = data.levelingOffset();
        if (levelingOffset != null) {
            int x = origin.getX() + levelingOffset.getX();
            int z = origin.getZ() + levelingOffset.getZ();
            SurfaceSample sample = wildernessodysseyapi$findSurface(level, x, z);
            placementOrigin = new BlockPos(origin.getX(), sample.y() - levelingOffset.getY(), origin.getZ());
            levelingReplacement = sample.state();
        }

        LargeStructurePlacementOptimizer.preparePlacement(level, placementOrigin, data.size());
        if (LargeStructurePlacementOptimizer.exceedsStructureBlockLimit(data.size())) {
            int estimated = LargeStructurePlacementOptimizer.estimateAffectedBlocks(data.size());
            ModConstants.LOGGER.warn("Placing structure {} will touch approximately {} blocks, exceeding the recommended limit of {}.",
                    id, estimated, StructureUtils.STRUCTURE_BLOCK_LIMIT);
        }

        List<BlockState> sampledTerrain = new ArrayList<>(data.terrainOffsets().size());
        for (BlockPos offset : data.terrainOffsets()) {
            BlockPos worldPos = placementOrigin.offset(offset);
            BlockState state = level.getBlockState(worldPos);
            if (state.isAir()) {
                state = level.getBlockState(worldPos.below());
            }
            sampledTerrain.add(state);
        }

        StructurePlaceSettings settings = new StructurePlaceSettings();
        boolean placed = data.template().placeInWorld(level, placementOrigin, placementOrigin, settings, level.random, 2);
        if (!placed) {
            return null;
        }

        for (int i = 0; i < data.terrainOffsets().size(); i++) {
            BlockPos worldPos = placementOrigin.offset(data.terrainOffsets().get(i));
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
                .collect(Collectors.toUnmodifiableList());

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

        NbtParsingUtils.extendNbtParseTimeout();

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

        collectOffsets(template, cryoOffsets, terrainOffsets);
        levelingOffset = findLevelingOffset(template);

        TemplateData data = new TemplateData(template, List.copyOf(cryoOffsets), List.copyOf(terrainOffsets), size,
                levelingOffset);
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
                                BlockPos levelingOffset) {}

    private record SurfaceSample(int y, BlockState state) {}

    private void collectOffsets(StructureTemplate template, List<BlockPos> cryoOffsets, List<BlockPos> terrainOffsets) {
        StructurePlaceSettings identitySettings = new StructurePlaceSettings();

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
        }
    }

    private BlockPos findLevelingOffset(StructureTemplate template) {
        StructurePlaceSettings identitySettings = new StructurePlaceSettings();
        List<StructureBlockInfo> markers = template.filterBlocks(BlockPos.ZERO, identitySettings, Blocks.BLUE_WOOL);
        if (markers.isEmpty()) {
            return null;
        }

        return markers.get(0).pos();
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
