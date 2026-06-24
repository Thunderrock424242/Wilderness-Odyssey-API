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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static final int CACHE_LIFETIME_TICKS = 20;
    private static final int MAX_DEPTH_SAMPLE = 16;
    private static final float MAX_SURFACE_STEP = 0.75f;
    private static final float UV_SCALE = 0.28f;
    private static final float VISUAL_TIDE_SCALE = 0.18f;

    private static final List<SurfacePatch> PATCHES = new ArrayList<>();
    private static volatile Set<Long> ownedVanillaTops = Set.of();
    private static ClientLevel cachedLevel;
    private static int cachedCenterX = Integer.MIN_VALUE;
    private static int cachedCenterZ = Integer.MIN_VALUE;
    private static int cachedRadius = -1;
    private static int cachedCellSize = -1;
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
        int radius = WaterRenderingConfig.oceanRenderDistanceBlocks();
        int cellSize = WaterRenderingConfig.oceanCellSize();
        refreshCacheIfNeeded(level, (int) Math.floor(camera.x), (int) Math.floor(camera.z), radius, cellSize);
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
                    seaState.windDirectionZ()
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
                    sprite, poseStack.last(), buffer);
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
            VertexConsumer buffer
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
                waveProfile, spectrum, timeSeconds, localTideOffset, waveLimit, patchColor);
        VertexData second = vertex(level, x0, patch.secondY, z1, waveBlend,
                waveProfile, spectrum, timeSeconds, localTideOffset, waveLimit, patchColor);
        VertexData third = vertex(level, x1, patch.thirdY, z1, waveBlend,
                waveProfile, spectrum, timeSeconds, localTideOffset, waveLimit, patchColor);
        VertexData fourth = vertex(level, x1, patch.fourthY, z0, waveBlend,
                waveProfile, spectrum, timeSeconds, localTideOffset, waveLimit, patchColor);

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
            int color
    ) {
        WaveSurfaceSample sample = waveProfile
                .sampleAt(baseX, baseZ, timeSeconds, waveLimit, spectrum)
                .withHeightOffset(tideOffset)
                .attenuated(waveBlend);
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

        float shallowR = 0.08f + tintR * 0.38f;
        float shallowG = 0.47f + tintG * 0.30f;
        float shallowB = 0.62f + tintB * 0.28f;
        float deepR = 0.025f + tintR * 0.12f;
        float deepG = 0.12f + tintG * 0.12f;
        float deepB = 0.30f + tintB * 0.22f;
        float red = mix(shallowR, deepR, absorption);
        float green = mix(shallowG, deepG, absorption);
        float blue = mix(shallowB, deepB, absorption);

        float foam = smoothStep(0.0f, 0.8f, 1.15f - depth);
        red = mix(red, 0.86f, foam);
        green = mix(green, 0.94f, foam);
        blue = mix(blue, 1.0f, foam);
        float alpha = 0.56f + absorption * 0.14f + foam * 0.08f;

        return (channel(alpha) << 24)
                | (channel(red) << 16)
                | (channel(green) << 8)
                | channel(blue);
    }

    private static void refreshCacheIfNeeded(
            ClientLevel level,
            int cameraX,
            int cameraZ,
            int radius,
            int cellSize
    ) {
        long gameTime = level.getGameTime();
        int movementThreshold = Math.max(2, cellSize * 2);
        boolean stale = cachedLevel != level
                || radius != cachedRadius
                || cellSize != cachedCellSize
                || Math.abs(cameraX - cachedCenterX) >= movementThreshold
                || Math.abs(cameraZ - cachedCenterZ) >= movementThreshold
                || gameTime - cachedGameTime >= CACHE_LIFETIME_TICKS;
        if (!stale) {
            return;
        }

        cachedLevel = level;
        cachedCenterX = cameraX;
        cachedCenterZ = cameraZ;
        cachedRadius = radius;
        cachedCellSize = cellSize;
        cachedGameTime = gameTime;
        PATCHES.clear();

        Map<Long, WaterColumn> columns = new HashMap<>();
        Set<Long> rebuiltOwnedTops = new HashSet<>();
        int radiusSquared = radius * radius;
        int startX = Math.floorDiv(cameraX - radius, cellSize) * cellSize;
        int endX = Math.floorDiv(cameraX + radius, cellSize) * cellSize;
        int startZ = Math.floorDiv(cameraZ - radius, cellSize) * cellSize;
        int endZ = Math.floorDiv(cameraZ + radius, cellSize) * cellSize;

        for (int x = startX; x < endX; x += cellSize) {
            for (int z = startZ; z < endZ; z += cellSize) {
                int dx = x + cellSize / 2 - cameraX;
                int dz = z + cellSize / 2 - cameraZ;
                if (dx * dx + dz * dz > radiusSquared) {
                    continue;
                }

                WaterColumn first = column(level, columns, x, z);
                WaterColumn second = column(level, columns, x, z + cellSize);
                WaterColumn third = column(level, columns, x + cellSize, z + cellSize);
                WaterColumn fourth = column(level, columns, x + cellSize, z);
                if (!first.valid || !second.valid || !third.valid || !fourth.valid) {
                    continue;
                }

                PatchFootprint footprint = validatePatchFootprint(
                        level,
                        columns,
                        x,
                        z,
                        cellSize
                );
                if (!footprint.valid) {
                    continue;
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

                // The replacement quad spans every block inside the selected
                // cell. Record only real exposed water tops so the chunk mixin
                // can omit precisely those vanilla faces, never side walls or
                // distant compatibility water.
                for (int offsetX = 0; offsetX < cellSize; offsetX++) {
                    for (int offsetZ = 0; offsetZ < cellSize; offsetZ++) {
                        WaterColumn covered = column(level, columns, x + offsetX, z + offsetZ);
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
        updateVanillaTopOwnership(level, rebuiltOwnedTops);
    }

    // Coarse optimized cells must never bridge an island, beach corner, or
    // unloaded gap merely because their four outer corners contain water.
    // Validate the complete vertex grid and use its shallowest depth so wave
    // attenuation remains conservative near irregular shorelines.
    private static PatchFootprint validatePatchFootprint(
            ClientLevel level,
            Map<Long, WaterColumn> columns,
            int startX,
            int startZ,
            int cellSize
    ) {
        float minimumSurface = Float.POSITIVE_INFINITY;
        float maximumSurface = Float.NEGATIVE_INFINITY;
        float minimumDepth = MAX_DEPTH_SAMPLE;
        for (int offsetX = 0; offsetX <= cellSize; offsetX++) {
            for (int offsetZ = 0; offsetZ <= cellSize; offsetZ++) {
                WaterColumn covered = column(level, columns, startX + offsetX, startZ + offsetZ);
                if (!covered.valid) {
                    return PatchFootprint.INVALID;
                }
                minimumSurface = Math.min(minimumSurface, covered.surfaceY);
                maximumSurface = Math.max(maximumSurface, covered.surfaceY);
                minimumDepth = Math.min(minimumDepth, covered.depth);
            }
        }
        if (maximumSurface - minimumSurface > MAX_SURFACE_STEP) {
            return PatchFootprint.INVALID;
        }
        return new PatchFootprint(true, minimumDepth);
    }

    private static WaterColumn column(
            ClientLevel level,
            Map<Long, WaterColumn> columns,
            int x,
            int z
    ) {
        long key = ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
        return columns.computeIfAbsent(key, ignored -> scanColumn(level, x, z));
    }

    private static WaterColumn scanColumn(ClientLevel level, int x, int z) {
        int surfaceBlockY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        pos.set(x, surfaceBlockY, z);
        if (!level.hasChunkAt(pos)) {
            return WaterColumn.INVALID;
        }

        FluidState surfaceFluid = level.getFluidState(pos);
        var canonicalCell = CanonicalWater.getTracked(level, pos);
        if ((canonicalCell != null && canonicalCell.volumeUnits() <= 0)
                || (canonicalCell == null && !surfaceFluid.is(Fluids.WATER))
                || level.getFluidState(above.set(x, surfaceBlockY + 1, z)).is(Fluids.WATER)) {
            return WaterColumn.INVALID;
        }

        WaterBodyClassifier.WaterType waterType = WaterBodyClassifier.classify(level, pos);
        float depth = MAX_DEPTH_SAMPLE;
        for (int offset = 1; offset <= MAX_DEPTH_SAMPLE; offset++) {
            pos.set(x, surfaceBlockY - offset, z);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                depth = offset;
                break;
            }
        }
        return new WaterColumn(
                true,
                surfaceBlockY,
                surfaceBlockY + (canonicalCell != null
                        ? canonicalCell.fillFraction()
                        : surfaceFluid.getOwnHeight()) + 0.001f,
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
                Math.max(7, LightTexture.block(packed)),
                Math.max(7, LightTexture.sky(packed))
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
        ownedVanillaTops = Set.of();
        cachedLevel = null;
        cachedCenterX = Integer.MIN_VALUE;
        cachedCenterZ = Integer.MIN_VALUE;
        cachedGameTime = Long.MIN_VALUE;
    }

    /** Returns whether the per-frame mesh currently replaces this baked top face. */
    public static boolean ownsBakedTop(BlockPos pos) {
        return ownedVanillaTops.contains(pos.asLong());
    }

    // Rebuild only chunk sections whose top-face ownership changed. The set is
    // immutable so chunk compilation workers can read it without locking.
    private static void updateVanillaTopOwnership(ClientLevel level, Set<Long> rebuiltOwnership) {
        Set<Long> next = Set.copyOf(rebuiltOwnership);
        Set<Long> previous = ownedVanillaTops;
        if (previous.equals(next)) {
            return;
        }
        ownedVanillaTops = next;

        Set<Long> changed = new HashSet<>(previous);
        for (long packedPos : next) {
            if (!changed.add(packedPos)) {
                changed.remove(packedPos);
            }
        }
        markSectionsDirty(level, changed);
    }

    private static void releaseVanillaTopOwnership(ClientLevel level) {
        Set<Long> previous = ownedVanillaTops;
        if (previous.isEmpty()) {
            return;
        }
        ownedVanillaTops = Set.of();
        PATCHES.clear();
        if (level != null) {
            markSectionsDirty(level, previous);
        }
    }

    private static void markSectionsDirty(ClientLevel level, Set<Long> changedPositions) {
        Set<Long> dirtySections = new HashSet<>();
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
