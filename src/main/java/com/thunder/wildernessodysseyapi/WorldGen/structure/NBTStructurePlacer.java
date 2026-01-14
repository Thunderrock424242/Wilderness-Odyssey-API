package com.thunder.wildernessodysseyapi.WorldGen.structure;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.configurable.StructureConfig;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StructurePlacementDebugger.PlacementAttempt;
import com.thunder.wildernessodysseyapi.WorldGen.structure.TerrainReplacerEngine.SurfaceSample;
import com.thunder.wildernessodysseyapi.mixin.StructureTemplateAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.phys.AABB;
import net.minecraft.tags.BlockTags;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.BitSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.neoforged.fml.ModList;

/**
 * Places vanilla structure templates loaded from NBT files and exposes metadata used by the mod.
 */
public class NBTStructurePlacer {
    private static final String CRYO_TUBE_NAME = "wildernessodysseyapi:cryo_tube";
    private static final String CREATE_ELEVATOR_PULLEY_NAME = "create:elevator_pulley";
    private static final String LEVELING_MARKER_NAME =
            BuiltInRegistries.BLOCK.getKey(Blocks.BLUE_WOOL).toString();
    private static final int MIN_LEVELING_MARKER_Y = 62;
    private static final int MAX_LEVELING_MARKER_Y = 65;
    private static final String[] PALETTE_BLOCK_FIELD_NAMES = {"blocks", "blockInfos", "blockInfoList"};
    private static boolean loggedPaletteFieldWarning = false;

    private final ResourceLocation id;
    private final List<StructureProcessor> extraProcessors;
    private TemplateData cachedData;

    public NBTStructurePlacer(String namespace, String path) {
        this(ResourceLocation.tryBuild(namespace, path));
    }

    public NBTStructurePlacer(ResourceLocation id) {
        this(id, List.of());
    }

    public NBTStructurePlacer(ResourceLocation id, List<StructureProcessor> extraProcessors) {
        this.id = id;
        this.extraProcessors = List.copyOf(extraProcessors);
    }

    /**
     * Places the configured structure template at the given origin.
     *
     * @param level  the level to place into
     * @param origin the placement origin
     * @return placement result containing bounds and cryo tube positions, or {@code null} on failure
     */
    public PlacementResult place(ServerLevel level, BlockPos origin) {
        return place(level, origin, null);
    }

    /**
     * Places the structure so that the leveling marker (or template origin if no marker exists)
     * lines up with the provided {@code anchor} position.
     */
    public PlacementResult placeAnchored(ServerLevel level, BlockPos anchor) {
        return placeAnchored(level, anchor, null);
    }

    /**
     * Places the structure and reports progress to the provided debug attempt when available,
     * aligning the template's anchor with the supplied world position.
     */
    public PlacementResult placeAnchored(ServerLevel level, BlockPos anchor, PlacementAttempt debugAttempt) {
        TemplateData data = load(level);
        if (data == null) {
            StructurePlacementDebugger.markFailure(debugAttempt, "template missing");
            return null;
        }

        BlockPos origin = anchor;
        if (data.levelingOffset() != null) {
            origin = anchor.subtract(data.levelingOffset());
        }

        PlacementAttempt attempt = debugAttempt != null
                ? debugAttempt
                : StructurePlacementDebugger.startAttempt(level, id, data.size(), origin);

        if (!data.hasStructureBlocks()) {
            StructurePlacementDebugger.markFailure(attempt, "template is empty");
            ModConstants.LOGGER.warn("Skipping placement for {} because the template contains no structure blocks.", id);
            return null;
        }

        PlacementFoundation foundation = resolvePlacementOriginAnchored(level, origin, anchor, data.levelingOffset());
        if (foundation == null) {
            StructurePlacementDebugger.markFailure(attempt, "unable to find terrain anchor");
            return null;
        }

        return placeWithFoundation(level, data, foundation, attempt);
    }

    /**
     * Places the structure and reports progress to the provided debug attempt when available.
     */
    public PlacementResult place(ServerLevel level, BlockPos origin, PlacementAttempt debugAttempt) {
        TemplateData data = load(level);
        if (data == null) {
            StructurePlacementDebugger.markFailure(debugAttempt, "template missing");
            return null;
        }

        PlacementAttempt attempt = debugAttempt != null
                ? debugAttempt
                : StructurePlacementDebugger.startAttempt(level, id, data.size(), origin);

        if (!data.hasStructureBlocks()) {
            StructurePlacementDebugger.markFailure(attempt, "template is empty");
            ModConstants.LOGGER.warn("Skipping placement for {} because the template contains no structure blocks.", id);
            return null;
        }

        PlacementFoundation foundation = resolvePlacementOrigin(level, origin, data.levelingOffset());
        if (foundation == null) {
            StructurePlacementDebugger.markFailure(attempt, "unable to find terrain anchor");
            return null;
        }

        return placeWithFoundation(level, data, foundation, attempt);
    }

    private PlacementResult placeWithFoundation(ServerLevel level,
                                                TemplateData data,
                                                PlacementFoundation foundation,
                                                PlacementAttempt attempt) {
        LargeStructurePlacementOptimizer.preparePlacement(level, foundation.origin(), data.size());
        if (LargeStructurePlacementOptimizer.exceedsStructureBlockLimit(data.size())) {
            int estimated = LargeStructurePlacementOptimizer.estimateAffectedBlocks(data.size());
            ModConstants.LOGGER.warn("Placing structure {} will touch approximately {} blocks, exceeding the recommended limit of {}.",
                    id, estimated, StructureUtils.STRUCTURE_BLOCK_LIMIT);
        }

        BoundingBox placementBox = expandPlacementBox(foundation.origin(), data.size(), data.template());
        StructurePlaceSettings settings = new StructurePlaceSettings()
                // We already know the template dimensions, so skip the expensive shape discovery pass.
                .setKnownShape(true)
                // Supplying the bounds up-front prevents the vanilla placer from bailing out when it cannot
                // infer them (which resulted in the template refusing to place while terrain markers still
                // ran).
                .setBoundingBox(placementBox)
                // Preserve entities baked into the template (e.g., Create super glue and contraptions) so
                // complex machines such as elevators remain assembled after placement.
                .setIgnoreEntities(false);
        for (StructureProcessor processor : extraProcessors) {
            settings.addProcessor(processor);
        }
        boolean placed = data.template().placeInWorld(level, foundation.origin(), foundation.origin(), settings, level.random, 2);
        if (!placed) {
            StructurePlacementDebugger.markFailure(attempt, "template refused placement");
            return null;
        }

        if (isStarterBunker()) {
            clearTerrainInsideStructure(level, foundation.origin(), data.size(), data.template());
        }

        int autoBlended = 0;
        if (StructureConfig.ENABLE_AUTO_TERRAIN_BLEND.get()) {
            int maxDepth = StructureConfig.AUTO_TERRAIN_BLEND_MAX_DEPTH.get();
            int radius = resolveAutoBlendRadius(data.size());
            TerrainReplacerEngine.AutoBlendMask mask = TerrainReplacerEngine.AutoBlendMask.allowAll();
            if (StructureConfig.ENABLE_SMART_AUTO_TERRAIN_BLEND.get()) {
                mask = buildAutoBlendMask(data.template(), foundation.origin(), data.size());
            }
            autoBlended = TerrainReplacerEngine.applyAutoBlend(level, placementBox, maxDepth, radius, mask);
        }

        if (data.levelingOffset() != null && foundation.levelingReplacement() != null) {
            BlockPos markerWorldPos = foundation.origin().offset(data.levelingOffset());
            BlockState markerReplacement = normalizeLevelingReplacement(foundation.levelingReplacement());
            level.setBlock(markerWorldPos, markerReplacement, 2);
        }

        Vec3i size = data.size();
        AABB bounds = LargeStructurePlacementOptimizer.createBounds(foundation.origin(), size);
        List<AABB> chunkSlices = LargeStructurePlacementOptimizer.computeChunkSlices(foundation.origin(), size);

        activateCreateElevators(level, foundation.origin(), data.elevatorPulleyOffsets());

        List<BlockPos> cryoPositions = data.cryoOffsets().stream()
                .map(foundation.origin()::offset)
                .toList();

        StructurePlacementDebugger.markSuccess(attempt,
                "placed with %s auto-blended blocks and %s cryo tubes"
                        .formatted(autoBlended, cryoPositions.size()));

        return new PlacementResult(foundation.origin(), bounds, cryoPositions, List.copyOf(chunkSlices));
    }

    /** Returns the template id used by this placer. */
    public ResourceLocation id() {
        return id;
    }

    /** Returns the template size if known, or {@link Vec3i#ZERO} when loading failed. */
    public Vec3i peekSize(ServerLevel level) {
        TemplateData data = load(level);
        return data == null ? Vec3i.ZERO : data.size();
    }

    /** Returns the leveling marker offset when present, or {@code null} if the template lacks one. */
    public BlockPos peekLevelingOffset(ServerLevel level) {
        TemplateData data = load(level);
        return data == null ? null : data.levelingOffset();
    }

    private PlacementFoundation resolvePlacementOrigin(ServerLevel level, BlockPos origin, BlockPos levelingOffset) {
        if (levelingOffset == null) {
            return new PlacementFoundation(origin, null);
        }

        SurfaceSample sample = TerrainReplacerEngine.sampleSurface(level, origin.offset(levelingOffset.getX(), 0, levelingOffset.getZ()));
        int surfaceAnchorY = sample.y() + 1;
        int desiredY = surfaceAnchorY - levelingOffset.getY();
        int maxDepth = StructureConfig.MAX_LEVELING_DEPTH.get();
        if (maxDepth >= 0) {
            int clampedY = Math.max(desiredY, surfaceAnchorY - maxDepth);
            if (clampedY != desiredY) {
                ModConstants.LOGGER.warn("Clamping leveling depth for structure {}. Desired bury depth {} exceeds limit {} at marker {}.",
                        id, surfaceAnchorY - desiredY, maxDepth, levelingOffset);
                desiredY = clampedY;
            }
        }

        int markerY = desiredY + levelingOffset.getY();
        if (markerY < MIN_LEVELING_MARKER_Y) {
            ModConstants.LOGGER.warn(
                    "Raising structure {} so the leveling marker sits at y={} instead of y={}.",
                    id, MIN_LEVELING_MARKER_Y, markerY);
            desiredY = MIN_LEVELING_MARKER_Y - levelingOffset.getY();
        } else if (markerY > MAX_LEVELING_MARKER_Y) {
            ModConstants.LOGGER.warn(
                    "Lowering structure {} so the leveling marker sits at y={} instead of y={}.",
                    id, MAX_LEVELING_MARKER_Y, markerY);
            desiredY = MAX_LEVELING_MARKER_Y - levelingOffset.getY();
        }

        BlockPos placementOrigin = new BlockPos(origin.getX(), desiredY, origin.getZ());
        return new PlacementFoundation(placementOrigin, sample.state());
    }

    private PlacementFoundation resolvePlacementOriginAnchored(ServerLevel level,
                                                               BlockPos origin,
                                                               BlockPos anchor,
                                                               BlockPos levelingOffset) {
        if (levelingOffset == null) {
            return new PlacementFoundation(origin, null);
        }

        SurfaceSample sample = TerrainReplacerEngine.sampleSurface(level, anchor);
        int surfaceAnchorY = sample.y() + 1;
        int desiredY = anchor.getY() - levelingOffset.getY();
        int maxDepth = StructureConfig.MAX_LEVELING_DEPTH.get();
        if (maxDepth >= 0) {
            int clampedY = Math.max(desiredY, surfaceAnchorY - maxDepth);
            if (clampedY != desiredY) {
                ModConstants.LOGGER.warn("Clamping leveling depth for structure {}. Desired bury depth {} exceeds limit {} at marker {}.",
                        id, surfaceAnchorY - desiredY, maxDepth, levelingOffset);
                desiredY = clampedY;
            }
        }

        int markerY = desiredY + levelingOffset.getY();
        if (markerY < MIN_LEVELING_MARKER_Y) {
            ModConstants.LOGGER.warn(
                    "Raising structure {} so the leveling marker sits at y={} instead of y={}.",
                    id, MIN_LEVELING_MARKER_Y, markerY);
            desiredY = MIN_LEVELING_MARKER_Y - levelingOffset.getY();
        } else if (markerY > MAX_LEVELING_MARKER_Y) {
            ModConstants.LOGGER.warn(
                    "Lowering structure {} so the leveling marker sits at y={} instead of y={}.",
                    id, MAX_LEVELING_MARKER_Y, markerY);
            desiredY = MAX_LEVELING_MARKER_Y - levelingOffset.getY();
        }

        BlockPos placementOrigin = new BlockPos(origin.getX(), desiredY, origin.getZ());
        return new PlacementFoundation(placementOrigin, sample.state());
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
        StructureTemplate template = manager.get(id).orElse(null);
        if (template == null || isTemplateEmpty(template)) {
            manager.remove(id);
            template = loadDirect(level, manager);
            if (template == null || isTemplateEmpty(template)) {
                return null;
            }
        }

        List<BlockPos> cryoOffsets = new ArrayList<>();
        List<BlockPos> elevatorPulleyOffsets = new ArrayList<>();
        BlockPos levelingOffset = null;
        Vec3i size = template.getSize();

        CollectionResult collectionResult = collectOffsets(template, cryoOffsets, elevatorPulleyOffsets, size);
        boolean hasStructureBlocks = collectionResult.hasStructureBlocks();
        LevelingMarkerData levelingData = findLevelingMarker(template);
        levelingOffset = levelingData.offset();

        TemplateData data = new TemplateData(template, List.copyOf(cryoOffsets),
                List.copyOf(elevatorPulleyOffsets), size, levelingOffset, hasStructureBlocks);

        // Do not cache empty templates so that later placement attempts can retry once resources finish loading.
        // The GameTest server asks for the bunker extremely early during startup, before data packs have been
        // stitched. Caching the placeholder zero-sized template here would permanently mark the bunker as empty.
        if (hasStructureBlocks) {
            cachedData = data;
        } else {
            cachedData = null;
            manager.remove(id);
        }

        return data;
    }

    private StructureTemplate loadDirect(ServerLevel level, StructureTemplateManager manager) {
        ResourceLocation resourcePath = ResourceLocation.fromNamespaceAndPath(
                id.getNamespace(),
                "structures/" + id.getPath() + ".nbt");
        Optional<Resource> resource = level.getServer().getResourceManager().getResource(resourcePath);
        if (resource.isEmpty()) {
            ModConstants.LOGGER.warn("Structure template {} not found at {}.", id, resourcePath);
            return null;
        }

        try (var stream = resource.get().open()) {
            CompoundTag tag = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
            return manager.readStructure(tag);
        } catch (Exception e) {
            ModConstants.LOGGER.warn("Failed to read structure template {} from resources.", id, e);
            return null;
        }
    }

    private boolean isTemplateEmpty(StructureTemplate template) {
        Vec3i size = template.getSize();
        return size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0;
    }

    /**
     * Result of placing a structure, exposing the overall bounds, any detected cryo tubes and
     * chunk-aligned slices that callers can use for follow-up processing.
     */
    public record PlacementResult(BlockPos origin, AABB bounds, List<BlockPos> cryoPositions, List<AABB> chunkSlices) {}

    private record TemplateData(StructureTemplate template,
                                List<BlockPos> cryoOffsets,
                                List<BlockPos> elevatorPulleyOffsets,
                                Vec3i size,
                                BlockPos levelingOffset,
                                boolean hasStructureBlocks) {}

    private record CollectionResult(boolean hasStructureBlocks) {}

    private record LevelingMarkerData(BlockPos offset, boolean present) {}

    private record PlacementFoundation(BlockPos origin, BlockState levelingReplacement) {}

    private CollectionResult collectOffsets(StructureTemplate template, List<BlockPos> cryoOffsets,
                                            List<BlockPos> elevatorOffsets, Vec3i size) {
        StructurePlaceSettings identitySettings = new StructurePlaceSettings();

        Block cryoTube = resolveBlock(CRYO_TUBE_NAME, "cryo tube");
        if (cryoTube != Blocks.AIR) {
            for (StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, identitySettings, cryoTube)) {
                cryoOffsets.add(info.pos());
            }
        }

        if (ModList.get().isLoaded("create")) {
            Block elevatorPulley = resolveBlock(CREATE_ELEVATOR_PULLEY_NAME, "Create elevator pulley");
            if (elevatorPulley != Blocks.AIR) {
                for (StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, identitySettings, elevatorPulley)) {
                    elevatorOffsets.add(info.pos());
                }
            }
        }

        boolean hasStructureBlocks = size.getX() > 0 && size.getY() > 0 && size.getZ() > 0;

        return new CollectionResult(hasStructureBlocks);
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

    private void activateCreateElevators(ServerLevel level, BlockPos origin, List<BlockPos> pulleyOffsets) {
        if (!ModList.get().isLoaded("create") || pulleyOffsets.isEmpty()) {
            return;
        }

        // No mixin is required here: Create already exposes the assembly trigger on the block entity
        // itself. Calling the same method that a player interaction would invoke keeps us aligned
        // with Create's update flow without linking against its classes at compile time.
        for (BlockPos offset : pulleyOffsets) {
            BlockPos worldPos = origin.offset(offset);
            BlockEntity blockEntity = level.getBlockEntity(worldPos);
            if (blockEntity == null) {
                continue;
            }
            try {
                Method clicked = blockEntity.getClass().getMethod("clicked");
                clicked.setAccessible(true);
                clicked.invoke(blockEntity);
                level.scheduleTick(worldPos, blockEntity.getBlockState().getBlock(), 1);
            } catch (NoSuchMethodException e) {
                ModConstants.LOGGER.warn("Create elevator pulley at {} for {} exposes no activation hook; skipping.", worldPos, id);
            } catch (Exception e) {
                ModConstants.LOGGER.warn("Failed to prime Create elevator pulley at {} for {}.", worldPos, id, e);
            }
        }
    }

    /**
     * Ensures the placement bounding box encloses entities baked into the template, preventing contraptions that
     * extend below the footprint (e.g., rope pulleys) from being discarded during placement.
     */
    private BoundingBox expandPlacementBox(BlockPos origin, Vec3i size, StructureTemplate template) {
        BoundingBox baseBox = LargeStructurePlacementOptimizer.createPlacementBox(origin, size);
        if (!(template instanceof StructureTemplateAccessor accessor)) {
            return baseBox;
        }

        List<BlockPos> positions = new ArrayList<>();
        positions.add(new BlockPos(baseBox.minX(), baseBox.minY(), baseBox.minZ()));
        positions.add(new BlockPos(baseBox.maxX(), baseBox.maxY(), baseBox.maxZ()));

        for (StructureTemplate.StructureEntityInfo info : accessor.getEntityInfoList()) {
            positions.add(origin.offset(info.blockPos));
        }

        return BoundingBox.encapsulatingPositions(positions).orElse(baseBox);
    }

    private BlockState normalizeLevelingReplacement(BlockState replacement) {
        if (replacement == null) {
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }
        if (replacement.is(BlockTags.DIRT)) {
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }
        return replacement;
    }

    private boolean isStarterBunker() {
        return ModConstants.MOD_ID.equals(id.getNamespace())
                && "bunker".equals(id.getPath());
    }

    private int resolveAutoBlendRadius(Vec3i size) {
        int configuredRadius = StructureConfig.AUTO_TERRAIN_BLEND_RADIUS.get();
        if (!isStarterBunker()) {
            return configuredRadius;
        }
        int maxSpan = Math.max(size.getX(), size.getZ());
        int sizeRadius = maxSpan > 0 ? maxSpan / 24 : 0;
        int blendedRadius = Math.max(configuredRadius, Math.max(4, sizeRadius));
        return Math.min(12, blendedRadius);
    }

    private TerrainReplacerEngine.AutoBlendMask buildAutoBlendMask(StructureTemplate template, BlockPos origin, Vec3i size) {
        if (!(template instanceof StructureTemplateAccessor accessor)) {
            return TerrainReplacerEngine.AutoBlendMask.allowAll();
        }

        int sizeX = size.getX();
        int sizeZ = size.getZ();
        if (sizeX <= 0 || sizeZ <= 0) {
            return TerrainReplacerEngine.AutoBlendMask.allowAll();
        }

        int[] lowestY = new int[sizeX * sizeZ];
        Arrays.fill(lowestY, Integer.MAX_VALUE);

        boolean foundBlocks = false;
        int globalMinY = Integer.MAX_VALUE;
        for (StructureTemplate.Palette palette : accessor.getPalettes()) {
            List<StructureBlockInfo> blocks = resolvePaletteBlocks(palette);
            if (blocks.isEmpty()) {
                continue;
            }
            for (StructureBlockInfo info : blocks) {
                if (info.state().isAir() || info.state().is(Blocks.STRUCTURE_VOID)) {
                    continue;
                }
                int localX = info.pos().getX();
                int localZ = info.pos().getZ();
                if (localX < 0 || localX >= sizeX || localZ < 0 || localZ >= sizeZ) {
                    continue;
                }
                int index = localX + (localZ * sizeX);
                int blockY = info.pos().getY();
                lowestY[index] = Math.min(lowestY[index], blockY);
                globalMinY = Math.min(globalMinY, blockY);
                foundBlocks = true;
            }
        }

        if (!foundBlocks || globalMinY == Integer.MAX_VALUE) {
            return TerrainReplacerEngine.AutoBlendMask.allowAll();
        }

        boolean[] supported = new boolean[sizeX * sizeZ];
        boolean anySupported = false;
        for (int i = 0; i < lowestY.length; i++) {
            if (lowestY[i] != Integer.MAX_VALUE && lowestY[i] == globalMinY) {
                supported[i] = true;
                anySupported = true;
            }
        }

        if (!anySupported) {
            return TerrainReplacerEngine.AutoBlendMask.allowAll();
        }

        return new TerrainReplacerEngine.AutoBlendMask(origin.getX(), origin.getZ(), sizeX, sizeZ, supported);
    }

    @SuppressWarnings("unchecked")
    private List<StructureBlockInfo> resolvePaletteBlocks(StructureTemplate.Palette palette) {
        if (palette == null) {
            return List.of();
        }
        for (String fieldName : PALETTE_BLOCK_FIELD_NAMES) {
            try {
                Field field = palette.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(palette);
                if (value instanceof List<?> list) {
                    return (List<StructureBlockInfo>) list;
                }
            } catch (NoSuchFieldException ignored) {
                // try next candidate
            } catch (IllegalAccessException e) {
                ModConstants.LOGGER.warn("Unable to access structure palette blocks for {}.", id, e);
                return List.of();
            }
        }

        if (!loggedPaletteFieldWarning) {
            loggedPaletteFieldWarning = true;
            ModConstants.LOGGER.warn("Unable to locate structure palette block list for {}; auto-blend masking will be skipped.", id);
        }
        return List.of();
    }

    private void clearTerrainInsideStructure(ServerLevel level, BlockPos origin, Vec3i size, StructureTemplate template) {
        if (!(template instanceof StructureTemplateAccessor accessor)) {
            return;
        }
        int sizeX = size.getX();
        int sizeY = size.getY();
        int sizeZ = size.getZ();
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            return;
        }

        int volume = sizeX * sizeY * sizeZ;
        BitSet occupied = new BitSet(volume);
        int columns = sizeX * sizeZ;
        int[] minYByColumn = new int[columns];
        int[] maxYByColumn = new int[columns];
        Arrays.fill(minYByColumn, Integer.MAX_VALUE);
        Arrays.fill(maxYByColumn, Integer.MIN_VALUE);
        for (StructureTemplate.Palette palette : accessor.getPalettes()) {
            List<StructureBlockInfo> blocks = resolvePaletteBlocks(palette);
            if (blocks.isEmpty()) {
                continue;
            }
            for (StructureBlockInfo info : blocks) {
                BlockState state = info.state();
                if (state.isAir() || state.is(Blocks.STRUCTURE_VOID)) {
                    continue;
                }
                BlockPos pos = info.pos();
                int x = pos.getX();
                int y = pos.getY();
                int z = pos.getZ();
                if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) {
                    continue;
                }
                int index = x + sizeX * (y + sizeY * z);
                occupied.set(index);
                int columnIndex = x + sizeX * z;
                minYByColumn[columnIndex] = Math.min(minYByColumn[columnIndex], y);
                maxYByColumn[columnIndex] = Math.max(maxYByColumn[columnIndex], y);
            }
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                int columnIndex = x + sizeX * z;
                int minY = minYByColumn[columnIndex];
                int maxY = maxYByColumn[columnIndex];
                if (minY == Integer.MAX_VALUE || maxY == Integer.MIN_VALUE) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    int index = x + sizeX * (y + sizeY * z);
                    if (occupied.get(index)) {
                        continue;
                    }
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    BlockState existing = level.getBlockState(cursor);
                    if (!BunkerTerrainClearer.shouldClear(existing)) {
                        continue;
                    }
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }
}
