package com.thunder.wildernessodysseyapi.weather.client.precipitation;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.client.ClientWeatherCoordinator;
import com.thunder.wildernessodysseyapi.weather.client.precipitation.PrecipitationImpactModel.ImpactSurface;
import com.thunder.wildernessodysseyapi.weather.config.WeatherRenderingConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Renders and ticks server-authored precipitation through Minecraft's weather pipeline.
 *
 * <p>Near columns retain vanilla rain and snow textures, animation, light, and
 * render state, but each column reads its own atmospheric intensity. A bounded
 * loaded-only lattice adds distant rain curtains without scanning terrain or
 * forcing chunks outside the player's view.</p>
 */
public final class LocalizedPrecipitationRenderer {

    private static final ResourceLocation RAIN_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/environment/rain.png");
    private static final ResourceLocation SNOW_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/environment/snow.png");
    private static final int FANCY_NEAR_RADIUS = 10;
    private static final int FAST_NEAR_RADIUS = 5;
    private static final int MAX_NEAR_COLUMNS = 21 * 21;
    private static final int MAX_DISTANT_SHAFTS = 2_048;
    private static final int MAX_RENDER_COLUMNS = MAX_NEAR_COLUMNS + MAX_DISTANT_SHAFTS;
    private static final byte RAIN = 1;
    private static final byte SNOW = 2;
    private static final byte HAIL = 3;
    private static final byte NEAR = 0;
    private static final byte DISTANT = 1;

    private static final int[] RENDER_X = new int[MAX_RENDER_COLUMNS];
    private static final int[] RENDER_Z = new int[MAX_RENDER_COLUMNS];
    private static final int[] RENDER_BOTTOM_Y = new int[MAX_RENDER_COLUMNS];
    private static final int[] RENDER_TOP_Y = new int[MAX_RENDER_COLUMNS];
    private static final int[] RENDER_LIGHT_Y = new int[MAX_RENDER_COLUMNS];
    private static final float[] RENDER_ALPHA = new float[MAX_RENDER_COLUMNS];
    private static final byte[] RENDER_TYPE = new byte[MAX_RENDER_COLUMNS];
    private static final byte[] RENDER_STYLE = new byte[MAX_RENDER_COLUMNS];

    private static final int[] DISTANT_X = new int[MAX_DISTANT_SHAFTS];
    private static final int[] DISTANT_Z = new int[MAX_DISTANT_SHAFTS];
    private static final int[] DISTANT_BOTTOM_Y = new int[MAX_DISTANT_SHAFTS];
    private static final int[] DISTANT_TOP_Y = new int[MAX_DISTANT_SHAFTS];
    private static final float[] DISTANT_INTENSITY = new float[MAX_DISTANT_SHAFTS];
    private static final byte[] DISTANT_TYPE = new byte[MAX_DISTANT_SHAFTS];

    private static ClientLevel renderedLevel;
    private static int renderColumnCount;
    private static int distantShaftCount;
    private static int cachedDistantOriginX = Integer.MIN_VALUE;
    private static int cachedDistantOriginZ = Integer.MIN_VALUE;
    private static int cachedDistantRadius = -1;
    private static int cachedCloudHeight = Integer.MIN_VALUE;
    private static long cachedSequence = Long.MIN_VALUE;
    private static long lastDistantBuildTick = Long.MIN_VALUE;
    private static WeatherRenderingConfig.Settings cachedSettings;
    private static int rainSoundTime;
    private static long lastVisiblePrecipitationTick = Long.MIN_VALUE;
    private static Diagnostics diagnostics = Diagnostics.INACTIVE;

    private LocalizedPrecipitationRenderer() {
    }

    /**
     * Draws localized near precipitation and cached distant rain shafts.
     *
     * <p>This method is called from vanilla's dimension-weather ownership
     * boundary, while the Fabulous weather target is already active.</p>
     */
    public static void render(
            ClientLevel level,
            int ticks,
            float partialTick,
            LightTexture lightTexture,
            double camX,
            double camY,
            double camZ,
            float cloudHeight
    ) {
        ClientWeatherCoordinator.ClientStateView state = ClientWeatherCoordinator.stateView(level);
        if (state == null) {
            clear();
            return;
        }
        prepareLevel(level);

        WeatherRenderingConfig.Settings settings = WeatherRenderingConfig.settings();
        int nearRadius = Minecraft.useFancyGraphics() ? FANCY_NEAR_RADIUS : FAST_NEAR_RADIUS;
        renderColumnCount = 0;
        collectNearColumns(level, camX, camY, camZ, nearRadius, settings);
        if (settings.distantRainShafts()) {
            rebuildDistantShaftsIfNeeded(
                    level,
                    state,
                    settings,
                    ticks,
                    camX,
                    camY,
                    camZ,
                    cloudHeight,
                    nearRadius
            );
            appendDistantShafts(camX, camY, camZ, nearRadius, cachedDistantRadius);
        } else {
            clearDistantCache();
        }

        int rainColumns = countColumns(RAIN);
        int snowColumns = countColumns(SNOW);
        int hailColumns = countColumns(HAIL);
        if (rainColumns > 0 || snowColumns > 0 || hailColumns > 0) {
            drawColumns(level, ticks, partialTick, lightTexture, camX, camY, camZ, settings);
            lastVisiblePrecipitationTick = level.getGameTime();
        }
        int distantColumns = countStyledColumns(DISTANT);
        diagnostics = new Diagnostics(
                true,
                renderColumnCount - distantColumns,
                distantColumns,
                renderColumnCount * 4
        );
    }

    /**
     * Spawns subtle localized impacts and rain sounds from visible sampled columns.
     */
    public static void tick(ClientLevel level, int ticks, Camera camera) {
        if (level == null || camera == null) {
            clear();
            return;
        }
        prepareLevel(level);
        Minecraft minecraft = Minecraft.getInstance();
        WeatherRenderingConfig.Settings settings = WeatherRenderingConfig.settings();
        long elapsedSinceVisible = level.getGameTime() >= lastVisiblePrecipitationTick
                ? level.getGameTime() - lastVisiblePrecipitationTick
                : Long.MAX_VALUE;
        // The impact path follows the actual precipitation mesh. This prevents
        // ground splashes from advertising rain that failed to draw.
        if (elapsedSinceVisible > 2L) {
            rainSoundTime = 0;
            return;
        }
        RandomSource random = RandomSource.create((long) ticks * 312987231L);
        LevelReader levelReader = level;
        BlockPos cameraPos = BlockPos.containing(camera.getPosition());
        BlockPos soundPos = null;
        ParticleStatus particleStatus = minecraft.options.particles().get();
        int attempts = Minecraft.useFancyGraphics() ? 100 : 50;
        if (particleStatus == ParticleStatus.DECREASED) {
            attempts /= 2;
        }
        double particleFactor = particleStatus == ParticleStatus.DECREASED ? 0.5 : 1.0;

        for (int attempt = 0; attempt < attempts; attempt++) {
            int offsetX = random.nextInt(21) - 10;
            int offsetZ = random.nextInt(21) - 10;
            int blockX = cameraPos.getX() + offsetX;
            int blockZ = cameraPos.getZ() + offsetZ;
            BlockPos queryPos = new BlockPos(blockX, cameraPos.getY(), blockZ);
            if (!level.hasChunkAt(queryPos)) {
                continue;
            }

            double intensity = ClientWeatherCoordinator.visualPrecipitationIntensityAt(
                    level,
                    blockX + 0.5,
                    blockZ + 0.5
            );
            if (intensity <= PrecipitationVisualModel.PRECIPITATION_EPSILON
                    || random.nextDouble() > intensity) {
                continue;
            }
            if (!PrecipitationVisualModel.shouldRenderNearColumn(
                    blockX,
                    blockZ,
                    intensity,
                    settings.precipitationStreakDensity()
            )) {
                continue;
            }

            BlockPos surfacePos = levelReader.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING,
                    queryPos
            );
            PrecipitationType surfaceType = ClientWeatherCoordinator.precipitationTypeAt(level, surfacePos);
            if (surfacePos.getY() <= levelReader.getMinBuildHeight()
                    || surfacePos.getY() > cameraPos.getY() + 10
                    || surfacePos.getY() < cameraPos.getY() - 10
                    || !level.canSeeSky(surfacePos)
                    || (surfaceType != PrecipitationType.RAIN
                    && surfaceType != PrecipitationType.HAIL)) {
                continue;
            }

            soundPos = surfacePos.below();
            if (particleStatus == ParticleStatus.MINIMAL) {
                break;
            }

            double localX = random.nextDouble();
            double localZ = random.nextDouble();
            BlockState blockState = levelReader.getBlockState(soundPos);
            FluidState fluidState = levelReader.getFluidState(soundPos);
            VoxelShape shape = blockState.getCollisionShape(levelReader, soundPos);
            double collisionHeight = shape.max(Direction.Axis.Y, localX, localZ);
            double fluidHeight = fluidState.getHeight(levelReader, soundPos);
            double particleHeight = Math.max(collisionHeight, fluidHeight);
            boolean hotSurface = fluidState.is(FluidTags.LAVA)
                    || blockState.is(Blocks.MAGMA_BLOCK)
                    || CampfireBlock.isLitCampfire(blockState);
            if (hotSurface) {
                level.addParticle(
                        ParticleTypes.SMOKE,
                        soundPos.getX() + localX,
                        soundPos.getY() + particleHeight,
                        soundPos.getZ() + localZ,
                        0.0,
                        0.0,
                        0.0
                );
                continue;
            }

            double impactChance = PrecipitationImpactModel.spawnProbability(
                    intensity,
                    settings.precipitationImpactDensity(),
                    particleFactor
            );
            if (random.nextDouble() <= impactChance) {
                ImpactSurface impactSurface = surfaceType == PrecipitationType.HAIL
                        ? ImpactSurface.HAIL
                        : fluidState.is(FluidTags.WATER)
                        ? ImpactSurface.WATER
                        : blockState.is(BlockTags.LEAVES)
                        ? ImpactSurface.LEAF
                        : ImpactSurface.HARD;
                WeatherImpactRenderer.spawn(
                        level,
                        soundPos.getX() + localX,
                        soundPos.getY() + particleHeight,
                        soundPos.getZ() + localZ,
                        impactSurface,
                        (float) intensity
                );
            }
        }

        if (soundPos != null && random.nextInt(3) < rainSoundTime++) {
            rainSoundTime = 0;
            if (soundPos.getY() > cameraPos.getY() + 1
                    && levelReader.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, cameraPos).getY()
                    > Mth.floor(cameraPos.getY())) {
                level.playLocalSound(
                        soundPos,
                        SoundEvents.WEATHER_RAIN_ABOVE,
                        SoundSource.WEATHER,
                        0.1F,
                        0.5F,
                        false
                );
            } else {
                level.playLocalSound(
                        soundPos,
                        SoundEvents.WEATHER_RAIN,
                        SoundSource.WEATHER,
                        0.2F,
                        1.0F,
                        false
                );
            }
        }
    }

    /** Returns immutable values for the F3 weather diagnostics. */
    public static Diagnostics diagnostics() {
        return diagnostics;
    }

    /** Clears render-only caches when a dimension effect owns weather geometry. */
    public static void clearRenderState() {
        renderColumnCount = 0;
        lastVisiblePrecipitationTick = Long.MIN_VALUE;
        clearDistantCache();
        diagnostics = Diagnostics.INACTIVE;
    }

    /** Clears only localized sound cadence when a dimension effect owns rain ticking. */
    public static void clearTickState() {
        rainSoundTime = 0;
    }

    /** Clears all per-level precipitation caches and counters. */
    public static void clear() {
        renderedLevel = null;
        clearRenderState();
        clearTickState();
    }

    private static void prepareLevel(ClientLevel level) {
        if (renderedLevel == level) {
            return;
        }
        clear();
        renderedLevel = level;
    }

    private static void collectNearColumns(
            ClientLevel level,
            double camX,
            double camY,
            double camZ,
            int radius,
            WeatherRenderingConfig.Settings settings
    ) {
        int cameraX = Mth.floor(camX);
        int cameraY = Mth.floor(camY);
        int cameraZ = Mth.floor(camZ);
        BlockPos.MutableBlockPos queryPos = new BlockPos.MutableBlockPos();
        for (int blockZ = cameraZ - radius; blockZ <= cameraZ + radius; blockZ++) {
            for (int blockX = cameraX - radius; blockX <= cameraX + radius; blockX++) {
                queryPos.set(blockX, cameraY, blockZ);
                if (!level.hasChunkAt(queryPos)) {
                    continue;
                }
                double intensity = ClientWeatherCoordinator.visualPrecipitationIntensityAt(
                        level,
                        blockX + 0.5,
                        blockZ + 0.5
                );
                if (intensity <= PrecipitationVisualModel.PRECIPITATION_EPSILON) {
                    continue;
                }
                if (!PrecipitationVisualModel.shouldRenderNearColumn(
                        blockX,
                        blockZ,
                        intensity,
                        settings.precipitationStreakDensity()
                )) {
                    continue;
                }
                PrecipitationType type = ClientWeatherCoordinator.precipitationTypeAt(level, queryPos);
                if (type == PrecipitationType.NONE) {
                    continue;
                }

                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
                int bottomY = Math.max(cameraY - radius, surfaceY);
                int topY = Math.max(cameraY + radius, surfaceY);
                if (bottomY == topY) {
                    continue;
                }
                double distance = Math.hypot(blockX + 0.5 - camX, blockZ + 0.5 - camZ);
                boolean snow = type == PrecipitationType.SNOW;
                boolean hail = type == PrecipitationType.HAIL;
                float alpha = PrecipitationVisualModel.scaledAlpha(
                        PrecipitationVisualModel.nearAlpha(intensity, distance, radius, snow),
                        settings.precipitationOpacity()
                );
                if (alpha <= 0.001F) {
                    continue;
                }
                addRenderColumn(
                        blockX,
                        blockZ,
                        bottomY,
                        topY,
                        Math.max(surfaceY, cameraY),
                        alpha,
                        hail ? HAIL : snow ? SNOW : RAIN,
                        NEAR
                );
            }
        }
    }

    private static void rebuildDistantShaftsIfNeeded(
            ClientLevel level,
            ClientWeatherCoordinator.ClientStateView state,
            WeatherRenderingConfig.Settings settings,
            long ticks,
            double camX,
            double camY,
            double camZ,
            float cloudHeight,
            int nearRadius
    ) {
        int spacing = settings.distantRainSpacingBlocks();
        int originX = Math.floorDiv(Mth.floor(camX), spacing) * spacing;
        int originZ = Math.floorDiv(Mth.floor(camZ), spacing) * spacing;
        int radius = PrecipitationVisualModel.boundedDistantRadiusBlocks(
                settings.distantRainDistanceBlocks(),
                spacing,
                settings.maximumDistantRainShafts()
        );
        int roundedCloudHeight = Float.isFinite(cloudHeight)
                ? Mth.ceil(cloudHeight)
                : Mth.floor(camY) + 64;
        long elapsed = ticks >= lastDistantBuildTick
                ? ticks - lastDistantBuildTick
                : Long.MAX_VALUE;
        boolean rebuild = renderedLevel != level
                || originX != cachedDistantOriginX
                || originZ != cachedDistantOriginZ
                || radius != cachedDistantRadius
                || roundedCloudHeight != cachedCloudHeight
                || state.sequence() != cachedSequence
                || !settings.equals(cachedSettings)
                || elapsed >= settings.rebuildIntervalTicks();
        if (!rebuild) {
            return;
        }

        distantShaftCount = 0;
        int radiusCells = spacing == 0 ? 0 : radius / spacing;
        int minimumDistance = nearRadius + spacing;
        long minimumDistanceSquared = (long) minimumDistance * minimumDistance;
        long radiusSquared = (long) radius * radius;
        int cameraY = Mth.floor(camY);
        BlockPos.MutableBlockPos queryPos = new BlockPos.MutableBlockPos();
        for (int cellZ = -radiusCells; cellZ <= radiusCells; cellZ++) {
            for (int cellX = -radiusCells; cellX <= radiusCells; cellX++) {
                int offsetX = cellX * spacing;
                int offsetZ = cellZ * spacing;
                long distanceSquared = (long) offsetX * offsetX + (long) offsetZ * offsetZ;
                if (distanceSquared <= minimumDistanceSquared || distanceSquared > radiusSquared
                        || distantShaftCount >= settings.maximumDistantRainShafts()) {
                    continue;
                }
                int blockX = originX + offsetX;
                int blockZ = originZ + offsetZ;
                queryPos.set(blockX, cameraY, blockZ);
                if (!level.hasChunkAt(queryPos)) {
                    continue;
                }
                double intensity = ClientWeatherCoordinator.visualPrecipitationIntensityAt(
                        level,
                        blockX + 0.5,
                        blockZ + 0.5
                );
                PrecipitationType type = ClientWeatherCoordinator.precipitationTypeAt(level, queryPos);
                if (intensity <= PrecipitationVisualModel.PRECIPITATION_EPSILON
                        || (type != PrecipitationType.RAIN && type != PrecipitationType.HAIL)) {
                    continue;
                }

                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
                int topY = Math.max(surfaceY + 16, roundedCloudHeight);
                topY = Math.min(topY, level.getMaxBuildHeight() - 1);
                if (topY <= surfaceY) {
                    continue;
                }
                int index = distantShaftCount++;
                DISTANT_X[index] = blockX;
                DISTANT_Z[index] = blockZ;
                DISTANT_BOTTOM_Y[index] = surfaceY;
                DISTANT_TOP_Y[index] = topY;
                DISTANT_INTENSITY[index] = (float) intensity;
                DISTANT_TYPE[index] = type == PrecipitationType.HAIL ? HAIL : RAIN;
            }
        }

        cachedDistantOriginX = originX;
        cachedDistantOriginZ = originZ;
        cachedDistantRadius = radius;
        cachedCloudHeight = roundedCloudHeight;
        cachedSequence = state.sequence();
        cachedSettings = settings;
        lastDistantBuildTick = ticks;
    }

    private static void appendDistantShafts(
            double camX,
            double camY,
            double camZ,
            int nearRadius,
            int farRadius
    ) {
        int cameraY = Mth.floor(camY);
        for (int index = 0; index < distantShaftCount; index++) {
            double distance = Math.hypot(
                    DISTANT_X[index] + 0.5 - camX,
                    DISTANT_Z[index] + 0.5 - camZ
            );
            float alpha = PrecipitationVisualModel.distantAlpha(
                    DISTANT_INTENSITY[index],
                    distance,
                    nearRadius,
                    farRadius
            );
            alpha = PrecipitationVisualModel.scaledAlpha(
                    alpha,
                    WeatherRenderingConfig.settings().precipitationOpacity()
            );
            if (alpha <= 0.001F) {
                continue;
            }
            addRenderColumn(
                    DISTANT_X[index],
                    DISTANT_Z[index],
                    DISTANT_BOTTOM_Y[index],
                    DISTANT_TOP_Y[index],
                    Math.max(DISTANT_BOTTOM_Y[index], cameraY),
                    alpha,
                    DISTANT_TYPE[index],
                    DISTANT
            );
        }
    }

    private static void addRenderColumn(
            int blockX,
            int blockZ,
            int bottomY,
            int topY,
            int lightY,
            float alpha,
            byte type,
            byte style
    ) {
        if (renderColumnCount >= MAX_RENDER_COLUMNS) {
            return;
        }
        int index = renderColumnCount++;
        RENDER_X[index] = blockX;
        RENDER_Z[index] = blockZ;
        RENDER_BOTTOM_Y[index] = bottomY;
        RENDER_TOP_Y[index] = topY;
        RENDER_LIGHT_Y[index] = lightY;
        RENDER_ALPHA[index] = alpha;
        RENDER_TYPE[index] = type;
        RENDER_STYLE[index] = style;
    }

    private static void drawColumns(
            ClientLevel level,
            int ticks,
            float partialTick,
            LightTexture lightTexture,
            double camX,
            double camY,
            double camZ,
            WeatherRenderingConfig.Settings settings
    ) {
        lightTexture.turnOnLightLayer();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getParticleShader);
        try {
            drawBatch(level, ticks, partialTick, camX, camY, camZ, settings, RAIN, RAIN_LOCATION);
            drawBatch(level, ticks, partialTick, camX, camY, camZ, settings, SNOW, SNOW_LOCATION);
            drawBatch(level, ticks, partialTick, camX, camY, camZ, settings, HAIL, SNOW_LOCATION);
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            lightTexture.turnOffLightLayer();
        }
    }

    private static void drawBatch(
            ClientLevel level,
            int ticks,
            float partialTick,
            double camX,
            double camY,
            double camZ,
            WeatherRenderingConfig.Settings settings,
            byte type,
            ResourceLocation texture
    ) {
        if (countColumns(type) == 0) {
            return;
        }
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.PARTICLE
        );
        BlockPos.MutableBlockPos lightPos = new BlockPos.MutableBlockPos();
        double renderTicks = ticks + partialTick;
        WeatherSample localWeather = ClientWeatherCoordinator.localSample(level);
        for (int index = 0; index < renderColumnCount; index++) {
            if (RENDER_TYPE[index] != type) {
                continue;
            }
            int blockX = RENDER_X[index];
            int blockZ = RENDER_Z[index];
            double deltaX = blockX + 0.5 - camX;
            double deltaZ = blockZ + 0.5 - camZ;
            double length = Math.hypot(deltaX, deltaZ);
            double halfWidth = RENDER_STYLE[index] == DISTANT
                    ? Math.min(3.0, settings.distantRainSpacingBlocks() * 0.34)
                    : 0.28 + PrecipitationVisualModel.columnNoise(
                            blockX,
                            blockZ,
                            0x94D049BB133111EBL
                    ) * 0.18;
            double sideX = length <= 1.0E-6 ? halfWidth : -deltaZ / length * halfWidth;
            double sideZ = length <= 1.0E-6 ? 0.0 : deltaX / length * halfWidth;
            float x0 = (float) (blockX - camX - sideX + 0.5);
            float x1 = (float) (blockX - camX + sideX + 0.5);
            float z0 = (float) (blockZ - camZ - sideZ + 0.5);
            float z1 = (float) (blockZ - camZ + sideZ + 0.5);
            float top = (float) (RENDER_TOP_Y[index] - camY);
            float bottom = (float) (RENDER_BOTTOM_Y[index] - camY);
            boolean snow = type == SNOW;
            float topOffsetX = settings.windDrivenPrecipitation()
                    ? PrecipitationVisualModel.topWindOffset(
                            localWeather.wind().x(),
                            top - bottom,
                            settings.precipitationWindSlantBlocks(),
                            snow
                    )
                    : 0.0F;
            float topOffsetZ = settings.windDrivenPrecipitation()
                    ? PrecipitationVisualModel.topWindOffset(
                            localWeather.wind().z(),
                            top - bottom,
                            settings.precipitationWindSlantBlocks(),
                            snow
                    )
                    : 0.0F;
            lightPos.set(blockX, RENDER_LIGHT_Y[index], blockZ);
            int light = LevelRenderer.getLightColor(level, lightPos);

            if (type == RAIN || type == HAIL) {
                emitRainQuad(
                        builder,
                        blockX,
                        blockZ,
                        renderTicks,
                        x0,
                        x1,
                        z0,
                        z1,
                        top,
                        bottom,
                        topOffsetX,
                        topOffsetZ,
                        RENDER_TOP_Y[index],
                        RENDER_BOTTOM_Y[index],
                        RENDER_ALPHA[index],
                        light
                );
            } else {
                emitSnowQuad(
                        builder,
                        blockX,
                        blockZ,
                        renderTicks,
                        x0,
                        x1,
                        z0,
                        z1,
                        top,
                        bottom,
                        topOffsetX,
                        topOffsetZ,
                        RENDER_TOP_Y[index],
                        RENDER_BOTTOM_Y[index],
                        RENDER_ALPHA[index],
                        light
                );
            }
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static void emitRainQuad(
            BufferBuilder builder,
            int blockX,
            int blockZ,
            double renderTicks,
            float x0,
            float x1,
            float z0,
            float z1,
            float top,
            float bottom,
            float topOffsetX,
            float topOffsetZ,
            int topWorldY,
            int bottomWorldY,
            float alpha,
            int light
    ) {
        int time = ((int) renderTicks) & 131071;
        int phase = blockX * blockX * 3121 + blockX * 45238971
                + blockZ * blockZ * 418711 + blockZ * 13761 & 0xFF;
        float speed = 3.0F + (float) PrecipitationVisualModel.columnNoise(
                blockX,
                blockZ,
                0x243F6A8885A308D3L
        );
        float scroll = -((time + phase) + (float) (renderTicks - Math.floor(renderTicks)))
                / 32.0F * speed;
        scroll %= 32.0F;
        builder.addVertex(x0 + topOffsetX, top, z0 + topOffsetZ)
                .setUv(0.0F, bottomWorldY * 0.25F + scroll)
                .setColor(1.0F, 1.0F, 1.0F, alpha)
                .setLight(light);
        builder.addVertex(x1 + topOffsetX, top, z1 + topOffsetZ)
                .setUv(1.0F, bottomWorldY * 0.25F + scroll)
                .setColor(1.0F, 1.0F, 1.0F, alpha)
                .setLight(light);
        builder.addVertex(x1, bottom, z1)
                .setUv(1.0F, topWorldY * 0.25F + scroll)
                .setColor(1.0F, 1.0F, 1.0F, alpha)
                .setLight(light);
        builder.addVertex(x0, bottom, z0)
                .setUv(0.0F, topWorldY * 0.25F + scroll)
                .setColor(1.0F, 1.0F, 1.0F, alpha)
                .setLight(light);
    }

    private static void emitSnowQuad(
            BufferBuilder builder,
            int blockX,
            int blockZ,
            double renderTicks,
            float x0,
            float x1,
            float z0,
            float z1,
            float top,
            float bottom,
            float topOffsetX,
            float topOffsetZ,
            int topWorldY,
            int bottomWorldY,
            float alpha,
            int light
    ) {
        float scroll = -((float) (((int) renderTicks) & 511)
                + (float) (renderTicks - Math.floor(renderTicks))) / 512.0F;
        float uOffset = (float) (
                PrecipitationVisualModel.columnNoise(blockX, blockZ, 0x13198A2E03707344L)
                        + renderTicks * 0.01
                        * (PrecipitationVisualModel.columnNoise(blockX, blockZ, 0xA4093822299F31D0L) * 2.0 - 1.0)
        );
        float vOffset = (float) (
                PrecipitationVisualModel.columnNoise(blockX, blockZ, 0x082EFA98EC4E6C89L)
                        + renderTicks * 0.001
                        * (PrecipitationVisualModel.columnNoise(blockX, blockZ, 0x452821E638D01377L) * 2.0 - 1.0)
        );
        int sky = light >> 16 & 65535;
        int block = light & 65535;
        int snowLight = ((sky * 3 + 240) / 4) << 16 | (block * 3 + 240) / 4;
        builder.addVertex(x0 + topOffsetX, top, z0 + topOffsetZ)
                .setUv(uOffset, bottomWorldY * 0.25F + scroll + vOffset)
                .setColor(1.0F, 1.0F, 1.0F, alpha)
                .setLight(snowLight);
        builder.addVertex(x1 + topOffsetX, top, z1 + topOffsetZ)
                .setUv(1.0F + uOffset, bottomWorldY * 0.25F + scroll + vOffset)
                .setColor(1.0F, 1.0F, 1.0F, alpha)
                .setLight(snowLight);
        builder.addVertex(x1, bottom, z1)
                .setUv(1.0F + uOffset, topWorldY * 0.25F + scroll + vOffset)
                .setColor(1.0F, 1.0F, 1.0F, alpha)
                .setLight(snowLight);
        builder.addVertex(x0, bottom, z0)
                .setUv(uOffset, topWorldY * 0.25F + scroll + vOffset)
                .setColor(1.0F, 1.0F, 1.0F, alpha)
                .setLight(snowLight);
    }

    private static int countColumns(byte type) {
        int count = 0;
        for (int index = 0; index < renderColumnCount; index++) {
            if (RENDER_TYPE[index] == type) {
                count++;
            }
        }
        return count;
    }

    private static int countStyledColumns(byte style) {
        int count = 0;
        for (int index = 0; index < renderColumnCount; index++) {
            if (RENDER_STYLE[index] == style) {
                count++;
            }
        }
        return count;
    }

    private static void clearDistantCache() {
        distantShaftCount = 0;
        cachedDistantOriginX = Integer.MIN_VALUE;
        cachedDistantOriginZ = Integer.MIN_VALUE;
        cachedDistantRadius = -1;
        cachedCloudHeight = Integer.MIN_VALUE;
        cachedSequence = Long.MIN_VALUE;
        lastDistantBuildTick = Long.MIN_VALUE;
        cachedSettings = null;
    }

    /** Compact precipitation renderer values displayed on the F3 overlay. */
    public record Diagnostics(boolean active, int nearColumns, int distantShafts, int vertices) {
        public static final Diagnostics INACTIVE = new Diagnostics(false, 0, 0, 0);
    }
}
