package com.thunder.wildernessodysseyapi.WorldGen.structure;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.io.InputStream;
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

    private final ResourceLocation id;
    private final ResourceLocation structurePath;
    private TemplateData cachedData;

    public NBTStructurePlacer(String namespace, String path) {
        this(ResourceLocation.tryBuild(namespace, path));
    }

    public NBTStructurePlacer(ResourceLocation id) {
        this.id = id;
        this.structurePath = ResourceLocation.tryBuild(id.getNamespace(), "structures/" + id.getPath() + ".nbt");
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

        LargeStructurePlacementOptimizer.preparePlacement(level, origin, data.size());
        if (LargeStructurePlacementOptimizer.exceedsStructureBlockLimit(data.size())) {
            int estimated = LargeStructurePlacementOptimizer.estimateAffectedBlocks(data.size());
            ModConstants.LOGGER.warn("Placing structure {} will touch approximately {} blocks, exceeding the recommended limit of {}.",
                    id, estimated, StructureUtils.STRUCTURE_BLOCK_LIMIT);
        }

        List<BlockState> sampledTerrain = new ArrayList<>(data.terrainOffsets().size());
        for (BlockPos offset : data.terrainOffsets()) {
            BlockPos worldPos = origin.offset(offset);
            BlockState state = level.getBlockState(worldPos);
            if (state.isAir()) {
                state = level.getBlockState(worldPos.below());
            }
            sampledTerrain.add(state);
        }

        StructurePlaceSettings settings = new StructurePlaceSettings();
        boolean placed = data.template().placeInWorld(level, origin, origin, settings, level.random, 2);
        if (!placed) {
            return null;
        }

        for (int i = 0; i < data.terrainOffsets().size(); i++) {
            BlockPos worldPos = origin.offset(data.terrainOffsets().get(i));
            BlockState replacement = sampledTerrain.get(i);
            level.setBlock(worldPos, replacement, 2);
        }

        Vec3i size = data.size();
        AABB bounds = LargeStructurePlacementOptimizer.createBounds(origin, size);
        List<AABB> chunkSlices = LargeStructurePlacementOptimizer.computeChunkSlices(origin, size);

        List<BlockPos> cryoPositions = data.cryoOffsets().stream()
                .map(origin::offset)
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
        Vec3i size = template.getSize();

        ResourceManager resourceManager = level.getServer().getResourceManager();
        Optional<Resource> resourceOpt = resourceManager.getResource(structurePath);
        if (resourceOpt.isEmpty()) {
            ModConstants.LOGGER.error("Structure resource not found: {}", structurePath);
            return null;
        }

        Resource resource = resourceOpt.get();
        try (InputStream in = resource.open()) {
            CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
            List<String> paletteNames = new ArrayList<>(palette.size());
            for (int i = 0; i < palette.size(); i++) {
                CompoundTag entry = palette.getCompound(i);
                paletteNames.add(entry.getString("Name"));
            }

            ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < blocks.size(); i++) {
                CompoundTag block = blocks.getCompound(i);
                ListTag posTag = block.getList("pos", Tag.TAG_INT);
                BlockPos offset = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
                int state = block.getInt("state");
                String blockName = state >= 0 && state < paletteNames.size() ? paletteNames.get(state) : "";
                if (CRYO_TUBE_NAME.equals(blockName)) {
                    cryoOffsets.add(offset);
                } else if (TERRAIN_REPLACER_NAME.equals(blockName)) {
                    terrainOffsets.add(offset);
                }
            }
        } catch (IOException e) {
            ModConstants.LOGGER.error("Failed to parse structure template {}", structurePath, e);
            return null;
        }

        TemplateData data = new TemplateData(template, List.copyOf(cryoOffsets), List.copyOf(terrainOffsets), size);
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
                                Vec3i size) {}
}
