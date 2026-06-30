package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thunder.wildernessodysseyapi.gpuprofiler.client.GpuDiagnostics;
import com.thunder.wildernessodysseyapi.watersystem.ocean.ClientOceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveProfile;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSurfaceSample;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSpectrumState;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterVolumeChunk;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.textures.FluidSpriteCache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the authoritative, per-frame surface for exposed open water around the camera.
 *
 * <p>World scanning is cached and only refreshed after movement or water-state
 * age thresholds. Geometry is regenerated every frame from one world-space
 * wave function, so neighboring chunks cannot bake different phases. Water-body
 * classification selects wave physics but never removes surface coverage. The
 * same mesh works with a standard translucent RenderType under external shader
 * packs and with the mod's optional optical core shader otherwise.</p>
 */
@EventBusSubscriber(modid = "wildernessodysseyapi", value = Dist.CLIENT)
public final class OceanSurfaceRenderer {

    private static final FluidState WATER_STATE = Fluids.WATER.defaultFluidState();
    private static final int CACHE_LIFETIME_TICKS = 40;
    private static final int MAX_DEPTH_SAMPLE = 16;
    private static final int SHORE_DETAIL_DEPTH = 12;
    private static final int MAX_DYNAMIC_CELL_SIZE = 2;
    private static final float MAX_SURFACE_STEP = 0.75f;
    private static final float UV_SCALE = 0.28f;
    private static final float VISUAL_TIDE_SCALE = 0.18f;

    private static final List<SurfacePatch> PATCHES = new ArrayList<>();
    private static volatile LongSet ownedVanillaTops = LongSets.EMPTY_SET;
    private static ClientLevel cachedLevel;
    private static int cachedCenterX = Integer.MIN_VALUE;
    private static int cachedCenterZ = Integer.MIN_VALUE;
    private static int cachedNearRadius = -1;
    private static int cachedFarRadius = -1;
    private static int cachedNearCellSize = -1;
    private static long cachedGameTime = Long.MIN_VALUE;

    private OceanSurfaceRenderer() {
    }

    /** Renders replacement open-water tops after translucent terrain. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        if (!WaterRenderingConfig.ENABLE_GERSTNER_WAVES.get()
                || !WaterRenderingConfig.ENABLE_DYNAMIC_OCEAN_SURFACE.get()) {
            releaseVanillaTopOwnership(Minecraft.getInstance().level);
            return;
        }

        try (GpuDiagnostics.Scope ignored = GpuDiagnostics.scope("water.surface.dynamic")) {
            render(event);
        }
    }

    private static void render(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            clearCache();
            return;
        }

        var camera = event.getCamera().getPosition();
        int nearRadius = WaterRenderingConfig.oceanRenderDistanceBlocks();
        int farRadius = WaterRenderingConfig.dynamicOceanRenderDistanceBlocks(
                minecraft.options.getEffectiveRenderDistance()
        );
        int nearCellSize = WaterRenderingConfig.oceanCellSize();
        refreshCacheIfNeeded(
                level,
                (int) Math.floor(camera.x),
                (int) Math.floor(camera.z),
                nearRadius,
                farRadius,
                nearCellSize
        );
        if (PATCHES.isEmpty()) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float timeSeconds = (level.getGameTime() + partialTick) / 20.0f;
        float tideOffset = TideSystem.getTideOffset(level) * VISUAL_TIDE_SCALE;
        OceanSeaState.Sample seaState = ClientOceanSeaState.current(level);

        boolean coreShader = WaterShaders.shouldUseCoreShader();
        RenderType renderType = coreShader ? WaterRenderTypes.dynamicOcean() : RenderType.translucent();
        if (coreShader) {
            WaterShaders.updateOceanUniforms(
                    timeSeconds,
                    seaState.strength(),
                    seaState.windDirectionX(),
                    seaState.windDirectionZ(),
                    ((level.getDayTime() + partialTick) % 24_000L) / 24_000.0f
            );
        }

        TextureAtlasSprite sprite = FluidSpriteCache.getFluidSprites(
                level,
                BlockPos.containing(camera),
                WATER_STATE
        )[0];
        var bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(renderType);
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        for (SurfacePatch patch : PATCHES) {
            drawPatch(level, patch, timeSeconds, tideOffset, seaState,
                    sprite, poseStack.last(), buffer,
                    (float) camera.x, (float) camera.z, nearRadius, farRadius);
        }

        poseStack.popPose();
        bufferSource.endBatch(renderType);
    }

    private static void drawPatch(
            ClientLevel level,
            SurfacePatch patch,
            float timeSeconds,
            float tideOffset,
            OceanSeaState.Sample seaState,
            TextureAtlasSprite sprite,
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float cameraX,
            float cameraZ,
            int nearRadius,
            int farRadius
    ) {
        float waveBlend = smoothStep(0.35f, 4.0f, patch.depth);
        int waveLimit = WaterRenderingConfig.waveTrainLimit(patch.waterType);
        GerstnerWaveProfile waveProfile = profileFor(patch.waterType);
        float localTideOffset = patch.waterType == WaterBodyClassifier.WaterType.OCEAN
                ? tideOffset
                : 0.0f;
        float x0 = patch.x;
        float z0 = patch.z;
        float x1 = patch.x + patch.size;
        float z1 = patch.z + patch.size;

        WaveSpectrumState spectrum = patch.waterType == WaterBodyClassifier.WaterType.OCEAN
                ? seaState.spectrum()
                : WaveSpectrumState.NEUTRAL;
        int patchColor = opticalColor(
                level,
                (x0 + x1) * 0.5f,
                (patch.firstY + patch.secondY + patch.thirdY + patch.fourthY) * 0.25f,
                (z0 + z1) * 0.5f,
                patch.depth
        );
        VertexData first = vertex(level, x0, patch.firstY, z0, waveBlend,
                waveProfile, spectrum, timeSeconds, localTideOffset, waveLimit, patchColor,
                cameraX, cameraZ, nearRadius, farRadius);
        VertexData second = vertex(level, x0, patch.secondY, z1, waveBlend,
                waveProfile, spectrum, timeSeconds, localTideOffset, waveLimit, patchColor,
                cameraX, cameraZ, nearRadius, farRadius);
        VertexData third = vertex(level, x1, patch.thirdY, z1, waveBlend,
                waveProfile, spectrum, timeSeconds, localTideOffset, waveLimit, patchColor,
                cameraX, cameraZ, nearRadius, farRadius);
        VertexData fourth = vertex(level, x1, patch.fourthY, z0, waveBlend,
                waveProfile, spectrum, timeSeconds, localTideOffset, waveLimit, patchColor,
                cameraX, cameraZ, nearRadius, farRadius);

        emitVertex(buffer, pose, sprite, first);
        emitVertex(buffer, pose, sprite, second);
        emitVertex(buffer, pose, sprite, third);
        emitVertex(buffer, pose, sprite, fourth);
    }

    private static VertexData vertex(
            ClientLevel level,
            float baseX,
            float baseY,
            float baseZ,
            float waveBlend,
            GerstnerWaveProfile waveProfile,
            WaveSpectrumState spectrum,
            float timeSeconds,
            float tideOffset,
            int waveLimit,
            int color,
            float cameraX,
            float cameraZ,
            int nearRadius,
            int farRadius
    ) {
        float distanceX = baseX - cameraX;
        float distanceZ = baseZ - cameraZ;
        float distance = (float) Math.sqrt(distanceX * distanceX + distanceZ * distanceZ);
        float distanceDetail = farRadius <= nearRadius
                ? 1.0f
                : 1.0f - smoothStep(nearRadius, farRadius, distance) * 0.80f;

        // Distant geometry is coarser, so its large gravity-wave curvature is
        // gradually reduced per world-space vertex. The fragment shader keeps
        // fine shimmer while the mesh avoids visibly planar triangle halves.
        WaveSurfaceSample sample = waveProfile
                .sampleAt(baseX, baseZ, timeSeconds, waveLimit, spectrum)
                .withHeightOffset(tideOffset)
                .attenuated(waveBlend * distanceDetail);
        float x = baseX + sample.displacementX();
        float y = baseY + sample.height() + 0.002f;
        float z = baseZ + sample.displacementZ();
        int light = waterLight(level, x, y, z);
        return new VertexData(x, y, z, sample.normalX(), sample.normalY(), sample.normalZ(), color, light);
    }

    private static void emitVertex(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            TextureAtlasSprite sprite,
            VertexData vertex
    ) {
        float u = sprite.getU(tile(vertex.x * UV_SCALE));
        float v = sprite.getV(tile(vertex.z * UV_SCALE));
        buffer.addVertex(pose, vertex.x, vertex.y, vertex.z)
                .setColor(
                        (vertex.color >> 16) & 0xFF,
                        (vertex.color >> 8) & 0xFF,
                        vertex.color & 0xFF,
                        (vertex.color >>> 24) & 0xFF
                )
                .setUv(u, v)
                .setLight(vertex.light)
                .setNormal(pose, vertex.normalX, vertex.normalY, vertex.normalZ);
    }

    private static int opticalColor(
            ClientLevel level,
            float x,
            float y,
            float z,
            float depth
    ) {
        int tint = IClientFluidTypeExtensions.of(Fluids.WATER).getTintColor(
                WATER_STATE,
                level,
                BlockPos.containing(x, y, z)
        );
        float tintR = ((tint >> 16) & 0xFF) / 255.0f;
        float tintG = ((tint >> 8) & 0xFF) / 255.0f;
        float tintB = (tint & 0xFF) / 255.0f;

        float absorption = 1.0f - (float) Math.exp(-Math.max(0.0f, depth) * 0.32f);

        // Keep the replacement surface optically water-colored even over dark
        // ocean floors. The shader still applies depth, light, and Fresnel, but
        // the base medium should not become black transparent terrain cutouts.
        float shallowR = 0.10f + tintR * 0.40f;
        float shallowG = 0.52f + tintG * 0.30f;
        float shallowB = 0.70f + tintB * 0.28f;
        float deepR = 0.04f + tintR * 0.18f;
        float deepG = 0.25f + tintG * 0.18f;
        float deepB = 0.52f + tintB * 0.24f;
        float red = mix(shallowR, deepR, absorption);
        float green = mix(shallowG, deepG, absorption);
        float blue = mix(shallowB, deepB, absorption);

        float foam = smoothStep(0.0f, 0.8f, 1.15f - depth);
        red = mix(red, 0.86f, foam);
        green = mix(green, 0.94f, foam);
        blue = mix(blue, 1.0f, foam);
        float alpha = 0.72f + absorption * 0.18f + foam * 0.05f;

        return (channel(alpha) << 24)
                | (channel(red) << 16)
                | (channel(green) << 8)
                | channel(blue);
    }

    private static void refreshCacheIfNeeded(
            ClientLevel level,
            int cameraX,
            int cameraZ,
            int nearRadius,
            int farRadius,
            int nearCellSize
    ) {
        long gameTime = level.getGameTime();
        int movementThreshold = Math.max(8, nearCellSize * 8);
        boolean stale = cachedLevel != level
                || nearRadius != cachedNearRadius
                || farRadius != cachedFarRadius
                || nearCellSize != cachedNearCellSize
                || Math.abs(cameraX - cachedCenterX) >= movementThreshold
                || Math.abs(cameraZ - cachedCenterZ) >= movementThreshold
                || gameTime - cachedGameTime >= CACHE_LIFETIME_TICKS;
        if (!stale) {
            return;
        }

        cachedLevel = level;
        cachedCenterX = cameraX;
        cachedCenterZ = cameraZ;
        cachedNearRadius = nearRadius;
        cachedFarRadius = farRadius;
        cachedNearCellSize = nearCellSize;
        cachedGameTime = gameTime;
        PATCHES.clear();

        Map<Long, WaterColumn> columns = new HashMap<>();
        Map<Long, SurfaceColumn> surfaces = new HashMap<>();
        LongOpenHashSet rebuiltOwnedTops = new LongOpenHashSet();
        int mediumRadius = Math.min(farRadius, Math.max(nearRadius, nearRadius * 2));
        // The true replacement ring stays at one-block detail; only the far
        // overlay is allowed to use tiny coarse cells. That preserves the
        // vanilla top under distant LOD water and removes large transparent
        // triangle artifacts while still covering the full view distance.
        int detailCellSize = 1;
        int farCellSize = Math.min(MAX_DYNAMIC_CELL_SIZE, Math.max(1, nearCellSize));
        int mediumCellSize = 1;
        int paddedFarRadius = farRadius + farCellSize;
        int paddedFarRadiusSquared = paddedFarRadius * paddedFarRadius;
        int startX = Math.floorDiv(cameraX - farRadius, farCellSize) * farCellSize;
        int endX = Math.floorDiv(cameraX + farRadius, farCellSize) * farCellSize;
        int startZ = Math.floorDiv(cameraZ - farRadius, farCellSize) * farCellSize;
        int endZ = Math.floorDiv(cameraZ + farRadius, farCellSize) * farCellSize;

        // Every far cell is either kept coarse or subdivided completely. That
        // keeps the LOD rings world-aligned with no overlapping or uncovered
        // cells in the cached footprint.
        for (int coarseX = startX; coarseX <= endX; coarseX += farCellSize) {
            for (int coarseZ = startZ; coarseZ <= endZ; coarseZ += farCellSize) {
                int dx = coarseX + farCellSize / 2 - cameraX;
                int dz = coarseZ + farCellSize / 2 - cameraZ;
                int distanceSquared = dx * dx + dz * dz;
                if (distanceSquared > paddedFarRadiusSquared) {
                    continue;
                }

                int selectedCellSize = distanceSquared <= nearRadius * nearRadius
                        ? detailCellSize
                        : distanceSquared <= mediumRadius * mediumRadius
                                ? mediumCellSize
                                : farCellSize;
                for (int x = coarseX; x < coarseX + farCellSize; x += selectedCellSize) {
                    for (int z = coarseZ; z < coarseZ + farCellSize; z += selectedCellSize) {
                        addPatch(level, columns, surfaces, rebuiltOwnedTops, x, z, selectedCellSize);
                    }
                }
            }
        }
        updateVanillaTopOwnership(level, rebuiltOwnedTops);
    }

    private static void addPatch(
            ClientLevel level,
            Map<Long, WaterColumn> columns,
            Map<Long, SurfaceColumn> surfaces,
            LongOpenHashSet rebuiltOwnedTops,
            int x,
            int z,
            int cellSize
    ) {
        WaterColumn first = column(level, columns, surfaces, x, z);
        WaterColumn second = column(level, columns, surfaces, x, z + cellSize);
        WaterColumn third = column(level, columns, surfaces, x + cellSize, z + cellSize);
        WaterColumn fourth = column(level, columns, surfaces, x + cellSize, z);
        if (!first.valid || !second.valid || !third.valid || !fourth.valid) {
            subdivideShorePatch(level, columns, surfaces, rebuiltOwnedTops, x, z, cellSize);
            return;
        }

        WaterColumn center = column(
                level,
                columns,
                surfaces,
                x + cellSize / 2,
                z + cellSize / 2
        );
        float sampledMinimumDepth = center.valid
                ? Math.min(
                        center.depth,
                        Math.min(Math.min(first.depth, second.depth), Math.min(third.depth, fourth.depth))
                )
                : 0.0f;

        PatchFootprint footprint = validatePatchFootprint(
                level,
                surfaces,
                x,
                z,
                cellSize,
                sampledMinimumDepth
        );
        if (!footprint.valid) {
            subdivideShorePatch(level, columns, surfaces, rebuiltOwnedTops, x, z, cellSize);
            return;
        }
        if (cellSize > 1 && footprint.minimumDepth <= SHORE_DETAIL_DEPTH) {
            subdivideShorePatch(level, columns, surfaces, rebuiltOwnedTops, x, z, cellSize);
            return;
        }

        PATCHES.add(new SurfacePatch(
                x,
                z,
                first.surfaceY,
                second.surfaceY,
                third.surfaceY,
                fourth.surfaceY,
                cellSize,
                footprint.minimumDepth,
                dominantType(first, second, third, fourth)
        ));

        // Only one-block patches truly replace vanilla top faces. Coarser LOD
        // patches are visual overlays; hiding the baked water beneath them
        // exposes seafloor/ice intersections as large transparent triangles.
        if (cellSize == 1) {
            for (int offsetX = 0; offsetX < cellSize; offsetX++) {
                for (int offsetZ = 0; offsetZ < cellSize; offsetZ++) {
                    SurfaceColumn covered = surfaceColumn(level, surfaces, x + offsetX, z + offsetZ);
                    if (covered.valid) {
                        rebuiltOwnedTops.add(BlockPos.asLong(
                                x + offsetX,
                                covered.surfaceBlockY,
                                z + offsetZ
                        ));
                    }
                }
            }
        }
    }

    // Coarse quads are appropriate for deep, visually smooth ocean water. At
    // shorelines they become non-planar and the GPU's two triangle halves can
    // expose visibly different sand-colored regions. Unit patches preserve the
    // exact block shoreline and keep each diagonal too small to notice.
    private static void subdivideShorePatch(
            ClientLevel level,
            Map<Long, WaterColumn> columns,
            Map<Long, SurfaceColumn> surfaces,
            LongOpenHashSet rebuiltOwnedTops,
            int startX,
            int startZ,
            int cellSize
    ) {
        if (cellSize <= 1) {
            return;
        }
        for (int x = startX; x < startX + cellSize; x++) {
            for (int z = startZ; z < startZ + cellSize; z++) {
                addPatch(level, columns, surfaces, rebuiltOwnedTops, x, z, 1);
            }
        }
    }

    // Coarse optimized cells must never bridge an island, beach corner, or
    // unloaded gap merely because their four outer corners contain water.
    // Validate the complete vertex grid and use its shallowest depth so wave
    // attenuation remains conservative near irregular shorelines.
    private static PatchFootprint validatePatchFootprint(
            ClientLevel level,
            Map<Long, SurfaceColumn> surfaces,
            int startX,
            int startZ,
            int cellSize,
            float sampledMinimumDepth
    ) {
        float minimumSurface = Float.POSITIVE_INFINITY;
        float maximumSurface = Float.NEGATIVE_INFINITY;
        for (int offsetX = 0; offsetX <= cellSize; offsetX++) {
            for (int offsetZ = 0; offsetZ <= cellSize; offsetZ++) {
                SurfaceColumn covered = surfaceColumn(
                        level,
                        surfaces,
                        startX + offsetX,
                        startZ + offsetZ
                );
                if (!covered.valid) {
                    return PatchFootprint.INVALID;
                }
                minimumSurface = Math.min(minimumSurface, covered.surfaceY);
                maximumSurface = Math.max(maximumSurface, covered.surfaceY);
            }
        }
        if (maximumSurface - minimumSurface > MAX_SURFACE_STEP) {
            return PatchFootprint.INVALID;
        }
        return new PatchFootprint(true, sampledMinimumDepth);
    }

    private static WaterColumn column(
            ClientLevel level,
            Map<Long, WaterColumn> columns,
            Map<Long, SurfaceColumn> surfaces,
            int x,
            int z
    ) {
        long key = ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
        return columns.computeIfAbsent(key, ignored -> scanColumn(
                level,
                x,
                z,
                surfaceColumn(level, surfaces, x, z)
        ));
    }

    private static SurfaceColumn surfaceColumn(
            ClientLevel level,
            Map<Long, SurfaceColumn> surfaces,
            int x,
            int z
    ) {
        long key = ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
        return surfaces.computeIfAbsent(key, ignored -> scanSurfaceColumn(level, x, z));
    }

    // Full view-distance coverage needs only exposed-surface checks. Expensive
    // bathymetry and water-body classification are evaluated at mesh vertices,
    // not for every interior block hidden beneath an LOD patch.
    private static SurfaceColumn scanSurfaceColumn(ClientLevel level, int x, int z) {
        int surfaceBlockY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        pos.set(x, surfaceBlockY, z);
        if (!level.hasChunkAt(pos)) {
            return SurfaceColumn.INVALID;
        }

        BlockState surfaceState = level.getBlockState(pos);
        FluidState surfaceFluid = surfaceState.getFluidState();
        var canonicalCell = CanonicalWater.getTracked(level, pos);
        if (!isRenderableWaterSurface(surfaceState, surfaceFluid, canonicalCell)
                || isSurfaceCovered(level, above.set(x, surfaceBlockY + 1, z))) {
            return SurfaceColumn.INVALID;
        }

        return new SurfaceColumn(
                true,
                surfaceBlockY,
                surfaceBlockY + (canonicalCell != null
                        ? canonicalCell.fillFraction()
                        : surfaceFluid.getOwnHeight()) + 0.001f
        );
    }

    // The dynamic mesh may hide vanilla water tops, so only true exposed water
    // blocks or tracked canonical cells are allowed to become replacement
    // surface anchors. Waterlogged plants and decorations keep vanilla rendering
    // because their fluid state is not an open water surface.
    private static boolean isRenderableWaterSurface(
            BlockState surfaceState,
            FluidState surfaceFluid,
            WaterVolumeChunk.WaterCell canonicalCell
    ) {
        if (canonicalCell != null) {
            return canonicalCell.volumeUnits() > 0;
        }
        return surfaceState.is(Blocks.WATER) && surfaceFluid.is(Fluids.WATER);
    }

    // Covered water under ice, lily pads, solid blocks, or another water cell
    // is not visually exposed. Leaving vanilla/collision-owned columns alone
    // avoids the black, angular floor patches seen in frozen shorelines.
    private static boolean isSurfaceCovered(ClientLevel level, BlockPos pos) {
        FluidState aboveFluid = level.getFluidState(pos);
        if (aboveFluid.is(Fluids.WATER)) {
            return true;
        }
        var aboveCanonicalCell = CanonicalWater.getTracked(level, pos);
        if (aboveCanonicalCell != null && aboveCanonicalCell.volumeUnits() > 0) {
            return true;
        }
        BlockState aboveState = level.getBlockState(pos);
        return !aboveState.getCollisionShape(level, pos).isEmpty();
    }

    private static WaterColumn scanColumn(
            ClientLevel level,
            int x,
            int z,
            SurfaceColumn surface
    ) {
        if (!surface.valid) {
            return WaterColumn.INVALID;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        pos.set(x, surface.surfaceBlockY, z);
        WaterBodyClassifier.WaterType waterType = WaterBodyClassifier.classify(level, pos);
        float depth = MAX_DEPTH_SAMPLE;
        for (int offset = 1; offset <= MAX_DEPTH_SAMPLE; offset++) {
            pos.set(x, surface.surfaceBlockY - offset, z);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                depth = offset;
                break;
            }
        }
        return new WaterColumn(
                true,
                surface.surfaceBlockY,
                surface.surfaceY,
                depth,
                waterType
        );
    }

    private static int waterLight(ClientLevel level, float x, float y, float z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!level.hasChunkAt(pos)) {
            return LightTexture.FULL_BRIGHT;
        }
        int packed = LevelRenderer.getLightColor(level, pos);
        return LightTexture.pack(
                Math.max(8, LightTexture.block(packed)),
                Math.max(12, LightTexture.sky(packed))
        );
    }

    // Classification changes wave behavior only; coverage remains continuous
    // across biome and cache-cell boundaries.
    private static WaterBodyClassifier.WaterType dominantType(
            WaterColumn first,
            WaterColumn second,
            WaterColumn third,
            WaterColumn fourth
    ) {
        int ocean = 0;
        int river = 0;
        int pond = 0;
        for (WaterColumn column : new WaterColumn[]{first, second, third, fourth}) {
            switch (column.waterType) {
                case OCEAN -> ocean++;
                case RIVER -> river++;
                case POND -> pond++;
            }
        }
        if (ocean >= river && ocean >= pond) {
            return WaterBodyClassifier.WaterType.OCEAN;
        }
        return river >= pond
                ? WaterBodyClassifier.WaterType.RIVER
                : WaterBodyClassifier.WaterType.POND;
    }

    private static GerstnerWaveProfile profileFor(WaterBodyClassifier.WaterType waterType) {
        return switch (waterType) {
            case OCEAN -> GerstnerWaveProfile.OCEAN;
            case RIVER -> GerstnerWaveProfile.RIVER;
            case POND -> GerstnerWaveProfile.POND;
        };
    }

    private static void clearCache() {
        PATCHES.clear();
        ownedVanillaTops = LongSets.EMPTY_SET;
        cachedLevel = null;
        cachedCenterX = Integer.MIN_VALUE;
        cachedCenterZ = Integer.MIN_VALUE;
        cachedNearRadius = -1;
        cachedFarRadius = -1;
        cachedNearCellSize = -1;
        cachedGameTime = Long.MIN_VALUE;
    }

    /** Clears top-face ownership before an old client level is discarded. */
    public static void clearLevel(ClientLevel level) {
        if (cachedLevel == level) {
            clearCache();
        }
    }

    /** Returns whether the per-frame mesh currently replaces this baked top face. */
    public static boolean ownsBakedTop(BlockPos pos) {
        return ownedVanillaTops.contains(pos.asLong());
    }

    // Rebuild only chunk sections whose top-face ownership changed. The set is
    // immutable so chunk compilation workers can read it without locking.
    private static void updateVanillaTopOwnership(ClientLevel level, LongSet rebuiltOwnership) {
        LongSet next = LongSets.unmodifiable(rebuiltOwnership);
        LongSet previous = ownedVanillaTops;
        if (previous.equals(next)) {
            return;
        }
        ownedVanillaTops = next;

        LongOpenHashSet changed = new LongOpenHashSet(previous);
        for (long packedPos : next) {
            if (!changed.add(packedPos)) {
                changed.remove(packedPos);
            }
        }
        markSectionsDirty(level, changed);
    }

    private static void releaseVanillaTopOwnership(ClientLevel level) {
        LongSet previous = ownedVanillaTops;
        if (previous.isEmpty()) {
            return;
        }
        ownedVanillaTops = LongSets.EMPTY_SET;
        PATCHES.clear();
        if (level != null) {
            markSectionsDirty(level, previous);
        }
    }

    private static void markSectionsDirty(ClientLevel level, LongSet changedPositions) {
        LongOpenHashSet dirtySections = new LongOpenHashSet();
        for (long packedPos : changedPositions) {
            dirtySections.add(SectionPos.asLong(BlockPos.of(packedPos)));
        }

        var levelRenderer = Minecraft.getInstance().levelRenderer;
        for (long packedSection : dirtySections) {
            levelRenderer.setSectionDirty(
                    SectionPos.x(packedSection),
                    SectionPos.y(packedSection),
                    SectionPos.z(packedSection)
            );
        }
    }

    private static float smoothStep(float edge0, float edge1, float value) {
        float t = Math.max(0.0f, Math.min(1.0f, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float mix(float first, float second, float factor) {
        return first + (second - first) * Math.max(0.0f, Math.min(1.0f, factor));
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0f)));
    }

    private static float tile(float value) {
        return value - (float) Math.floor(value);
    }

    private record SurfacePatch(
            int x,
            int z,
            float firstY,
            float secondY,
            float thirdY,
            float fourthY,
            int size,
            float depth,
            WaterBodyClassifier.WaterType waterType
    ) {
    }

    private record WaterColumn(
            boolean valid,
            int surfaceBlockY,
            float surfaceY,
            float depth,
            WaterBodyClassifier.WaterType waterType
    ) {
        private static final WaterColumn INVALID = new WaterColumn(
                false,
                0,
                0.0f,
                0.0f,
                WaterBodyClassifier.WaterType.POND
        );
    }

    private record SurfaceColumn(boolean valid, int surfaceBlockY, float surfaceY) {
        private static final SurfaceColumn INVALID = new SurfaceColumn(false, 0, 0.0f);
    }

    private record PatchFootprint(boolean valid, float minimumDepth) {
        private static final PatchFootprint INVALID = new PatchFootprint(false, 0.0f);
    }

    private record VertexData(
            float x,
            float y,
            float z,
            float normalX,
            float normalY,
            float normalZ,
            int color,
            int light
    ) {
    }
}
