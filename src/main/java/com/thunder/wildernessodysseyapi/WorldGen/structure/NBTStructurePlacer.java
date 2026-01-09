package com.thunder.wildernessodysseyapi.WorldGen.structure;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.configurable.StructureConfig;
import com.thunder.wildernessodysseyapi.WorldGen.structure.StructurePlacementDebugger.PlacementAttempt;
import com.thunder.wildernessodysseyapi.WorldGen.structure.TerrainReplacerEngine.SurfaceSample;
import com.thunder.wildernessodysseyapi.WorldGen.structure.TerrainReplacerEngine.TerrainReplacementPlan;
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.neoforged.fml.ModList;

/**
 * Places vanilla structure templates loaded from NBT files and exposes metadata used by the mod.
 */
public class NBTStructurePlacer {
    private static final String TERRAIN_REPLACER_NAME = "wildernessodysseyapi:terrain_replacer";
    private static final String CRYO_TUBE_NAME = "wildernessodysseyapi:cryo_tube";
    private static final String CREATE_ELEVATOR_PULLEY_NAME = "create:elevator_pulley";
    private static final String LEVELING_MARKER_NAME =
            BuiltInRegistries.BLOCK.getKey(Blocks.BLUE_WOOL).toString();
    private static final int MIN_LEVELING_MARKER_Y = 62;
    private static final int MAX_LEVELING_MARKER_Y = 65;

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

        boolean enableTerrainReplacer = shouldEnableTerrainReplacer(data);
        TerrainReplacementPlan replacementPlan = TerrainReplacerEngine.planReplacement(
                level, foundation.origin(), data.terrainOffsets(), enableTerrainReplacer);
        if (!replacementPlan.enabled() && !data.terrainOffsets().isEmpty()) {
            if (!StructureConfig.ENABLE_TERRAIN_REPLACER.get()) {
                ModConstants.LOGGER.info("Terrain replacer markers are present in {} but replacement is disabled via config.", id);
            } else if (!data.hasMarkerWool()) {
                ModConstants.LOGGER.info("Terrain replacer markers are present in {} but no wool markers were found; skipping replacement.", id);
            } else {
                ModConstants.LOGGER.info("Terrain replacer markers are present in {} but replacement is disabled due to template safety checks.", id);
            }
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

        int replaced = applyTerrainReplacement(level, foundation.origin(), data.terrainOffsets(),
                replacementPlan, data.levelingOffset());
        int autoBlended = 0;
        if (replaced == 0
                && data.terrainOffsets().isEmpty()
                && StructureConfig.ENABLE_AUTO_TERRAIN_BLEND.get()) {
            int maxDepth = StructureConfig.AUTO_TERRAIN_BLEND_MAX_DEPTH.get();
            int radius = StructureConfig.AUTO_TERRAIN_BLEND_RADIUS.get();
            autoBlended = TerrainReplacerEngine.applyAutoBlend(level, placementBox, maxDepth, radius);
        }

        if (data.levelingOffset() != null && foundation.levelingReplacement() != null) {
            BlockPos markerWorldPos = foundation.origin().offset(data.levelingOffset());
            BlockState markerReplacement = foundation.levelingReplacement();
            if (shouldForceDirtLayer(data.levelingOffset())) {
                markerReplacement = Blocks.DIRT.defaultBlockState();
            }
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
                "placed with %s terrain samples, %s auto-blended blocks, and %s cryo tubes"
                        .formatted(replaced, autoBlended, cryoPositions.size()));

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

    private boolean shouldEnableTerrainReplacer(TemplateData data) {
        return StructureConfig.ENABLE_TERRAIN_REPLACER.get()
                && !data.disableTerrainReplacement()
                && data.hasMarkerWool();
    }

    private int applyTerrainReplacement(ServerLevel level,
                                        BlockPos origin,
                                        List<BlockPos> offsets,
                                        TerrainReplacementPlan plan,
                                        BlockPos levelingOffset) {
        if (!plan.enabled()) {
            return 0;
        }

        boolean forceDirtLayer = shouldForceDirtLayer(levelingOffset);
        int dirtLayerY = forceDirtLayer ? levelingOffset.getY() : Integer.MIN_VALUE;
        int applied = 0;
        for (int i = 0; i < offsets.size() && i < plan.samples().size(); i++) {
            BlockPos offset = offsets.get(i);
            if (forceDirtLayer && offset.getY() != dirtLayerY) {
                continue;
            }
            BlockPos worldPos = origin.offset(offset);
            BlockState replacement = forceDirtLayer
                    ? Blocks.DIRT.defaultBlockState()
                    : plan.samples().get(i);
            level.setBlock(worldPos, replacement, 2);
            applied++;
        }

        return applied;
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
        List<BlockPos> terrainOffsets = new ArrayList<>();
        List<BlockPos> elevatorPulleyOffsets = new ArrayList<>();
        BlockPos levelingOffset = null;
        Vec3i size = template.getSize();

        CollectionResult collectionResult = collectOffsets(template, cryoOffsets, terrainOffsets, elevatorPulleyOffsets, size);
        boolean disableTerrainReplacement = collectionResult.disableTerrainReplacement();
        boolean hasStructureBlocks = collectionResult.hasStructureBlocks();
        LevelingMarkerData levelingData = findLevelingMarker(template);
        levelingOffset = levelingData.offset();

        TemplateData data = new TemplateData(template, List.copyOf(cryoOffsets), List.copyOf(terrainOffsets),
                List.copyOf(elevatorPulleyOffsets), size, levelingOffset, disableTerrainReplacement,
                levelingData.present(), hasStructureBlocks);

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
                                List<BlockPos> terrainOffsets,
                                List<BlockPos> elevatorPulleyOffsets,
                                Vec3i size,
                                BlockPos levelingOffset,
                                boolean disableTerrainReplacement,
                                boolean hasMarkerWool,
                                boolean hasStructureBlocks) {}

    private record CollectionResult(boolean disableTerrainReplacement, boolean hasStructureBlocks) {}

    private record LevelingMarkerData(BlockPos offset, boolean present) {}

    private record PlacementFoundation(BlockPos origin, BlockState levelingReplacement) {}

    private CollectionResult collectOffsets(StructureTemplate template, List<BlockPos> cryoOffsets, List<BlockPos> terrainOffsets,
                                            List<BlockPos> elevatorOffsets, Vec3i size) {
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

        if (ModList.get().isLoaded("create")) {
            Block elevatorPulley = resolveBlock(CREATE_ELEVATOR_PULLEY_NAME, "Create elevator pulley");
            if (elevatorPulley != Blocks.AIR) {
                for (StructureBlockInfo info : template.filterBlocks(BlockPos.ZERO, identitySettings, elevatorPulley)) {
                    elevatorOffsets.add(info.pos());
                }
            }
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

        BoundingBox expanded = BoundingBox.fromCorners(
                new BlockPos(baseBox.minX(), baseBox.minY(), baseBox.minZ()),
                new BlockPos(baseBox.maxX(), baseBox.maxY(), baseBox.maxZ()));

        for (StructureTemplate.StructureEntityInfo info : accessor.getEntityInfoList()) {
            BlockPos entityPos = origin.offset(info.blockPos);
            expanded.encapsulate(entityPos);
        }
        return expanded;
    }

    private boolean shouldForceDirtLayer(BlockPos levelingOffset) {
        return levelingOffset != null
                && ModConstants.MOD_ID.equals(id.getNamespace())
                && "bunker".equals(id.getPath());
    }

}
