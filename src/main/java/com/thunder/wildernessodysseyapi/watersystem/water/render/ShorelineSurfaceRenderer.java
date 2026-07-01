package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thunder.wildernessodysseyapi.gpuprofiler.client.GpuDiagnostics;
import com.thunder.wildernessodysseyapi.watersystem.ocean.ClientOceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveProfile;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSpectrumState;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSurfaceSample;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.textures.FluidSpriteCache;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the block-detail water overlay for unstable local water edges.
 *
 * <p>The open-ocean renderer intentionally refuses shore, ice-adjacent,
 * flowing, partial, or otherwise discontinuous cells. This renderer fills that
 * visual gap with one-block, laterally anchored quads that never hide vanilla
 * water. Vanilla remains the compatibility mask while this layer adds local
 * color, small vertical motion, and foam-like edge brightness.</p>
 */
@EventBusSubscriber(modid = "wildernessodysseyapi", value = Dist.CLIENT)
public final class ShorelineSurfaceRenderer {

    private static final FluidState WATER_STATE = Fluids.WATER.defaultFluidState();
    private static final int CACHE_LIFETIME_TICKS = 20;
    private static final int MAX_RENDER_RADIUS_BLOCKS = 96;
    private static final int MAX_DEPTH_SAMPLE = 8;
    private static final float SURFACE_OFFSET = 0.006f;
    private static final float UV_SCALE = 0.28f;
    private static final float VISUAL_TIDE_SCALE = 0.18f;

    private static final List<ShorePatch> PATCHES = new ArrayList<>();
    private static ClientLevel cachedLevel;
    private static int cachedCenterX = Integer.MIN_VALUE;
    private static int cachedCenterZ = Integer.MIN_VALUE;
    private static int cachedRadius = -1;
    private static long cachedGameTime = Long.MIN_VALUE;

    private ShorelineSurfaceRenderer() {
    }

    /** Renders local water detail after vanilla translucent terrain. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!WaterRenderingConfig.ENABLE_GERSTNER_WAVES.get()
                || !WaterRenderingConfig.ENABLE_DYNAMIC_OCEAN_SURFACE.get()
                || !WaterRenderingConfig.ENABLE_SHORELINE_SURFACE.get()) {
            clearCache();
            return;
        }

        try (GpuDiagnostics.Scope ignored = GpuDiagnostics.scope("water.surface.shoreline")) {
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
        int radius = Math.min(MAX_RENDER_RADIUS_BLOCKS, WaterRenderingConfig.shorelineRenderDistanceBlocks());
        refreshCacheIfNeeded(level, (int) Math.floor(camera.x), (int) Math.floor(camera.z), radius);
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

        float shorelineStrength = Math.max(0.0f, WaterRenderingConfig.shorelineOverlayStrength());
        for (ShorePatch patch : PATCHES) {
            drawPatch(level, patch, timeSeconds, tideOffset, seaState, sprite, poseStack.last(), buffer,
                    (float) camera.x, (float) camera.z, radius, shorelineStrength);
        }

        poseStack.popPose();
        bufferSource.endBatch(renderType);
    }

    private static void refreshCacheIfNeeded(ClientLevel level, int cameraX, int cameraZ, int radius) {
        long gameTime = level.getGameTime();
        boolean stale = cachedLevel != level
                || radius != cachedRadius
                || Math.abs(cameraX - cachedCenterX) >= 4
                || Math.abs(cameraZ - cachedCenterZ) >= 4
                || gameTime - cachedGameTime >= CACHE_LIFETIME_TICKS;
        if (!stale) {
            return;
        }

        cachedLevel = level;
        cachedCenterX = cameraX;
        cachedCenterZ = cameraZ;
        cachedRadius = radius;
        cachedGameTime = gameTime;
        PATCHES.clear();

        int radiusSquared = radius * radius;
        for (int x = cameraX - radius; x <= cameraX + radius; x++) {
            for (int z = cameraZ - radius; z <= cameraZ + radius; z++) {
                int dx = x - cameraX;
                int dz = z - cameraZ;
                if (dx * dx + dz * dz > radiusSquared) {
                    continue;
                }
                addPatchIfLocalEdge(level, x, z);
            }
        }
    }

    private static void addPatchIfLocalEdge(ClientLevel level, int x, int z) {
        SurfaceColumn surface = surfaceColumn(level, x, z);
        if (!surface.valid) {
            return;
        }
        if (OceanSurfaceRenderer.ownsBakedTop(new BlockPos(x, surface.surfaceBlockY, z))) {
            return;
        }

        EdgeSample edge = edgeSample(level, x, z, surface);
        if (!edge.localEdge && surface.fullWater) {
            return;
        }

        PATCHES.add(new ShorePatch(
                x,
                z,
                surface.surfaceY,
                surface.depth,
                WaterBodyClassifier.classify(level, new BlockPos(x, surface.surfaceBlockY, z)),
                edge.edgeStrength,
                surface.fullWater,
                surface.fillFraction,
                surface.velocityX,
                surface.velocityZ
        ));
    }

    private static EdgeSample edgeSample(ClientLevel level, int x, int z, SurfaceColumn surface) {
        int missingOrUneven = 0;
        float fillMismatch = 0.0f;
        float velocityMismatch = 0.0f;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            SurfaceColumn neighbour = surfaceColumn(level, x + direction.getStepX(), z + direction.getStepZ());
            if (!neighbour.valid
                    || !neighbour.fullWater
                    || neighbour.surfaceBlockY != surface.surfaceBlockY
                    || Math.abs(neighbour.surfaceY - surface.surfaceY) > 0.05f) {
                missingOrUneven++;
            }
            if (neighbour.valid) {
                fillMismatch += Math.abs(neighbour.fillFraction - surface.fillFraction);
                velocityMismatch += Math.abs(neighbour.velocityX - surface.velocityX)
                        + Math.abs(neighbour.velocityZ - surface.velocityZ);
            }
        }
        float edgeStrength = missingOrUneven / 3.0f
                + fillMismatch * 0.18f
                + Math.min(0.35f, velocityMismatch * 0.08f);
        return new EdgeSample(missingOrUneven > 0 || fillMismatch > 0.05f,
                Math.min(1.0f, edgeStrength));
    }

    private static SurfaceColumn surfaceColumn(ClientLevel level, int x, int z) {
        ClientWaterColumnSampler.ColumnSample sample = ClientWaterColumnSampler.sampleExposedSurface(
                level,
                x,
                z,
                MAX_DEPTH_SAMPLE,
                SURFACE_OFFSET
        );
        if (!sample.valid()) {
            return SurfaceColumn.INVALID;
        }
        return new SurfaceColumn(
                true,
                sample.surfaceBlockY(),
                sample.surfaceY(),
                sample.depth(),
                sample.fullWater(),
                sample.fillFraction(),
                sample.velocityX(),
                sample.velocityZ()
        );
    }

    private static void drawPatch(
            ClientLevel level,
            ShorePatch patch,
            float timeSeconds,
            float tideOffset,
            OceanSeaState.Sample seaState,
            TextureAtlasSprite sprite,
            PoseStack.Pose pose,
            VertexConsumer buffer,
            float cameraX,
            float cameraZ,
            int renderRadius,
            float shorelineStrength
    ) {
        float centerX = patch.x + 0.5f;
        float centerZ = patch.z + 0.5f;
        float dx = centerX - cameraX;
        float dz = centerZ - cameraZ;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);
        float distanceFade = 1.0f - smoothStep(renderRadius * 0.72f, renderRadius, distance);
        float visualStrength = Math.max(0.0f, shorelineStrength * distanceFade);
        if (visualStrength <= 0.0f) {
            return;
        }

        GerstnerWaveProfile waveProfile = profileFor(patch.waterType);
        WaveSpectrumState spectrum = patch.waterType == WaterBodyClassifier.WaterType.OCEAN
                ? seaState.spectrum()
                : WaveSpectrumState.NEUTRAL;
        int waveLimit = WaterRenderingConfig.waveTrainLimit(patch.waterType);
        float localTideOffset = patch.waterType == WaterBodyClassifier.WaterType.OCEAN ? tideOffset : 0.0f;
        float flowSpeed = (float) Math.sqrt(patch.velocityX * patch.velocityX + patch.velocityZ * patch.velocityZ);
        float fillWave = smoothStep(0.12f, 1.0f, patch.fillFraction);
        float flowChop = smoothStep(0.05f, 0.65f, flowSpeed) * 0.16f;
        float waveBlend = smoothStep(0.25f, 3.5f, patch.depth)
                * (0.12f + fillWave * 0.33f + flowChop)
                * Math.min(1.0f, visualStrength);
        int color = opticalColor(level, patch.x + 0.5f, patch.surfaceY, patch.z + 0.5f,
                patch.depth, patch.edgeStrength, patch.fullWater, patch.fillFraction, flowSpeed, visualStrength);

        emitVertex(buffer, pose, sprite, vertex(level, patch.x, patch.surfaceY, patch.z,
                waveBlend, waveProfile, spectrum, timeSeconds, localTideOffset, waveLimit, color,
                patch.velocityX, patch.velocityZ));
        emitVertex(buffer, pose, sprite, vertex(level, patch.x, patch.surfaceY, patch.z + 1.0f,
                waveBlend, waveProfile, spectrum, timeSeconds, localTideOffset, waveLimit, color,
                patch.velocityX, patch.velocityZ));
        emitVertex(buffer, pose, sprite, vertex(level, patch.x + 1.0f, patch.surfaceY, patch.z + 1.0f,
                waveBlend, waveProfile, spectrum, timeSeconds, localTideOffset, waveLimit, color,
                patch.velocityX, patch.velocityZ));
        emitVertex(buffer, pose, sprite, vertex(level, patch.x + 1.0f, patch.surfaceY, patch.z,
                waveBlend, waveProfile, spectrum, timeSeconds, localTideOffset, waveLimit, color,
                patch.velocityX, patch.velocityZ));
    }

    private static VertexData vertex(
            ClientLevel level,
            float x,
            float y,
            float z,
            float waveBlend,
            GerstnerWaveProfile waveProfile,
            WaveSpectrumState spectrum,
            float timeSeconds,
            float tideOffset,
            int waveLimit,
            int color,
            float flowX,
            float flowZ
    ) {
        float clampedFlowX = Math.max(-1.5f, Math.min(1.5f, flowX));
        float clampedFlowZ = Math.max(-1.5f, Math.min(1.5f, flowZ));
        float advectedX = x - clampedFlowX * timeSeconds * 0.35f;
        float advectedZ = z - clampedFlowZ * timeSeconds * 0.35f;
        WaveSurfaceSample sample = waveProfile
                .sampleAt(advectedX, advectedZ, timeSeconds, waveLimit, spectrum)
                .withHeightOffset(tideOffset)
                .attenuated(waveBlend);
        float surfaceY = y + sample.height();
        float textureU = x * UV_SCALE - clampedFlowX * timeSeconds * 0.08f;
        float textureV = z * UV_SCALE - clampedFlowZ * timeSeconds * 0.08f;
        return new VertexData(
                x,
                surfaceY,
                z,
                sample.normalX(),
                sample.normalY(),
                sample.normalZ(),
                color,
                waterLight(level, x, surfaceY, z),
                textureU,
                textureV
        );
    }

    private static void emitVertex(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            TextureAtlasSprite sprite,
            VertexData vertex
    ) {
        float u = sprite.getU(tile(vertex.textureU));
        float v = sprite.getV(tile(vertex.textureV));
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
            float edgeStrength,
            boolean fullWater,
            float fillFraction,
            float flowSpeed,
            float visualStrength
    ) {
        int tint = IClientFluidTypeExtensions.of(Fluids.WATER).getTintColor(
                WATER_STATE,
                level,
                BlockPos.containing(x, y, z)
        );
        float tintR = ((tint >> 16) & 0xFF) / 255.0f;
        float tintG = ((tint >> 8) & 0xFF) / 255.0f;
        float tintB = (tint & 0xFF) / 255.0f;
        float absorption = 1.0f - (float) Math.exp(-Math.max(0.0f, depth) * 0.28f);

        float red = mix(0.16f + tintR * 0.36f, 0.06f + tintR * 0.20f, absorption);
        float green = mix(0.58f + tintG * 0.26f, 0.32f + tintG * 0.18f, absorption);
        float blue = mix(0.76f + tintB * 0.22f, 0.60f + tintB * 0.20f, absorption);
        float fillFoam = smoothStep(0.0f, 0.85f, 1.0f - fillFraction) * 0.20f;
        float flowFoam = smoothStep(0.08f, 0.70f, flowSpeed) * (0.10f + edgeStrength * 0.18f);
        float foam = Math.max(Math.max(edgeStrength * 0.35f, smoothStep(0.0f, 0.9f, 1.05f - depth)),
                fillFoam + flowFoam)
                * Math.min(1.0f, visualStrength);
        red = mix(red, 0.88f, foam);
        green = mix(green, 0.96f, foam);
        blue = mix(blue, 1.0f, foam);
        float volumeAlpha = mix(0.28f, fullWater ? 0.52f : 0.44f, smoothStep(0.08f, 1.0f, fillFraction));
        float alpha = (volumeAlpha + edgeStrength * 0.10f + Math.min(0.06f, flowSpeed * 0.04f))
                * Math.min(1.0f, visualStrength);

        return (channel(alpha) << 24)
                | (channel(red) << 16)
                | (channel(green) << 8)
                | channel(blue);
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

    private static GerstnerWaveProfile profileFor(WaterBodyClassifier.WaterType waterType) {
        return switch (waterType) {
            case OCEAN -> GerstnerWaveProfile.OCEAN;
            case RIVER -> GerstnerWaveProfile.RIVER;
            case POND -> GerstnerWaveProfile.POND;
        };
    }

    private static void clearCache() {
        PATCHES.clear();
        cachedLevel = null;
        cachedCenterX = Integer.MIN_VALUE;
        cachedCenterZ = Integer.MIN_VALUE;
        cachedRadius = -1;
        cachedGameTime = Long.MIN_VALUE;
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

    private record SurfaceColumn(
            boolean valid,
            int surfaceBlockY,
            float surfaceY,
            float depth,
            boolean fullWater,
            float fillFraction,
            float velocityX,
            float velocityZ
    ) {
        private static final SurfaceColumn INVALID = new SurfaceColumn(false, 0, 0.0f, 0.0f, false,
                0.0f, 0.0f, 0.0f);
    }

    private record EdgeSample(boolean localEdge, float edgeStrength) {
    }

    private record ShorePatch(
            int x,
            int z,
            float surfaceY,
            float depth,
            WaterBodyClassifier.WaterType waterType,
            float edgeStrength,
            boolean fullWater,
            float fillFraction,
            float velocityX,
            float velocityZ
    ) {
    }

    private record VertexData(
            float x,
            float y,
            float z,
            float normalX,
            float normalY,
            float normalZ,
            int color,
            int light,
            float textureU,
            float textureV
    ) {
    }
}
