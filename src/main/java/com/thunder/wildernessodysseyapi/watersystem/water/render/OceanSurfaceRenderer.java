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
    private static final int MAX_DEPTH_SAMPLE = 16;
    private static final int SHORE_DETAIL_DEPTH = 12;
    private static final int CONTINUITY_BORDER = 1;
    private static final float MAX_SURFACE_STEP = 0.05f;
    private static final float UV_SCALE = 0.28f;
    private static final float VISUAL_TIDE_SCALE = 0.18f;

    private static final List<SurfacePatch> PATCHES = new ArrayList<>();
    private static volatile LongSet ownedVanillaTops = LongSets.EMPTY_SET;
    private static volatile LongSet oceanOwnedVanillaTops = LongSets.EMPTY_SET;
    private static volatile LongSet supplementalOwnedVanillaTops = LongSets.EMPTY_SET;
    private static ClientLevel cachedLevel;
    private static int cachedCenterX = Integer.MIN_VALUE;
    private static int cachedCenterZ = Integer.MIN_VALUE;
    private static int cachedNearRadius = -1;
    private static int cachedFarRadius = -1;
    private static int cachedNearCellSize = -1;
    private static int cachedMaxPatches = -1;
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
                nearCellSize,
                WaterRenderingConfig.maxOceanSurfacePatches()
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
        float waveBlend = 0.25f + smoothStep(0.35f, 4.0f, patch.depth) * 0.75f;
        int waveLimit = WaterRenderingConfig.waveTrainLimit(patch.waterType);
        GerstnerWaveProfile waveProfile = profileFor(patch.waterType);
        float localTideOffset = patch.waterType == WaterBodyClassifier.WaterType.OCEAN
                ? tideOffset
                : 0.0f;
        float x0 = patch.x;
        float z0 = patch.z;
        float x1 = patch.x + patch.size;
        float z1 = patch.z + patch.size;
        float centerX = (x0 + x1) * 0.5f;
        float centerZ = (z0 + z1) * 0.5f;
        float alphaScale = patchAlphaScale(patch, centerX, centerZ, cameraX, cameraZ, nearRadius, farRadius);
        if (alphaScale <= 0.01f) {
            return;
        }

        WaveSpectrumState spectrum = patch.waterType == WaterBodyClassifier.WaterType.OCEAN
                ? seaState.spectrum()
                : WaveSpectrumState.NEUTRAL;
        int patchColor = opticalColor(
                level,
                centerX,
                (patch.firstY + patch.secondY + patch.thirdY + patch.fourthY) * 0.25f,
                centerZ,
                patch.depth,
                alphaScale
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
        // Full Gerstner orbital motion assumes a continuous ocean sheet. The
        // Minecraft replacement mesh is clipped per block by shores, ice, and
        // compatibility water, so horizontal displacement can pull boundary
        // vertices across missing neighbors and expose triangular gaps. Keep
        // this render mesh height-only; entity physics still uses orbital
        // velocity from the same wave sample.
        float x = baseX;
        float transientDisplacement = WaterSurfaceDisplacement.sampleHeight(
                level,
                baseX,
                baseZ,
                timeSeconds * 20.0f
        );
        float y = baseY + sample.height() + transientDisplacement + 0.002f;
        float z = baseZ;
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
            float depth,
            float alphaScale
    ) {
        int tint = IClientFluidTypeExtensions.of(Fluids.WATER).getTintColor(
                WATER_STATE,
                level,
                BlockPos.containing(x, y, z)
        );
        float tintR = ((tint >> 16) & 0xFF) / 255.0f;
        float tintG = ((tint >> 8) & 0xFF) / 255.0f;
        float tintB = (tint & 0xFF) / 255.0f;

        float absorption = 1.0f - (float) Math.exp(
                -Math.max(0.0f, depth) * 0.32f * WaterRenderingConfig.surfaceAbsorptionStrength()
        );

        // Keep the replacement surface optically water-colored even over dark
        // ocean floors. The shader still applies depth, light, and Fresnel, but
        // the base medium should not become black transparent terrain cutouts.
        float shallowR = 0.06f + tintR * 0.32f;
        float shallowG = 0.56f + tintG * 0.24f;
        float shallowB = 0.82f + tintB * 0.16f;
        float deepR = 0.015f + tintR * 0.12f;
        float deepG = 0.20f + tintG * 0.18f;
        float deepB = 0.44f + tintB * 0.32f;
        float red = mix(shallowR, deepR, absorption);
        float green = mix(shallowG, deepG, absorption);
        float blue = mix(shallowB, deepB, absorption);

        float foam = smoothStep(0.0f, 0.8f, 1.15f - depth);
        red = mix(red, 0.86f, foam);
        green = mix(green, 0.94f, foam);
        blue = mix(blue, 1.0f, foam);
        float baseAlpha = WaterRenderingConfig.suppressVanillaWaterTopFaces() ? 0.76f : 0.50f;
        float depthAlpha = WaterRenderingConfig.suppressVanillaWaterTopFaces() ? 0.22f : 0.12f;
        float alpha = (baseAlpha + absorption * depthAlpha + foam * 0.05f)
                * WaterRenderingConfig.surfaceOpacityStrength()
                * Math.max(0.0f, Math.min(1.0f, alphaScale));

        return (channel(alpha) << 24)
                | (channel(red) << 16)
                | (channel(green) << 8)
                | channel(blue);
    }

    private static float patchAlphaScale(
            SurfacePatch patch,
            float centerX,
            float centerZ,
            float cameraX,
            float cameraZ,
            int nearRadius,
            int farRadius
    ) {
        float distanceX = centerX - cameraX;
        float distanceZ = centerZ - cameraZ;
        float distance = (float) Math.sqrt(distanceX * distanceX + distanceZ * distanceZ);
        boolean replacingVanillaTop = WaterRenderingConfig.suppressVanillaWaterTopFaces();
        float edgeFade = farRadius <= nearRadius || replacingVanillaTop
                ? 1.0f
                : 1.0f - smoothStep(Math.max(nearRadius, farRadius - 24.0f), farRadius, distance);
        // In strict replacement mode, LOD patches are the visible ocean, not a
        // faint overlay over Minecraft water. Keep them optically dense enough
        // that the hidden vanilla top does not reveal cave-like seafloor shapes.
        float lodFade = patch.size <= 1
                ? 1.0f
                : replacingVanillaTop
                        ? patch.size <= 2 ? 0.92f : 0.84f
                        : patch.size <= 2 ? 0.58f : 0.42f;
        return Math.max(0.0f, Math.min(1.0f, edgeFade * lodFade));
    }

    private static void refreshCacheIfNeeded(
            ClientLevel level,
            int cameraX,
            int cameraZ,
            int nearRadius,
            int farRadius,
            int nearCellSize,
            int maxPatches
    ) {
        long gameTime = level.getGameTime();
        int maxDynamicCellSize = Math.max(4, WaterRenderingConfig.dynamicOceanMaxCellSize());
        int movementThreshold = Math.max(8, maxDynamicCellSize * 2);
        int cacheLifetimeTicks = Math.max(1, WaterRenderingConfig.dynamicOceanCacheLifetimeTicks());
        boolean stale = cachedLevel != level
                || nearRadius != cachedNearRadius
                || farRadius != cachedFarRadius
                || nearCellSize != cachedNearCellSize
                || maxPatches != cachedMaxPatches
                || Math.abs(cameraX - cachedCenterX) >= movementThreshold
                || Math.abs(cameraZ - cachedCenterZ) >= movementThreshold
                || gameTime - cachedGameTime >= cacheLifetimeTicks;
        if (!stale) {
            return;
        }

        cachedLevel = level;
        cachedCenterX = cameraX;
        cachedCenterZ = cameraZ;
        cachedNearRadius = nearRadius;
        cachedFarRadius = farRadius;
        cachedNearCellSize = nearCellSize;
        cachedMaxPatches = maxPatches;
        cachedGameTime = gameTime;
        PATCHES.clear();

        Map<Long, WaterColumn> columns = new HashMap<>();
        Map<Long, SurfaceColumn> surfaces = new HashMap<>();
        LongOpenHashSet rebuiltOwnedTops = new LongOpenHashSet();
        int mediumRadius = Math.min(farRadius, Math.max(nearRadius, nearRadius * 2));
        int boundedPatchBudget = Math.max(1, maxPatches);
        // The high-detail ring respects the active quality profile. Medium and
        // far rings are coarsened aggressively so render-distance coverage
        // follows the player's settings without rebuilding block-resolution
        // oceans across the whole visible radius.
        int detailCellSize = Math.max(1, nearCellSize);
        int mediumCellSize = Math.min(maxDynamicCellSize, Math.max(2, nearCellSize * 2));
        int farCellSize = Math.min(maxDynamicCellSize, Math.max(4, nearCellSize * 4));
        int paddedFarRadius = farRadius + farCellSize;
        int paddedFarRadiusSquared = paddedFarRadius * paddedFarRadius;
        int centerCoarseX = Math.floorDiv(cameraX, farCellSize) * farCellSize;
        int centerCoarseZ = Math.floorDiv(cameraZ, farCellSize) * farCellSize;
        int gridRadius = Math.floorDiv(farRadius + farCellSize - 1, farCellSize) + 1;

        // Every far cell is either kept coarse or subdivided completely. That
        // keeps the LOD rings world-aligned with no overlapping or uncovered
        // cells in the cached footprint. Patch budgets are a final FPS guard
        // for extreme configs or unusually expensive shore/ocean borders. The
        // cache fills nearest-first so hitting the budget fades outward instead
        // of cutting a hard rectangular seam through the view.
        for (int ring = 0; ring <= gridRadius && PATCHES.size() < boundedPatchBudget; ring++) {
            if (ring == 0) {
                addLodCoarseCell(level, columns, surfaces, rebuiltOwnedTops,
                        centerCoarseX, centerCoarseZ, cameraX, cameraZ,
                        nearRadius, mediumRadius, farCellSize, detailCellSize, mediumCellSize,
                        paddedFarRadiusSquared, boundedPatchBudget);
                continue;
            }

            int minX = centerCoarseX - ring * farCellSize;
            int maxX = centerCoarseX + ring * farCellSize;
            int minZ = centerCoarseZ - ring * farCellSize;
            int maxZ = centerCoarseZ + ring * farCellSize;

            for (int coarseX = minX; coarseX <= maxX && PATCHES.size() < boundedPatchBudget; coarseX += farCellSize) {
                addLodCoarseCell(level, columns, surfaces, rebuiltOwnedTops,
                        coarseX, minZ, cameraX, cameraZ,
                        nearRadius, mediumRadius, farCellSize, detailCellSize, mediumCellSize,
                        paddedFarRadiusSquared, boundedPatchBudget);
                if (minZ != maxZ) {
                    addLodCoarseCell(level, columns, surfaces, rebuiltOwnedTops,
                            coarseX, maxZ, cameraX, cameraZ,
                            nearRadius, mediumRadius, farCellSize, detailCellSize, mediumCellSize,
                            paddedFarRadiusSquared, boundedPatchBudget);
                }
            }

            for (int coarseZ = minZ + farCellSize;
                    coarseZ < maxZ && PATCHES.size() < boundedPatchBudget;
                    coarseZ += farCellSize) {
                addLodCoarseCell(level, columns, surfaces, rebuiltOwnedTops,
                        minX, coarseZ, cameraX, cameraZ,
                        nearRadius, mediumRadius, farCellSize, detailCellSize, mediumCellSize,
                        paddedFarRadiusSquared, boundedPatchBudget);
                if (minX != maxX) {
                    addLodCoarseCell(level, columns, surfaces, rebuiltOwnedTops,
                            maxX, coarseZ, cameraX, cameraZ,
                            nearRadius, mediumRadius, farCellSize, detailCellSize, mediumCellSize,
                            paddedFarRadiusSquared, boundedPatchBudget);
                }
            }
        }
        if (WaterRenderingConfig.suppressVanillaWaterTopFaces()) {
            updateVanillaTopOwnership(level, rebuiltOwnedTops);
        } else {
            releaseVanillaTopOwnership(level);
        }
    }

    private static void addLodCoarseCell(
            ClientLevel level,
            Map<Long, WaterColumn> columns,
            Map<Long, SurfaceColumn> surfaces,
            LongOpenHashSet rebuiltOwnedTops,
            int coarseX,
            int coarseZ,
            int cameraX,
            int cameraZ,
            int nearRadius,
            int mediumRadius,
            int farCellSize,
            int detailCellSize,
            int mediumCellSize,
            int paddedFarRadiusSquared,
            int maxPatches
    ) {
        if (PATCHES.size() >= maxPatches) {
            return;
        }
        int dx = coarseX + farCellSize / 2 - cameraX;
        int dz = coarseZ + farCellSize / 2 - cameraZ;
        int distanceSquared = dx * dx + dz * dz;
        if (distanceSquared > paddedFarRadiusSquared) {
            return;
        }

        int selectedCellSize = distanceSquared <= nearRadius * nearRadius
                ? detailCellSize
                : distanceSquared <= mediumRadius * mediumRadius
                        ? mediumCellSize
                        : farCellSize;
        for (int x = coarseX; x < coarseX + farCellSize; x += selectedCellSize) {
            for (int z = coarseZ; z < coarseZ + farCellSize; z += selectedCellSize) {
                if (PATCHES.size() >= maxPatches) {
                    return;
                }
                addPatch(level, columns, surfaces, rebuiltOwnedTops, x, z,
                        selectedCellSize, maxPatches);
            }
        }
    }

    private static void addPatch(
            ClientLevel level,
            Map<Long, WaterColumn> columns,
            Map<Long, SurfaceColumn> surfaces,
            LongOpenHashSet rebuiltOwnedTops,
            int x,
            int z,
            int cellSize,
            int maxPatches
    ) {
        if (PATCHES.size() >= maxPatches) {
            return;
        }
        WaterColumn first = column(level, columns, surfaces, x, z);
        WaterColumn second = column(level, columns, surfaces, x, z + cellSize);
        WaterColumn third = column(level, columns, surfaces, x + cellSize, z + cellSize);
        WaterColumn fourth = column(level, columns, surfaces, x + cellSize, z);
        if (!first.valid || !second.valid || !third.valid || !fourth.valid) {
            subdivideShorePatch(level, columns, surfaces, rebuiltOwnedTops, x, z, cellSize, maxPatches);
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
            subdivideShorePatch(level, columns, surfaces, rebuiltOwnedTops, x, z, cellSize, maxPatches);
            return;
        }
        if (!footprint.replacementSafe && cellSize > 1) {
            subdivideShorePatch(level, columns, surfaces, rebuiltOwnedTops, x, z, cellSize, maxPatches);
            return;
        }
        if (cellSize > 1 && footprint.minimumDepth <= SHORE_DETAIL_DEPTH) {
            subdivideShorePatch(level, columns, surfaces, rebuiltOwnedTops, x, z, cellSize, maxPatches);
            return;
        }

        PATCHES.add(new SurfacePatch(
                x,
                z,
                footprint.surfaceY,
                footprint.surfaceY,
                footprint.surfaceY,
                footprint.surfaceY,
                cellSize,
                footprint.minimumDepth,
                dominantType(first, second, third, fourth)
        ));

        // Strict visual replacement: validated open-water footprints own the
        // visible top face across both block-detail and coarse LOD patches.
        // Vanilla fluid blocks remain in-world for tags and compatibility, but
        // their top surface should not be the thing the player sees.
        if (WaterRenderingConfig.suppressVanillaWaterTopFaces()) {
            for (int offsetX = 0; offsetX < cellSize; offsetX++) {
                for (int offsetZ = 0; offsetZ < cellSize; offsetZ++) {
                    SurfaceColumn covered = surfaceColumn(level, surfaces, x + offsetX, z + offsetZ);
                    if (covered.valid && covered.replacementSafe) {
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
            int cellSize,
            int maxPatches
    ) {
        if (cellSize <= 1) {
            return;
        }
        for (int x = startX; x < startX + cellSize; x++) {
            for (int z = startZ; z < startZ + cellSize; z++) {
                if (PATCHES.size() >= maxPatches) {
                    return;
                }
                addPatch(level, columns, surfaces, rebuiltOwnedTops, x, z, 1, maxPatches);
            }
        }
    }

    // Dynamic geometry treats vanilla water as a migration mask, not as final
    // mesh data. Coarse patches still require fully replacement-safe ownership;
    // pending migration cells may render only after subdivision to one-block
    // previews so the Wilderness surface stays visible while authority catches
    // up without stretching unsafe quads over shore or covered columns.
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
        boolean replacementSafe = true;
        for (int offsetX = -CONTINUITY_BORDER; offsetX <= cellSize + CONTINUITY_BORDER; offsetX++) {
            for (int offsetZ = -CONTINUITY_BORDER; offsetZ <= cellSize + CONTINUITY_BORDER; offsetZ++) {
                SurfaceColumn covered = surfaceColumn(
                        level,
                        surfaces,
                        startX + offsetX,
                        startZ + offsetZ
                );
                if (!covered.valid) {
                    return PatchFootprint.INVALID;
                }
                replacementSafe &= covered.replacementSafe;
                if (offsetX >= 0 && offsetX <= cellSize && offsetZ >= 0 && offsetZ <= cellSize) {
                    minimumSurface = Math.min(minimumSurface, covered.surfaceY);
                    maximumSurface = Math.max(maximumSurface, covered.surfaceY);
                }
            }
        }
        if (maximumSurface - minimumSurface > MAX_SURFACE_STEP) {
            return PatchFootprint.INVALID;
        }
        return new PatchFootprint(true, replacementSafe, sampledMinimumDepth, (minimumSurface + maximumSurface) * 0.5f);
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
        ClientWaterColumnSampler.ColumnSample sample = ClientWaterColumnSampler.sampleExposedSurface(
                level,
                x,
                z,
                MAX_DEPTH_SAMPLE,
                0.001f
        );
        if (!sample.valid()) {
            return SurfaceColumn.INVALID;
        }

        return new SurfaceColumn(
                true,
                sample.surfaceBlockY(),
                sample.surfaceY(),
                sample.replacementSafe(),
                sample.depth()
        );
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
        return new WaterColumn(
                true,
                surface.surfaceBlockY,
                surface.surfaceY,
                surface.depth,
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
        oceanOwnedVanillaTops = LongSets.EMPTY_SET;
        supplementalOwnedVanillaTops = LongSets.EMPTY_SET;
        cachedLevel = null;
        cachedCenterX = Integer.MIN_VALUE;
        cachedCenterZ = Integer.MIN_VALUE;
        cachedNearRadius = -1;
        cachedFarRadius = -1;
        cachedNearCellSize = -1;
        cachedMaxPatches = -1;
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
        return WaterRenderingConfig.suppressVanillaWaterTopFaces()
                && ownedVanillaTops.contains(pos.asLong());
    }

    /**
     * Adds ownership from local shoreline/detail renderers into the same culling
     * set used by the open-ocean mesh.
     *
     * <p>The mixin that hides vanilla top faces has one source of truth:
     * {@link #ownedVanillaTops}. Shoreline rendering runs after the open-ocean
     * pass, so it publishes a supplemental set here instead of owning another
     * independent culling path.</p>
     */
    public static void setSupplementalBakedTopOwnership(ClientLevel level, LongSet supplementalOwnership) {
        LongSet next = supplementalOwnership.isEmpty()
                ? LongSets.EMPTY_SET
                : LongSets.unmodifiable(new LongOpenHashSet(supplementalOwnership));
        if (supplementalOwnedVanillaTops.equals(next)) {
            return;
        }
        supplementalOwnedVanillaTops = next;
        publishVanillaTopOwnership(level);
    }

    // Rebuild only chunk sections whose top-face ownership changed. The merged
    // set is immutable so chunk compilation workers can read it without locking.
    private static void updateVanillaTopOwnership(ClientLevel level, LongSet rebuiltOwnership) {
        oceanOwnedVanillaTops = rebuiltOwnership.isEmpty()
                ? LongSets.EMPTY_SET
                : LongSets.unmodifiable(rebuiltOwnership);
        publishVanillaTopOwnership(level);
    }

    private static void publishVanillaTopOwnership(ClientLevel level) {
        LongSet next = mergeTopOwnership(oceanOwnedVanillaTops, supplementalOwnedVanillaTops);
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
        if (level != null && !changed.isEmpty()) {
            markSectionsDirty(level, changed);
        }
    }

    private static LongSet mergeTopOwnership(LongSet oceanOwnership, LongSet supplementalOwnership) {
        if (oceanOwnership.isEmpty()) {
            return supplementalOwnership;
        }
        if (supplementalOwnership.isEmpty()) {
            return oceanOwnership;
        }
        LongOpenHashSet merged = new LongOpenHashSet(oceanOwnership);
        merged.addAll(supplementalOwnership);
        return LongSets.unmodifiable(merged);
    }

    private static void releaseVanillaTopOwnership(ClientLevel level) {
        LongSet previous = ownedVanillaTops;
        if (previous.isEmpty()) {
            return;
        }
        ownedVanillaTops = LongSets.EMPTY_SET;
        oceanOwnedVanillaTops = LongSets.EMPTY_SET;
        supplementalOwnedVanillaTops = LongSets.EMPTY_SET;
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

    private record SurfaceColumn(
            boolean valid,
            int surfaceBlockY,
            float surfaceY,
            boolean replacementSafe,
            float depth
    ) {
        private static final SurfaceColumn INVALID = new SurfaceColumn(false, 0, 0.0f, false, 0.0f);
    }

    private record PatchFootprint(boolean valid, boolean replacementSafe, float minimumDepth, float surfaceY) {
        private static final PatchFootprint INVALID = new PatchFootprint(false, false, 0.0f, 0.0f);
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
