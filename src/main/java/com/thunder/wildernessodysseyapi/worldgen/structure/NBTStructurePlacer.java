package com.thunder.wildernessodysseyapi.worldgen.structure;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.util.ChunkErrorReporter;
import com.thunder.wildernessodysseyapi.worldgen.config.StructureConfig;
import com.thunder.wildernessodysseyapi.worldgen.processor.BlockEntityNbtSanitizingProcessor;
import com.thunder.wildernessodysseyapi.worldgen.structure.StructurePlacementDebugger.PlacementAttempt;
import com.thunder.wildernessodysseyapi.worldgen.structure.TerrainReplacerEngine.SurfaceSample;
import com.thunder.wildernessodysseyapi.mixin.StructureTemplateAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.AABB;
import net.minecraft.tags.BlockTags;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.neoforged.fml.ModList;

/**
 * Places vanilla structure templates loaded from NBT files and exposes metadata used by the mod.
 */
public class NBTStructurePlacer {
    private static final long MAX_COMPRESSED_TEMPLATE_BYTES = 16L * 1024L * 1024L;
    private static final long MAX_DECODED_NBT_BYTES = 64L * 1024L * 1024L;
    private static final String CRYO_TUBE_NAME = "wildernessodysseyapi:cryo_tube";
    private static final String CREATE_ELEVATOR_PULLEY_NAME = "create:elevator_pulley";
    private static final String LEVELING_MARKER_NAME =
            BuiltInRegistries.BLOCK.getKey(Blocks.BLUE_WOOL).toString();
    private static final int MIN_LEVELING_MARKER_Y = 62;
    private static final int MAX_LEVELING_MARKER_Y = 65;
    private static final int STARTER_BUNKER_PERIMETER_BLEND_RADIUS = 3;
    private static final int STARTER_BUNKER_PERIMETER_BLEND_DEPTH = 2;

    // Restored reflection cache for the Palette final class workaround
    private static final String[] PALETTE_BLOCK_FIELD_NAMES = {"blocks", "blockInfos", "blockInfoList", "c"};
    private static final ConcurrentMap<Class<?>, Optional<Field>> PALETTE_BLOCK_FIELDS = new ConcurrentHashMap<>();
    private static boolean loggedPaletteFieldWarning = false;

    private final ResourceLocation id;
    private final List<StructureProcessor> extraProcessors;
    private final StructureProcessor blockEntityNbtSanitizer;
    private final Path externalTemplatePath;
    private TemplateData cachedData;

    public NBTStructurePlacer(String namespace, String path) {
        this(ResourceLocation.tryBuild(namespace, path));
    }

    public NBTStructurePlacer(ResourceLocation id) {
        this(id, List.of(), null);
    }

    public NBTStructurePlacer(ResourceLocation id, Path externalTemplatePath) {
        this(id, List.of(), externalTemplatePath);
    }

    public NBTStructurePlacer(ResourceLocation id, List<StructureProcessor> extraProcessors) {
        this(id, extraProcessors, null);
    }

    public NBTStructurePlacer(ResourceLocation id, List<StructureProcessor> extraProcessors, Path externalTemplatePath) {
        this.id = id;
        this.extraProcessors = List.copyOf(extraProcessors);
        this.blockEntityNbtSanitizer = new BlockEntityNbtSanitizingProcessor(id);
        this.externalTemplatePath = externalTemplatePath;
    }

    public PlacementResult place(ServerLevel level, BlockPos origin) {
        return place(level, origin, null);
    }

    public PlacementResult placeAnchored(ServerLevel level, BlockPos anchor) {
        return placeAnchored(level, anchor, null);
    }

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
        try {
            if (!LargeStructurePlacementOptimizer.preparePlacement(level, foundation.origin(), data.size())) {
                StructurePlacementDebugger.markFailure(attempt, "placement chunks unavailable or over budget");
                ModConstants.LOGGER.warn(
                        "Skipping structure {} at {} because its {} touched chunks are not all loaded or exceed the {} chunk budget.",
                        id, foundation.origin(),
                        LargeStructurePlacementOptimizer.countPlacementChunks(foundation.origin(), data.size()),
                        LargeStructurePlacementOptimizer.MAX_PLACEMENT_CHUNKS);
                return null;
            }

            BoundingBox placementBox = expandPlacementBox(foundation.origin(), data.size(), data.template());
            if (isStarterBunker()) {
                clearPlacementVolumeForStarterBunker(level, foundation.origin(), data.size());
            }
            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setKnownShape(true)
                    .setBoundingBox(placementBox)
                    .setIgnoreEntities(false);
            for (StructureProcessor processor : extraProcessors) {
                settings.addProcessor(processor);
            }
            // Validate after every caller-supplied transform so state and NBT
            // compatibility is checked at the final placement boundary.
            settings.addProcessor(blockEntityNbtSanitizer);
            boolean placed = data.template().placeInWorld(level, foundation.origin(), foundation.origin(), settings, level.random, 2);
            if (!placed) {
                StructurePlacementDebugger.markFailure(attempt, "template refused placement");
                return null;
            }

            if (isStarterBunker()) {
                clearTerrainInsideStructure(level, foundation.origin(), data.size(), data.template());
                replaceStarterBunkerSurfaceBlocks(level, foundation.origin(), data.size(), placementBox);
                blendStarterBunkerPerimeter(level, foundation.origin(), data.size(), placementBox);
            }

            int autoBlended = 0;
            if (StructureConfig.ENABLE_AUTO_TERRAIN_BLEND.get()) {
                int maxDepth = StructureConfig.AUTO_TERRAIN_BLEND_MAX_DEPTH.get();
                int radius = resolveAutoBlendRadius(data.size());
                TerrainReplacerEngine.AutoBlendMask mask = TerrainReplacerEngine.AutoBlendMask.allowAll();
                if (!isStarterBunker() && StructureConfig.ENABLE_SMART_AUTO_TERRAIN_BLEND.get()) {
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

            List<BlockPos> cryoOffsets = data.cryoOffsets();
            List<BlockPos> cryoPositions = new ArrayList<>(cryoOffsets.size());
            for (BlockPos offset : cryoOffsets) {
                cryoPositions.add(foundation.origin().offset(offset));
            }

            StructurePlacementDebugger.markSuccess(attempt,
                    "placed with %s auto-blended blocks and %s cryo tubes"
                            .formatted(autoBlended, cryoPositions.size()));

            return new PlacementResult(foundation.origin(), bounds, cryoPositions, List.copyOf(chunkSlices));
        } catch (Exception exception) {
            StructurePlacementDebugger.markFailure(attempt, "exception: " + exception.getClass().getSimpleName());
            ChunkErrorReporter.reportChunkError("generation", level, new ChunkPos(foundation.origin()), exception);
            return null;
        }
    }

    public ResourceLocation id() {
        return id;
    }

    public Vec3i peekSize(ServerLevel level) {
        TemplateData data = load(level);
        return data == null ? Vec3i.ZERO : data.size();
    }

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

        // The anchored API receives an explicit world position for the marker.
        // Reapplying generic terrain-depth clamps here treated the marker's
        // local Y offset as burial depth, then immediately undid that clamp to
        // force the marker back to sea level. Honor the caller-owned anchor.
        return new PlacementFoundation(origin, sample.state());
    }

    /**
     * Computes the exact transformed template bounds without reading or changing world blocks.
     *
     * <p>Development tooling uses this for honest previews and then performs a
     * separate server-side containment check before allowing placement.</p>
     */
    public BoundingBox previewBoundingBox(ServerLevel level,
                                          BlockPos origin,
                                          Rotation rotation,
                                          Mirror mirror) {
        TemplateData data = load(level);
        if (data == null || !data.hasStructureBlocks()) {
            return null;
        }
        StructurePlaceSettings settings = transformedSettings(rotation, mirror);
        return data.template().getBoundingBox(settings, origin);
    }

    /**
     * Places a template exactly inside caller-owned bounds with no terrain leveling or blending.
     *
     * <p>This narrow method exists for bounded Development Studio labs. Normal
     * worldgen continues to use the terrain-aware placement paths above.</p>
     */
    public PlacementResult placeExact(ServerLevel level,
                                      BlockPos origin,
                                      Rotation rotation,
                                      Mirror mirror,
                                      BoundingBox allowedBounds) {
        TemplateData data = load(level);
        if (data == null || !data.hasStructureBlocks() || allowedBounds == null) {
            return null;
        }
        StructurePlaceSettings settings = transformedSettings(rotation, mirror);
        BoundingBox placementBox = data.template().getBoundingBox(settings, origin);
        if (!contains(allowedBounds, placementBox)
                || !LargeStructurePlacementOptimizer.preparePlacement(level, origin, data.size())) {
            return null;
        }
        settings.setKnownShape(true)
                .setBoundingBox(allowedBounds)
                .setIgnoreEntities(false);
        for (StructureProcessor processor : extraProcessors) {
            settings.addProcessor(processor);
        }
        settings.addProcessor(blockEntityNbtSanitizer);
        try {
            boolean placed = data.template().placeInWorld(level, origin, origin, settings, level.random, 2);
            if (!placed) {
                return null;
            }
            AABB bounds = new AABB(
                    placementBox.minX(), placementBox.minY(), placementBox.minZ(),
                    placementBox.maxX() + 1.0D, placementBox.maxY() + 1.0D, placementBox.maxZ() + 1.0D
            );
            return new PlacementResult(origin, bounds, List.of(),
                    LargeStructurePlacementOptimizer.computeChunkSlices(origin, data.size()));
        } catch (Exception exception) {
            ChunkErrorReporter.reportChunkError("studio placement", level, new ChunkPos(origin), exception);
            return null;
        }
    }

    /** Drops the cached template so the next preview or placement reloads resources. */
    public void reload(ServerLevel level) {
        cachedData = null;
        level.getStructureManager().remove(id);
    }

    public List<BlockPos> getCryoOffsets(ServerLevel level) {
        TemplateData data = load(level);
        return data == null ? List.of() : data.cryoOffsets();
    }

    private TemplateData load(ServerLevel level) {
        if (cachedData != null) {
            return cachedData;
        }

        StructureTemplateManager manager = level.getStructureManager();
        // An explicit operator-supplied file owns this request. Do not silently substitute a
        // bundled template with the same id, and always route external input through our quotas.
        StructureTemplate template = externalTemplatePath == null
                ? manager.get(id).orElse(null)
                : loadDirect(level, manager);
        if (template == null || isTemplateEmpty(template)) {
            manager.remove(id);
            template = externalTemplatePath == null ? loadDirect(level, manager) : null;
            if (template == null || isTemplateEmpty(template)) {
                return null;
            }
        }

        if (!LargeStructurePlacementOptimizer.isWithinTemplateBudget(template.getSize())) {
            ModConstants.LOGGER.warn(
                    "Structure template {} has rejected dimensions {}. Limits are {} blocks per axis and {} total blocks.",
                    id, template.getSize(), StructureUtils.STRUCTURE_BLOCK_LIMIT,
                    LargeStructurePlacementOptimizer.MAX_TEMPLATE_VOLUME);
            manager.remove(id);
            return null;
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

        if (hasStructureBlocks) {
            cachedData = data;
        } else {
            cachedData = null;
            manager.remove(id);
        }

        return data;
    }

    private StructureTemplate loadDirect(ServerLevel level, StructureTemplateManager manager) {
        if (externalTemplatePath != null) {
            if (!Files.exists(externalTemplatePath)) {
                ModConstants.LOGGER.warn("External structure template {} not found for {}.", externalTemplatePath, id);
                return null;
            }
            try {
                long compressedSize = Files.size(externalTemplatePath);
                if (compressedSize > MAX_COMPRESSED_TEMPLATE_BYTES) {
                    ModConstants.LOGGER.warn(
                            "External structure template {} for {} is {} bytes; the compressed limit is {} bytes.",
                            externalTemplatePath, id, compressedSize, MAX_COMPRESSED_TEMPLATE_BYTES);
                    return null;
                }
                CompoundTag tag = readBoundedCompressed(Files.newInputStream(externalTemplatePath));
                return manager.readStructure(tag);
            } catch (Exception e) {
                ModConstants.LOGGER.warn("Failed to read external structure template {} for {}.", externalTemplatePath, id, e);
                return null;
            }
        }

        ResourceLocation resourcePath = ResourceLocation.fromNamespaceAndPath(
                id.getNamespace(),
                "structures/" + id.getPath() + ".nbt");
        Optional<Resource> resource = level.getServer().getResourceManager().getResource(resourcePath);
        if (resource.isEmpty()) {
            ModConstants.LOGGER.warn("Structure template {} not found at {}.", id, resourcePath);
            return null;
        }

        try {
            CompoundTag tag = readBoundedCompressed(resource.get().open());
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
        StructurePlaceSettings identitySettings = new StructurePlaceSettings();
        List<StructureBlockInfo> markers = template.filterBlocks(BlockPos.ZERO, identitySettings, Blocks.BLUE_WOOL);
        if (markers.isEmpty()) {
            return new LevelingMarkerData(null, false);
        }

        Vec3i size = template.getSize();
        double centerX = size.getX() / 2.0D;
        double centerZ = size.getZ() / 2.0D;

        StructureBlockInfo best = null;
        int bestY = 0;
        double bestDist = 0.0D;
        for (StructureBlockInfo marker : markers) {
            int markerY = marker.pos().getY();
            double dx = marker.pos().getX() - centerX;
            double dz = marker.pos().getZ() - centerZ;
            double dist = (dx * dx) + (dz * dz);
            if (best == null || markerY < bestY || (markerY == bestY && dist < bestDist)) {
                best = marker;
                bestY = markerY;
                bestDist = dist;
            }
        }
        return new LevelingMarkerData(best == null ? null : best.pos(), true);
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
        int sizeRadius = maxSpan > 0 ? maxSpan / 16 : 0;
        int blendedRadius = Math.max(configuredRadius, Math.max(8, sizeRadius));
        return Math.min(32, blendedRadius);
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

        final int supportTolerance = 1;
        boolean[] supported = new boolean[sizeX * sizeZ];
        boolean anySupported = false;
        for (int i = 0; i < lowestY.length; i++) {
            if (lowestY[i] != Integer.MAX_VALUE && lowestY[i] <= globalMinY + supportTolerance) {
                supported[i] = true;
                anySupported = true;
            }
        }

        if (!anySupported) {
            return TerrainReplacerEngine.AutoBlendMask.allowAll();
        }

        return new TerrainReplacerEngine.AutoBlendMask(origin.getX(), origin.getZ(), sizeX, sizeZ, supported);
    }

    // THE FIX: Restored the cached reflection logic to bypass the final class compilation error
    @SuppressWarnings("unchecked")
    private List<StructureBlockInfo> resolvePaletteBlocks(StructureTemplate.Palette palette) {
        try {
            Optional<Field> fieldOpt = PALETTE_BLOCK_FIELDS.computeIfAbsent(palette.getClass(), clazz -> {
                for (String fieldName : PALETTE_BLOCK_FIELD_NAMES) {
                    try {
                        Field field = clazz.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        return Optional.of(field);
                    } catch (NoSuchFieldException ignored) {
                    }
                }
                return Optional.empty();
            });

            if (fieldOpt.isPresent()) {
                return (List<StructureBlockInfo>) fieldOpt.get().get(palette);
            } else if (!loggedPaletteFieldWarning) {
                ModConstants.LOGGER.warn("Failed to find block list field in StructureTemplate.Palette! Auto-blending will be skipped.");
                loggedPaletteFieldWarning = true;
            }
        } catch (Exception e) {
            if (!loggedPaletteFieldWarning) {
                ModConstants.LOGGER.warn("Error accessing StructureTemplate.Palette blocks: {}", e.getMessage());
                loggedPaletteFieldWarning = true;
            }
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
                    clearTerrainBlockWithoutDrops(level, cursor);
                }
            }
        }
    }

    private void clearPlacementVolumeForStarterBunker(ServerLevel level, BlockPos origin, Vec3i size) {
        int sizeX = size.getX();
        int sizeY = size.getY();
        int sizeZ = size.getZ();
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            return;
        }

        // Only terrain intersecting the template can obstruct placement. Scanning the full world column here
        // multiplied a large bunker footprint by the entire build height and also removed unrelated terrain.
        int minY = Math.max(level.getMinBuildHeight(), origin.getY());
        int placementMaxY = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + sizeY - 1);
        if (minY > placementMaxY) {
            return;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                int worldX = origin.getX() + x;
                int worldZ = origin.getZ() + z;
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1;
                int maxY = Math.min(placementMaxY, surfaceY);
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(worldX, y, worldZ);
                    BlockState existing = level.getBlockState(cursor);
                    if (!BunkerTerrainClearer.shouldClear(existing) || existing.is(Blocks.BEDROCK)) {
                        continue;
                    }
                    clearTerrainBlockWithoutDrops(level, cursor);
                }
            }
        }
    }

    // Bounds both compressed input and decoded NBT accounting before a template object is created.
    private CompoundTag readBoundedCompressed(InputStream source) throws Exception {
        try (source) {
            byte[] compressed = source.readNBytes((int) MAX_COMPRESSED_TEMPLATE_BYTES + 1);
            if (compressed.length > MAX_COMPRESSED_TEMPLATE_BYTES) {
                throw new IllegalArgumentException("compressed structure template exceeds "
                        + MAX_COMPRESSED_TEMPLATE_BYTES + " bytes");
            }
            try (ByteArrayInputStream bounded = new ByteArrayInputStream(compressed)) {
                CompoundTag tag = NbtIo.readCompressed(bounded, NbtAccounter.create(MAX_DECODED_NBT_BYTES));
                int blockCount = tag.getList("blocks", Tag.TAG_COMPOUND).size();
                int entityCount = tag.getList("entities", Tag.TAG_COMPOUND).size();
                if (!LargeStructurePlacementOptimizer.isWithinContentBudget(blockCount, entityCount)) {
                    throw new IllegalArgumentException(
                            "structure template content exceeds placement limits: "
                                    + blockCount + "/" + LargeStructurePlacementOptimizer.MAX_TEMPLATE_BLOCKS
                                    + " blocks, "
                                    + entityCount + "/" + LargeStructurePlacementOptimizer.MAX_TEMPLATE_ENTITIES
                                    + " entities");
                }
                return tag;
            }
        }
    }

    private StructurePlaceSettings transformedSettings(Rotation rotation, Mirror mirror) {
        return new StructurePlaceSettings()
                .setRotation(rotation == null ? Rotation.NONE : rotation)
                .setMirror(mirror == null ? Mirror.NONE : mirror);
    }

    private boolean contains(BoundingBox outer, BoundingBox inner) {
        return inner.minX() >= outer.minX() && inner.maxX() <= outer.maxX()
                && inner.minY() >= outer.minY() && inner.maxY() <= outer.maxY()
                && inner.minZ() >= outer.minZ() && inner.maxZ() <= outer.maxZ();
    }

    /**
     * Clears terrain for starter-structure placement without running container
     * drop paths from modded block entities.
     *
     * <p>The starter bunker can replace generated modded blocks while the
     * integrated server is still creating spawn. Some blocks unpack loot or
     * fire gameplay events from {@code onRemove}; removing the block entity
     * first keeps structure carving from cascading into unrelated event-bus
     * handlers before a player world is fully running.</p>
     */
    private void clearTerrainBlockWithoutDrops(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) != null) {
            level.removeBlockEntity(pos);
        }
        level.setBlock(
                pos,
                Blocks.AIR.defaultBlockState(),
                Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS
        );
    }

    private void replaceStarterBunkerSurfaceBlocks(ServerLevel level,
                                                   BlockPos origin,
                                                   Vec3i size,
                                                   BoundingBox bounds) {
        if (bounds == null) {
            return;
        }
        int sizeX = size.getX();
        int sizeY = size.getY();
        int sizeZ = size.getZ();
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            return;
        }

        int minX = origin.getX();
        int minZ = origin.getZ();
        int maxX = origin.getX() + sizeX - 1;
        int maxZ = origin.getZ() + sizeZ - 1;
        int minY = origin.getY();
        int maxY = origin.getY() + sizeY - 1;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (surfaceY < minY || surfaceY > maxY) {
                    continue;
                }
                cursor.set(x, surfaceY, z);
                if (!bounds.isInside(cursor)) {
                    continue;
                }
                BlockState existing = level.getBlockState(cursor);
                if (!isSurfaceReplacementCandidate(existing)) {
                    continue;
                }

                TerrainReplacerEngine.SurfaceMaterial material =
                        TerrainReplacerEngine.sampleSurfaceMaterialOutsideBounds(level, cursor, bounds);
                BlockState replacement = TerrainReplacerEngine.chooseReplacement(material, surfaceY);
                if (replacement == existing) {
                    continue;
                }
                level.setBlock(cursor, replacement, 2);
            }
        }
    }

    private void blendStarterBunkerPerimeter(ServerLevel level,
                                             BlockPos origin,
                                             Vec3i size,
                                             BoundingBox bounds) {
        if (bounds == null) {
            return;
        }
        int sizeX = size.getX();
        int sizeZ = size.getZ();
        if (sizeX <= 0 || sizeZ <= 0) {
            return;
        }

        int minX = origin.getX() - STARTER_BUNKER_PERIMETER_BLEND_RADIUS;
        int minZ = origin.getZ() - STARTER_BUNKER_PERIMETER_BLEND_RADIUS;
        int maxX = origin.getX() + sizeX - 1 + STARTER_BUNKER_PERIMETER_BLEND_RADIUS;
        int maxZ = origin.getZ() + sizeZ - 1 + STARTER_BUNKER_PERIMETER_BLEND_RADIUS;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int edgeDistance = distanceToBounds2D(x, z, bounds);
                if (edgeDistance <= 0 || edgeDistance > STARTER_BUNKER_PERIMETER_BLEND_RADIUS) {
                    continue;
                }

                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                cursor.set(x, surfaceY, z);
                BlockState existing = level.getBlockState(cursor);
                if (!isSurfaceReplacementCandidate(existing)) {
                    continue;
                }

                TerrainReplacerEngine.SurfaceMaterial material =
                        TerrainReplacerEngine.sampleSurfaceMaterialOutsideBounds(level, cursor, bounds);

                int depth = Math.max(1, STARTER_BUNKER_PERIMETER_BLEND_DEPTH - (edgeDistance - 1));
                int minY = surfaceY - (depth - 1);
                for (int y = surfaceY; y >= minY; y--) {
                    cursor.setY(y);
                    BlockState stateAtY = level.getBlockState(cursor);
                    if (!isSurfaceReplacementCandidate(stateAtY)) {
                        break;
                    }
                    BlockState replacement = TerrainReplacerEngine.chooseReplacement(material, y);
                    if (replacement != stateAtY) {
                        level.setBlock(cursor, replacement, 2);
                    }
                }
            }
        }
    }

    private int distanceToBounds2D(int x, int z, BoundingBox bounds) {
        int dx;
        if (x < bounds.minX()) {
            dx = bounds.minX() - x;
        } else if (x > bounds.maxX()) {
            dx = x - bounds.maxX();
        } else {
            dx = 0;
        }

        int dz;
        if (z < bounds.minZ()) {
            dz = bounds.minZ() - z;
        } else if (z > bounds.maxZ()) {
            dz = z - bounds.maxZ();
        } else {
            dz = 0;
        }

        return Math.max(dx, dz);
    }

    private boolean isSurfaceReplacementCandidate(BlockState state) {
        return state.is(BlockTags.DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.GRAVEL);
    }
}
