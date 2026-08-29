package com.thunder.wildernessodysseyapi.rendering.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.rendering.EnvironmentState;
import com.thunder.wildernessodysseyapi.rendering.RenderFrameContext;
import com.thunder.wildernessodysseyapi.rendering.RenderingQuality;
import com.thunder.wildernessodysseyapi.rendering.TemporalFrameData;
import com.thunder.wildernessodysseyapi.rendering.backend.RenderBackend;
import com.thunder.wildernessodysseyapi.rendering.backend.RenderBackends;
import com.thunder.wildernessodysseyapi.rendering.config.RendererConfig;
import com.thunder.wildernessodysseyapi.rendering.performance.AdaptiveQualityController;
import com.thunder.wildernessodysseyapi.rendering.performance.RenderQualityState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindManager;
import com.thunder.wildernessodysseyapi.weather.api.WindSample;
import com.thunder.wildernessodysseyapi.weather.client.ClientWeatherCoordinator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.List;
import java.util.Locale;

/**
 * Publishes one shared, immutable client rendering context per frame.
 *
 * <p>Minecraft remains responsible for render-pass order and command
 * submission. This coordinator only samples existing owners, records frame
 * time, and publishes transient capability/quality decisions.</p>
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class WildernessRenderingFramework {

    private static final AdaptiveQualityController QUALITY_CONTROLLER = new AdaptiveQualityController();

    private static volatile RenderFrameContext currentFrame = RenderFrameContext.EMPTY;
    private static long frameIndex;
    private static long frameStartedNanos;

    private WildernessRenderingFramework() {
    }

    /** Captures static backend facts and one camera-local environment sample. */
    @SubscribeEvent
    public static void onFrameStart(RenderFrameEvent.Pre event) {
        frameStartedNanos = System.nanoTime();
        Minecraft minecraft = Minecraft.getInstance();
        RenderBackend backend = RenderBackends.current();
        TemporalFrameData.Resolution resolution = new TemporalFrameData.Resolution(
                minecraft.getWindow().getWidth(),
                minecraft.getWindow().getHeight()
        );
        RenderQualityState.Snapshot quality = RenderQualityState.snapshot();
        RenderFrameContext.FrameTiming previousTiming = currentFrame.timing();
        currentFrame = new RenderFrameContext(
                ++frameIndex,
                backend,
                backend.capabilities(),
                sampleEnvironment(minecraft),
                quality.quality(),
                previousTiming,
                TemporalFrameData.unavailable(
                        resolution,
                        resolution,
                        previousTiming.cpuFrameNanos()
                )
        );
    }

    /** Publishes measured CPU frame time and advances opt-in adaptive quality. */
    @SubscribeEvent
    public static void onFrameEnd(RenderFrameEvent.Post event) {
        if (frameStartedNanos <= 0L) {
            return;
        }
        long now = System.nanoTime();
        long elapsed = Math.max(0L, now - frameStartedNanos);
        frameStartedNanos = 0L;

        RendererConfig.Settings settings = RendererConfig.settings();
        RenderingQuality quality = RenderingQuality.CINEMATIC;
        Minecraft minecraft = Minecraft.getInstance();
        if (settings.adaptiveQuality() && minecraft.level != null) {
            quality = QUALITY_CONTROLLER.recordFrame(
                    elapsed,
                    now,
                    new AdaptiveQualityController.Policy(
                            true,
                            settings.targetFrameNanos(),
                            settings.minimumQuality(),
                            settings.maximumQuality(),
                            settings.cooldownNanos()
                    )
            );
        } else if (!settings.adaptiveQuality()) {
            QUALITY_CONTROLLER.recordFrame(elapsed, now, AdaptiveQualityController.Policy.DISABLED);
        } else {
            quality = QUALITY_CONTROLLER.quality();
        }
        long movingAverage = QUALITY_CONTROLLER.movingAverageNanos();
        RenderQualityState.publish(settings.adaptiveQuality(), quality, movingAverage);

        RenderFrameContext before = currentFrame;
        TemporalFrameData temporal = TemporalFrameData.unavailable(
                before.temporalData().renderResolution(),
                before.temporalData().outputResolution(),
                elapsed
        );
        currentFrame = new RenderFrameContext(
                before.frameIndex(),
                before.backend(),
                before.gpuCapabilities(),
                before.environment(),
                quality,
                new RenderFrameContext.FrameTiming(elapsed, -1L, movingAverage),
                temporal
        );
    }

    /** Returns the lock-free context for the current or most recently completed frame. */
    public static RenderFrameContext currentFrame() {
        return currentFrame;
    }

    /** Formats framework facts only when the Rendering debug page requests them. */
    public static List<String> debugLines() {
        RenderFrameContext frame = currentFrame;
        RenderQualityState.Snapshot quality = RenderQualityState.snapshot();
        EnvironmentState environment = frame.environment();
        return List.of(
                "WO backend: " + frame.gpuCapabilities().api().name().toLowerCase(Locale.ROOT)
                        + " | compute " + yesNo(frame.gpuCapabilities().supportsComputeShaders())
                        + " | GPU timing " + yesNo(frame.gpuCapabilities().supportsGpuTiming()),
                "WO capability paths: reflections " + yesNo(frame.gpuCapabilities().supportsAdvancedReflections())
                        + " | high volumetrics " + yesNo(frame.gpuCapabilities().supportsHighQualityVolumetrics()),
                String.format(
                        Locale.ROOT,
                        "WO frame: CPU %.3f ms | average %.3f ms | adaptive %s/%s",
                        frame.timing().cpuFrameNanos() / 1_000_000.0,
                        quality.averageFrameNanos() / 1_000_000.0,
                        quality.enabled() ? "on" : "off",
                        quality.quality().name().toLowerCase(Locale.ROOT)
                ),
                String.format(
                        Locale.ROOT,
                        "WO environment: rain %.2f snow %.2f storm %.2f wet %.2f wind %.2f @ %.2f,%.2f",
                        environment.rainIntensity(),
                        environment.snowIntensity(),
                        environment.stormIntensity(),
                        environment.wetness(),
                        environment.windSpeed(),
                        environment.windDirectionX(),
                        environment.windDirectionZ()
                ),
                "WO temporal inputs: "
                        + (frame.temporalData().hasTemporalReconstructionInputs()
                        ? "available" : "native path; motion/depth/color handoff unavailable")
        );
    }

    /** Clears world-specific presentation state without discarding cached backend facts. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            currentFrame = RenderFrameContext.EMPTY;
            frameStartedNanos = 0L;
        }
    }

    private static EnvironmentState sampleEnvironment(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            return EnvironmentState.CLEAR;
        }
        var position = minecraft.gameRenderer.getMainCamera().getPosition();
        WindSample wind = WindManager.getWind(level, position);
        if (ClientWeatherCoordinator.controls(level)) {
            WeatherSample weather = ClientWeatherCoordinator.sampleAt(level, position);
            return EnvironmentState.from(weather, wind);
        }
        return EnvironmentState.vanilla(
                level.getRainLevel(0.0F),
                level.getThunderLevel(0.0F),
                wind
        );
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
