package com.thunder.wildernessodysseyapi.weather.client.cloud;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.weather.api.CloudType;
import com.thunder.wildernessodysseyapi.weather.client.ClientWeatherCoordinator;
import com.thunder.wildernessodysseyapi.weather.config.WeatherRenderingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;

/**
 * Owns double-buffered near and distant cloud-field textures on the render thread.
 *
 * <p>Every refresh swaps the old target into the previous slot. Each atlas
 * contains physical field data plus blendable morphology weights. The shader
 * samples both atlases with their independent world origins and blends across
 * the refresh interval, preventing snapshot and recentering pops.</p>
 */
public final class ContinuousCloudFieldAtlas {

    private static final ResourceLocation FIRST_LOCATION = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID, "dynamic/cloud_field_first"
    );
    private static final ResourceLocation SECOND_LOCATION = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID, "dynamic/cloud_field_second"
    );

    private static DynamicTexture previousTexture;
    private static DynamicTexture currentTexture;
    private static CloudFieldAtlasModel.Layout previousLayout;
    private static CloudFieldAtlasModel.Layout currentLayout;
    private static ClientLevel activeLevel;
    private static WeatherRenderingConfig.Settings builtSettings;
    private static long builtSequence = Long.MIN_VALUE;
    private static long lastRefreshTick = Long.MIN_VALUE;
    private static double builtInterpolationProgress = -1.0;
    private static double transitionStartTicks;
    private static Statistics statistics = Statistics.EMPTY;

    private ContinuousCloudFieldAtlas() {
    }

    /** Refreshes the target atlas when weather, layout, or temporal blending requires it. */
    public static State update(
            ClientLevel level,
            ClientWeatherCoordinator.ClientStateView weatherState,
            WeatherRenderingConfig.Settings settings,
            double cameraX,
            double cameraZ,
            double renderTicks
    ) {
        if (level == null || weatherState == null || settings == null) {
            clear();
            return State.INACTIVE;
        }
        if (activeLevel != level) {
            clear();
            activeLevel = level;
        }

        CloudFieldAtlasModel.Layout desiredLayout = currentLayout;
        if (desiredLayout == null
                || !settings.equals(builtSettings)
                || CloudFieldAtlasModel.shouldRecenter(desiredLayout, cameraX, cameraZ)) {
            desiredLayout = CloudFieldAtlasModel.layout(
                    cameraX,
                    cameraZ,
                    settings.renderDistanceBlocks(),
                    settings.distantCloudDistanceBlocks(),
                    settings.distantCloudSpacingBlocks(),
                    settings.raymarchSteps(),
                    settings.distantCloudLayer()
            );
        }

        long tick = (long) Math.floor(renderTicks);
        long elapsed = tick >= lastRefreshTick ? tick - lastRefreshTick : Long.MAX_VALUE;
        boolean blendingWeather = weatherState.interpolationProgress() < 0.999;
        boolean needsFinalBlendedFrame = !blendingWeather && builtInterpolationProgress < 0.999;
        boolean refresh = currentLayout == null
                || !desiredLayout.equals(currentLayout)
                || !settings.equals(builtSettings)
                || weatherState.sequence() != builtSequence
                || needsFinalBlendedFrame
                || (blendingWeather && elapsed >= settings.rebuildIntervalTicks());
        if (refresh) {
            Frame frame = buildFrame(level, settings, desiredLayout);
            uploadFrame(frame, renderTicks);
            currentLayout = desiredLayout;
            builtSettings = settings;
            builtSequence = weatherState.sequence();
            builtInterpolationProgress = weatherState.interpolationProgress();
            lastRefreshTick = tick;
        }

        if (currentLayout == null || previousLayout == null
                || previousTexture == null || currentTexture == null) {
            return State.INACTIVE;
        }
        float blend = (float) Math.max(0.0, Math.min(1.0,
                (renderTicks - transitionStartTicks) / Math.max(2.0, settings.rebuildIntervalTicks())));
        return new State(true, previousLayout, currentLayout, blend, statistics);
    }

    /** Binds both field textures directly to the shader's named sampler slots. */
    public static void bindSamplers(ShaderInstance shader) {
        if (shader == null || previousTexture == null || currentTexture == null) {
            return;
        }
        shader.setSampler("CloudFieldPrevious", previousTexture.getId());
        shader.setSampler("CloudFieldCurrent", currentTexture.getId());
    }

    /** Releases render-thread texture ownership during reloads and level handoffs. */
    public static void clear() {
        DynamicTexture oldPrevious = previousTexture;
        DynamicTexture oldCurrent = currentTexture;
        previousTexture = null;
        currentTexture = null;
        previousLayout = null;
        currentLayout = null;
        activeLevel = null;
        builtSettings = null;
        builtSequence = Long.MIN_VALUE;
        lastRefreshTick = Long.MIN_VALUE;
        builtInterpolationProgress = -1.0;
        transitionStartTicks = 0.0;
        statistics = Statistics.EMPTY;
        if (oldPrevious == null && oldCurrent == null) {
            return;
        }
        if (RenderSystem.isOnRenderThread()) {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.getTextureManager().release(FIRST_LOCATION);
            minecraft.getTextureManager().release(SECOND_LOCATION);
        } else {
            RenderSystem.recordRenderCall(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.getTextureManager().release(FIRST_LOCATION);
                minecraft.getTextureManager().release(SECOND_LOCATION);
            });
        }
    }

    private static Frame buildFrame(
            ClientLevel level,
            WeatherRenderingConfig.Settings settings,
            CloudFieldAtlasModel.Layout layout
    ) {
        int[] pixels = new int[layout.atlasWidth() * layout.atlasHeight()];
        Map<CloudType, Double> typeWeights = new EnumMap<>(CloudType.class);
        MutableStatistics totals = new MutableStatistics();
        sampleRegion(level, settings, layout, false, pixels, typeWeights, totals);
        if (layout.hasDistantField()) {
            sampleRegion(level, settings, layout, true, pixels, typeWeights, totals);
        }
        CloudType dominant = CloudType.CLEAR;
        double maximumWeight = -1.0;
        for (Map.Entry<CloudType, Double> entry : typeWeights.entrySet()) {
            if (entry.getValue() > maximumWeight) {
                dominant = entry.getKey();
                maximumWeight = entry.getValue();
            }
        }
        Statistics frameStatistics = new Statistics(
                totals.visibleSamples,
                totals.visibleSamples == 0 ? 0.0 : totals.coverageSum / totals.visibleSamples,
                totals.bandMask,
                dominant.displayName()
        );
        return new Frame(layout, pixels, frameStatistics);
    }

    private static void sampleRegion(
            ClientLevel level,
            WeatherRenderingConfig.Settings settings,
            CloudFieldAtlasModel.Layout layout,
            boolean distant,
            int[] pixels,
            Map<CloudType, Double> typeWeights,
            MutableStatistics totals
    ) {
        int dimension = distant ? layout.distantDimension() : layout.nearDimension();
        int spacing = distant ? layout.distantSpacingBlocks() : layout.nearSpacingBlocks();
        int originX = distant ? layout.distantOriginBlockX() : layout.nearOriginBlockX();
        int originZ = distant ? layout.distantOriginBlockZ() : layout.nearOriginBlockZ();
        for (int z = 0; z < dimension; z++) {
            for (int x = 0; x < dimension; x++) {
                double worldX = originX + (double) x * spacing;
                double worldZ = originZ + (double) z * spacing;
                CloudFieldSample field = ClientWeatherCoordinator.cloudFieldAt(level, worldX, worldZ);
                CloudLayerProfile profile = CloudLayerProfile.evaluate(field);
                double fieldOpacity = CloudCoverageModel.opacity(field, settings.opacityMultiplier());
                double typeWeight = Math.max(0.0, CloudCoverageModel.coverage(field));
                if (!distant && typeWeight > 0.0) {
                    typeWeights.merge(profile.dominantType(), typeWeight, Double::sum);
                }
                for (CloudLayerProfile.BandProfile band : profile.bands()) {
                    double coverage = band.visible() ? fieldOpacity * band.density() : 0.0;
                    if (distant) {
                        coverage *= 0.50 + field.stormEnergy() * 0.24;
                    }
                    int row = CloudFieldAtlasModel.atlasRow(layout, distant, band.band().ordinal(), z);
                    pixels[row * layout.atlasWidth() + x] = CloudFieldAtlasModel.packPixel(
                            coverage,
                            band.baseOffsetBlocks(),
                            band.depthBlocks(),
                            field.stormEnergy()
                    );
                    int morphologyRow = CloudFieldAtlasModel.morphologyAtlasRow(
                            layout,
                            distant,
                            band.band().ordinal(),
                            z
                    );
                    pixels[morphologyRow * layout.atlasWidth() + x] =
                            CloudFieldAtlasModel.packMorphologyPixel(
                                    band.visible() ? band.shape() : CloudType.Shape.CLEAR
                            );
                    if (coverage > 0.015) {
                        totals.visibleSamples++;
                        totals.coverageSum += coverage;
                        totals.bandMask |= 1 << band.band().ordinal();
                    }
                }
            }
        }
    }

    private static void uploadFrame(Frame frame, double renderTicks) {
        ensureTextures(frame.layout().atlasWidth(), frame.layout().atlasHeight());
        if (previousLayout == null) {
            writeTexture(previousTexture, frame);
            writeTexture(currentTexture, frame);
            previousLayout = frame.layout();
            transitionStartTicks = renderTicks - 1_000.0;
        } else {
            DynamicTexture swap = previousTexture;
            previousTexture = currentTexture;
            currentTexture = swap;
            previousLayout = currentLayout;
            writeTexture(currentTexture, frame);
            transitionStartTicks = renderTicks;
        }
        statistics = frame.statistics();
    }

    private static void ensureTextures(int width, int height) {
        NativeImage currentPixels = currentTexture == null ? null : currentTexture.getPixels();
        if (currentPixels != null && currentPixels.getWidth() == width && currentPixels.getHeight() == height) {
            return;
        }
        if (previousTexture != null || currentTexture != null) {
            Minecraft.getInstance().getTextureManager().release(FIRST_LOCATION);
            Minecraft.getInstance().getTextureManager().release(SECOND_LOCATION);
        }
        previousTexture = new DynamicTexture(width, height, false);
        currentTexture = new DynamicTexture(width, height, false);
        previousTexture.setFilter(true, false);
        currentTexture.setFilter(true, false);
        Minecraft.getInstance().getTextureManager().register(FIRST_LOCATION, previousTexture);
        Minecraft.getInstance().getTextureManager().register(SECOND_LOCATION, currentTexture);
        previousLayout = null;
    }

    private static void writeTexture(DynamicTexture texture, Frame frame) {
        NativeImage image = texture.getPixels();
        if (image == null) {
            return;
        }
        image.fillRect(0, 0, image.getWidth(), image.getHeight(), 0);
        int[] pixels = frame.pixels();
        int width = frame.layout().atlasWidth();
        int height = frame.layout().atlasHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixelRGBA(x, y, pixels[y * width + x]);
            }
        }
        texture.upload();
    }

    /** Immutable render metadata used by uniforms and F3 diagnostics. */
    public record State(
            boolean active,
            CloudFieldAtlasModel.Layout previousLayout,
            CloudFieldAtlasModel.Layout currentLayout,
            float blend,
            Statistics statistics
    ) {
        private static final State INACTIVE = new State(false, null, null, 0.0F, Statistics.EMPTY);
    }

    /** Compact atlas occupancy and cloud-family diagnostics. */
    public record Statistics(int visibleSamples, double averageCoverage, int bandMask, String cloudType) {
        private static final Statistics EMPTY = new Statistics(0, 0.0, 0, "Clear");
    }

    private record Frame(CloudFieldAtlasModel.Layout layout, int[] pixels, Statistics statistics) {
    }

    private static final class MutableStatistics {
        private int visibleSamples;
        private double coverageSum;
        private int bandMask;
    }
}
