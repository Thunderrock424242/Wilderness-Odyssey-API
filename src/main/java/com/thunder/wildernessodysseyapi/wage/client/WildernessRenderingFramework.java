package com.thunder.wildernessodysseyapi.wage.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.wage.EnvironmentState;
import com.thunder.wildernessodysseyapi.wage.RenderFrameContext;
import com.thunder.wildernessodysseyapi.wage.RenderingQuality;
import com.thunder.wildernessodysseyapi.wage.TemporalFrameData;
import com.thunder.wildernessodysseyapi.wage.backend.RenderBackend;
import com.thunder.wildernessodysseyapi.wage.backend.RenderBackends;
import com.thunder.wildernessodysseyapi.wage.compat.ShaderPackCompatibility;
import com.thunder.wildernessodysseyapi.wage.config.RendererConfig;
import com.thunder.wildernessodysseyapi.wage.performance.AdaptiveQualityController;
import com.thunder.wildernessodysseyapi.wage.performance.RenderQualityState;
import com.thunder.wildernessodysseyapi.wage.performance.AdaptiveGpuController;
import com.thunder.wildernessodysseyapi.wage.performance.FrameTimeWindow;
import com.thunder.wildernessodysseyapi.wage.performance.RenderWorkloadTimer;
import com.thunder.wildernessodysseyapi.opc.core.JvmMetrics;
import com.thunder.wildernessodysseyapi.weather.api.WindManager;
import com.thunder.wildernessodysseyapi.weather.api.WindSample;
import com.thunder.wildernessodysseyapi.weather.client.ClientWeatherCoordinator;
import com.thunder.wildernessodysseyapi.weather.client.WeatherVisualState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.List;
import java.util.ArrayList;
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
    private static final AdaptiveGpuController GPU_CONTROLLER = new AdaptiveGpuController();
    private static final RenderWorkloadTimer CLOUD_TIMER = new RenderWorkloadTimer();
    private static final RenderWorkloadTimer FRAME_TIMER = new RenderWorkloadTimer();
    private static final FrameTimeWindow FRAME_CADENCE = new FrameTimeWindow(240);
    private static final FrameTimeWindow CPU_FRAMES = new FrameTimeWindow(240);
    private static final FrameTimeWindow GPU_FRAMES = new FrameTimeWindow(240);
    private static final FrameTimeWindow POLICY_COST = new FrameTimeWindow(240);
    private static final FrameTimeWindow CLOUD_BASELINE = new FrameTimeWindow(240);
    private static final FrameTimeWindow CLOUD_OPTIMIZED = new FrameTimeWindow(240);
    private static final boolean[] CLOUD_MODES = new boolean[16];
    private static final long[] CLOUD_MODE_FRAMES = new long[16];

    private static volatile RenderFrameContext currentFrame = RenderFrameContext.EMPTY;
    private static long frameIndex;
    private static long frameStartedNanos;
    private static RenderBackend.GpuTimingSample latestGpuSample;
    private static long lastFrameTimingSequence;
    private static long activeGeneration = -1;
    private static TemporalFrameData.Resolution activeResolution;
    private static boolean externalOwnership;
    private static boolean wasMonitoring;
    private static long previousFrameStart;
    private static long cloudPassFrame = -1;
    private static boolean cloudPassOpen;
    private static boolean cloudDensityBounds;
    private static long lastCloudTimingSequence;
    private static long jvmSampleTime;
    private static long previousGcMillis = -1;
    private static double heapUsage = -1;
    private static double gcFraction = -1;

    private WildernessRenderingFramework() {
    }

    /** Captures static backend facts and one camera-local environment sample. */
    @SubscribeEvent
    public static void onFrameStart(RenderFrameEvent.Pre event) {
        long now = System.nanoTime();
        long nextFrameIndex = ++frameIndex;
        ShaderPackCompatibility.sampleFrame(nextFrameIndex);
        Minecraft minecraft = Minecraft.getInstance();
        RenderBackends.Selection selection = RenderBackends.selection();
        RenderBackend backend = selection.backend();
        TemporalFrameData.Resolution resolution = new TemporalFrameData.Resolution(
                minecraft.getWindow().getWidth(),
                minecraft.getWindow().getHeight()
        );
        boolean external = ShaderPackCompatibility.isExternalShaderPackActive();
        boolean monitoring = monitoringGpuEngine();
        if (selection.generation() != activeGeneration || !resolution.equals(activeResolution)
                || external != externalOwnership || monitoring != wasMonitoring) {
            invalidateGpuEngine();
            activeGeneration = selection.generation();
            activeResolution = resolution;
            externalOwnership = external;
            wasMonitoring = monitoring;
        }
        frameStartedNanos = now;
        cloudDensityBounds = false;
        if (minecraft.level != null) {
            FRAME_TIMER.begin(backend, selection.generation(), nextFrameIndex);
            if (!FRAME_TIMER.healthy()) latestGpuSample = null;
            if (monitoring && previousFrameStart != 0) FRAME_CADENCE.record(now - previousFrameStart);
            previousFrameStart = now;
        }
        RenderQualityState.Snapshot quality = RenderQualityState.snapshot();
        RenderFrameContext.FrameTiming previousTiming = currentFrame.timing();
        WeatherVisualState weatherVisual = sampleWeatherVisual(minecraft, nextFrameIndex);
        currentFrame = new RenderFrameContext(
                nextFrameIndex,
                backend,
                backend.capabilities(),
                sampleEnvironment(minecraft, weatherVisual),
                weatherVisual,
                quality.quality(),
                previousTiming,
                TemporalFrameData.unavailable(
                        resolution,
                        resolution,
                        previousTiming.cpuFrameNanos()
                ),
                selection.generation()
        );
        if (minecraft.level != null && monitoring) updateGpuPolicy(now);
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
        FRAME_TIMER.end();
        RenderBackend.GpuTimingSample completed = FRAME_TIMER.poll();
        RenderBackend.GpuTimingSample gpuSample = completed != null
                && completed.sequence() > lastFrameTimingSequence ? completed : null;
        if (!FRAME_TIMER.healthy()) {
            latestGpuSample = null;
            gpuSample = null;
        } else if (gpuSample != null) {
            latestGpuSample = gpuSample;
            lastFrameTimingSequence = gpuSample.sequence();
        }
        if (monitoringGpuEngine() && Minecraft.getInstance().level != null) {
            RenderBackend.GpuTimingSample cloud = CLOUD_TIMER.poll();
            if (cloud != null && cloud.sequence() > lastCloudTimingSequence) {
                lastCloudTimingSequence = cloud.sequence();
                int slot = (int) Math.floorMod(cloud.sourceFrame(), CLOUD_MODES.length);
                if (CLOUD_MODE_FRAMES[slot] == cloud.sourceFrame()) {
                    (CLOUD_MODES[slot] ? CLOUD_OPTIMIZED : CLOUD_BASELINE).record(cloud.durationNanos());
                }
            }
            CPU_FRAMES.record(elapsed);
            if (gpuSample != null) GPU_FRAMES.record(gpuSample.durationNanos());
        }

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
                before.weatherVisualState(),
                quality,
                new RenderFrameContext.FrameTiming(
                        elapsed,
                        gpuSample == null ? -1L : gpuSample.durationNanos(),
                        movingAverage,
                        gpuSample == null ? -1L : gpuSample.sourceFrame()
                ),
                temporal,
                before.backendGeneration()
        );
    }

    /** Returns the lock-free context for the current or most recently completed frame. */
    public static RenderFrameContext currentFrame() {
        return currentFrame;
    }

    /** On-demand render-thread policy diagnostics, including admission and fallback counters. */
    public static AdaptiveGpuController.Snapshot gpuEngineSnapshot() {
        return GPU_CONTROLLER.snapshot();
    }

    /** Formats framework facts only when the Rendering debug page requests them. */
    public static List<String> debugLines() {
        RenderFrameContext frame = currentFrame;
        RenderQualityState.Snapshot quality = RenderQualityState.snapshot();
        EnvironmentState environment = frame.environment();
        RenderBackends.BackendStatus backendStatus = RenderBackends.status();
        List<String> lines = new ArrayList<>(List.of(
                "WO backend: " + frame.gpuCapabilities().api().name().toLowerCase(Locale.ROOT)
                        + " #" + frame.backendGeneration()
                        + " | " + backendStatus.state().name().toLowerCase(Locale.ROOT)
                        + " | compute " + yesNo(frame.gpuCapabilities().supportsComputeShaders())
                        + " | GPU timing " + yesNo(frame.gpuCapabilities().supportsGpuTiming()),
                "WO capability paths: reflections " + yesNo(frame.gpuCapabilities().supportsAdvancedReflections())
                        + " | high volumetrics " + yesNo(frame.gpuCapabilities().supportsHighQualityVolumetrics()),
                String.format(
                        Locale.ROOT,
                        "WO frame: CPU %.3f ms | GPU %s | average %.3f ms | adaptive %s/%s",
                        frame.timing().cpuFrameNanos() / 1_000_000.0,
                        formatGpuTiming(frame),
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
                        ? "available" : "native path; motion/depth/color handoff unavailable"),
                "WO shader ownership: " + ShaderPackCompatibility.status().name().toLowerCase(Locale.ROOT)
                        + (ShaderPackCompatibility.isExternalShaderPackActive()
                        ? " | external world effects" : " | native world effects")
        ));
        AdaptiveGpuController.Snapshot gpu = GPU_CONTROLLER.snapshot();
        lines.add("WAGE: " + gpu.mode().name().toLowerCase(Locale.ROOT)
                + " | cloud density bounds " + (cloudDensityBounds ? "active" : "existing path")
                + " | " + gpu.reason().name().toLowerCase(Locale.ROOT)
                + " | upscaling native | generated frames unavailable");
        if (RendererConfig.gpuEngineSettings().debugDiagnostics()) {
            FrameTimeWindow.Snapshot cadence = FRAME_CADENCE.snapshot();
            FrameTimeWindow.Snapshot cpu = CPU_FRAMES.snapshot();
            FrameTimeWindow.Snapshot measuredGpu = GPU_FRAMES.snapshot();
            FrameTimeWindow.Snapshot scheduler = POLICY_COST.snapshot();
            lines.add(String.format(Locale.ROOT,
                    "WAGE cadence: %.1f FPS | median/p95/p99 %.2f/%.2f/%.2f ms | %d frames",
                    cadence.framesPerSecond(), cadence.medianNanos() / 1e6,
                    cadence.p95Nanos() / 1e6, cadence.p99Nanos() / 1e6, cadence.samples()));
            lines.add(String.format(Locale.ROOT,
                    "WAGE elapsed CPU avg %.3f ms | GPU avg %s | cloud avg/budget %.3f/%.3f ms",
                    cpu.averageNanos() / 1e6,
                    measuredGpu.samples() == 0 ? "unavailable" : String.format(Locale.ROOT, "%.3f ms", measuredGpu.averageNanos() / 1e6),
                    gpu.cloudAverageNanos() / 1e6, gpu.cloudBudgetNanos() / 1e6));
            lines.add(String.format(Locale.ROOT,
                    "WAGE admitted/rejected %d/%d | fallbacks %d | policy avg/p99 %.2f/%.2f us | timer failures %d",
                    gpu.accepted(), gpu.rejected(), gpu.fallbacks(), scheduler.averageNanos() / 1e3,
                    scheduler.p99Nanos() / 1e3, CLOUD_TIMER.failures() + FRAME_TIMER.failures()));
            FrameTimeWindow.Snapshot baseline = CLOUD_BASELINE.snapshot();
            FrameTimeWindow.Snapshot optimized = CLOUD_OPTIMIZED.snapshot();
            lines.add(String.format(Locale.ROOT, "WAGE cloud baseline/optimized avg %s/%s ms | samples %d/%d (scene-dependent)",
                    baseline.samples() == 0 ? "unavailable" : String.format(Locale.ROOT, "%.3f", baseline.averageNanos() / 1e6),
                    optimized.samples() == 0 ? "unavailable" : String.format(Locale.ROOT, "%.3f", optimized.averageNanos() / 1e6),
                    baseline.samples(), optimized.samples()));
            lines.add("WAGE VRAM pressure: unavailable (capacity is not pressure); heap/GC fraction "
                    + formatFraction(heapUsage) + "/" + formatFraction(gcFraction)
                    + " | terrain remains renderer-owned | no retained GPU task queue");
        }
        return List.copyOf(lines);
    }

    /** Clears world-specific presentation state without discarding cached backend facts. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            invalidateGpuEngine();
            currentFrame = RenderFrameContext.EMPTY;
            frameStartedNanos = 0L;
            QUALITY_CONTROLLER.recordFrame(0, 0, AdaptiveQualityController.Policy.DISABLED);
            RenderQualityState.publish(false, RenderingQuality.CINEMATIC, 0);
        }
    }

    /** Begins the single existing cloud draw; timing and acceleration are independently optional. */
    public static void beginCloudPass() {
        cloudDensityBounds = false;
        if (!monitoringGpuEngine() || ShaderPackCompatibility.isExternalShaderPackActive()
                || cloudPassOpen || cloudPassFrame == currentFrame.frameIndex()) return;
        cloudPassFrame = currentFrame.frameIndex();
        CLOUD_TIMER.begin(currentFrame.backend(), currentFrame.backendGeneration(), cloudPassFrame);
        cloudPassOpen = true;
        cloudDensityBounds = GPU_CONTROLLER.tryAcquireCloud(cloudPassFrame,
                CLOUD_TIMER.healthy() && FRAME_TIMER.healthy());
        int slot = (int) Math.floorMod(cloudPassFrame, CLOUD_MODES.length);
        CLOUD_MODE_FRAMES[slot] = cloudPassFrame;
        CLOUD_MODES[slot] = cloudDensityBounds;
    }

    public static boolean cloudDensityBoundsEnabled() { return cloudDensityBounds; }

    /** Always paired in finally by the cloud owner, including failed draws. */
    public static void endCloudPass() {
        if (cloudPassOpen) CLOUD_TIMER.end();
        cloudPassOpen = false;
    }

    /** Resource reload, context replacement and world unload invalidate all delayed observations. */
    private static void invalidateGpuEngine() {
        FRAME_TIMER.close();
        CLOUD_TIMER.close();
        GPU_CONTROLLER.reset();
        latestGpuSample = null;
        lastFrameTimingSequence = 0;
        cloudPassFrame = -1;
        cloudPassOpen = false;
        cloudDensityBounds = false;
        previousFrameStart = 0;
        FRAME_CADENCE.clear();
        CPU_FRAMES.clear();
        GPU_FRAMES.clear();
        POLICY_COST.clear();
        CLOUD_BASELINE.clear();
        CLOUD_OPTIMIZED.clear();
        java.util.Arrays.fill(CLOUD_MODE_FRAMES, -1);
        lastCloudTimingSequence = 0;
        jvmSampleTime = 0;
        previousGcMillis = -1;
        heapUsage = gcFraction = -1;
    }

    private static boolean monitoringGpuEngine() {
        RendererConfig.GpuEngineSettings settings = RendererConfig.gpuEngineSettings();
        return settings.active() || settings.debugDiagnostics();
    }

    private static void updateGpuPolicy(long now) {
        long started = System.nanoTime();
        RendererConfig.GpuEngineSettings settings = RendererConfig.gpuEngineSettings();
        if (settings.active() && (jvmSampleTime == 0 || now - jvmSampleTime >= 1_000_000_000L)) {
            JvmMetrics.Sample raw = JvmMetrics.shared().samplePressure(now);
            heapUsage = raw.heapMaximumBytes() > 0 && raw.heapUsedBytes() >= 0
                    ? raw.heapUsedBytes() / (double) raw.heapMaximumBytes() : -1;
            gcFraction = previousGcMillis >= 0 && raw.gcCollectionMillis() >= previousGcMillis
                    && raw.nanoTime() > jvmSampleTime
                    ? Math.min(1, (raw.gcCollectionMillis() - previousGcMillis) * 1e6 / (raw.nanoTime() - jvmSampleTime)) : -1;
            previousGcMillis = raw.gcCollectionMillis();
            jvmSampleTime = raw.nanoTime();
        }
        RenderBackend.GpuTimingSample cloud = CLOUD_TIMER.latest();
        RendererConfig.Settings quality = RendererConfig.settings();
        GPU_CONTROLLER.observe(new AdaptiveGpuController.Observation(currentFrame.frameIndex(), now,
                currentFrame.backendGeneration(), currentFrame.temporalData().renderResolution(),
                currentFrame.gpuCapabilities(), externalOwnership, currentFrame.timing().cpuFrameNanos(),
                latestGpuSample == null ? -1 : latestGpuSample.durationNanos(),
                latestGpuSample == null ? -1 : latestGpuSample.sourceFrame(),
                cloud == null ? -1 : cloud.durationNanos(), cloud == null ? -1 : cloud.sourceFrame(),
                heapUsage, gcFraction), new AdaptiveGpuController.Policy(settings.active(), settings.mode(),
                quality.targetFrameNanos(), quality.cooldownNanos()));
        POLICY_COST.record(System.nanoTime() - started);
    }

    private static String formatFraction(double value) {
        return value < 0 ? "unavailable" : String.format(Locale.ROOT, "%.3f", value);
    }

    /** Called by the existing client setup owner; apply uses Minecraft's resource lifecycle. */
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> invalidateGpuEngine());
    }

    private static EnvironmentState sampleEnvironment(
            Minecraft minecraft,
            WeatherVisualState weatherVisual
    ) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            return EnvironmentState.CLEAR;
        }
        if (ClientWeatherCoordinator.controls(level)) {
            return EnvironmentState.from(weatherVisual);
        }
        var position = minecraft.gameRenderer.getMainCamera().getPosition();
        WindSample wind = WindManager.getWind(level, position);
        return EnvironmentState.vanilla(
                level.getRainLevel(0.0F),
                level.getThunderLevel(0.0F),
                wind
        );
    }

    private static WeatherVisualState sampleWeatherVisual(Minecraft minecraft, long nextFrameIndex) {
        ClientLevel level = minecraft.level;
        if (level == null || !ClientWeatherCoordinator.controls(level)) {
            return WeatherVisualState.CLEAR;
        }
        return ClientWeatherCoordinator.visualState(
                level,
                minecraft.gameRenderer.getMainCamera().getPosition(),
                nextFrameIndex
        );
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static String formatGpuTiming(RenderFrameContext frame) {
        RenderFrameContext.FrameTiming timing = frame.timing();
        if (timing.gpuFrameNanos() < 0L) {
            return "pending/unavailable";
        }
        long age = timing.gpuSampleFrame() < 0L
                ? -1L
                : Math.max(0L, frame.frameIndex() - timing.gpuSampleFrame());
        return String.format(
                Locale.ROOT,
                age < 0L ? "%.3f ms" : "%.3f ms (%d frames old)",
                timing.gpuFrameNanos() / 1_000_000.0,
                age
        );
    }
}
