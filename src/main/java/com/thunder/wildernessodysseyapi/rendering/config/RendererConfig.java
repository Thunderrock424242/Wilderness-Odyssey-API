package com.thunder.wildernessodysseyapi.rendering.config;

import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import com.thunder.wildernessodysseyapi.rendering.RenderingQuality;
import com.thunder.wildernessodysseyapi.rendering.performance.AdaptiveGpuController;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Shared client-side performance policy for Wilderness rendering systems. */
public final class RendererConfig {

    public static ModConfigSpec CONFIG_SPEC;
    public static ModConfigSpec.BooleanValue ADAPTIVE_QUALITY;
    public static ModConfigSpec.DoubleValue TARGET_FRAME_TIME;
    public static ModConfigSpec.EnumValue<RenderingQuality> MINIMUM_QUALITY;
    public static ModConfigSpec.EnumValue<RenderingQuality> MAXIMUM_QUALITY;
    public static ModConfigSpec.DoubleValue QUALITY_CHANGE_COOLDOWN;
    public static ModConfigSpec.BooleanValue GPU_ENGINE_ENABLED;
    public static ModConfigSpec.EnumValue<AdaptiveGpuController.Mode> GPU_ENGINE_MODE;
    public static ModConfigSpec.BooleanValue EFFECT_ACCELERATION;
    public static ModConfigSpec.BooleanValue GPU_ENGINE_DIAGNOSTICS;

    private static final Settings DEFAULTS = new Settings(
            false,
            16.67,
            RenderingQuality.LOW,
            RenderingQuality.CINEMATIC,
            5.0
    );
    private static volatile Settings activeSettings = DEFAULTS;
    private static volatile GpuEngineSettings gpuSettings = new GpuEngineSettings(
            false, AdaptiveGpuController.Mode.AUTOMATIC, true, false);

    static {
        WildernessConfigSpecs.initialize();
    }

    private RendererConfig() {
    }

    /** Defines keys beneath the unified client config's {@code renderer} category. */
    public static void define(ModConfigSpec.Builder builder) {
        ADAPTIVE_QUALITY = builder
                .comment("Gradually reduce expensive Wilderness water/weather effects when the measured frame time misses the target. This never rewrites saved feature settings.")
                .define("adaptiveQuality", false);
        TARGET_FRAME_TIME = builder
                .comment("Target client frame time in milliseconds for adaptive quality and the optional GPU engine.")
                .defineInRange("targetFrameTime", 16.67, 5.0, 100.0);
        MINIMUM_QUALITY = builder
                .comment("Lowest transient effect ceiling adaptive quality may select.")
                .defineEnum("minimumQuality", RenderingQuality.LOW);
        MAXIMUM_QUALITY = builder
                .comment("Highest transient effect ceiling adaptive quality may select. Feature-specific settings remain stricter ceilings.")
                .defineEnum("maximumQuality", RenderingQuality.CINEMATIC);
        QUALITY_CHANGE_COOLDOWN = builder
                .comment("Minimum seconds between adaptive quality changes. Smoothing and hysteresis also prevent oscillation.")
                .defineInRange("qualityChangeCooldown", 5.0, 1.0, 30.0);
        builder.push("gpuEngine");
        GPU_ENGINE_ENABLED = builder
                .comment("Enable experimental measured cloud shader optimization. Default off until hardware A/B validation; does not change terrain or gameplay.")
                .define("enabled", false);
        GPU_ENGINE_MODE = builder
                .comment("AUTOMATIC, PERFORMANCE and QUALITY select different cloud timing budgets. DISABLED retains existing rendering.")
                .defineEnum("mode", AdaptiveGpuController.Mode.AUTOMATIC);
        EFFECT_ACCELERATION = builder
                .comment("Allow bounded cloud density noise rejection when fresh measurements show sustained cloud/GPU pressure.")
                .define("effectAcceleration", true);
        GPU_ENGINE_DIAGNOSTICS = builder
                .comment("Show detailed GPU engine timings and frame percentiles on the Rendering debug page; no per-frame log output.")
                .define("debugDiagnostics", false);
        builder.pop();
    }

    public static Settings settings() {
        return activeSettings;
    }

    public static GpuEngineSettings gpuEngineSettings() { return gpuSettings; }

    /** Refreshes the immutable render-thread settings snapshot after config load/reload. */
    public static void reload() {
        activeSettings = new Settings(
                ADAPTIVE_QUALITY.get(),
                TARGET_FRAME_TIME.get(),
                MINIMUM_QUALITY.get(),
                MAXIMUM_QUALITY.get(),
                QUALITY_CHANGE_COOLDOWN.get()
        );
        gpuSettings = new GpuEngineSettings(GPU_ENGINE_ENABLED.get(), GPU_ENGINE_MODE.get(),
                EFFECT_ACCELERATION.get(), GPU_ENGINE_DIAGNOSTICS.get());
    }

    /** Separate snapshot keeps the existing public Settings constructor compatible. */
    public record GpuEngineSettings(boolean enabled, AdaptiveGpuController.Mode mode,
            boolean effectAcceleration, boolean debugDiagnostics) {
        public GpuEngineSettings {
            mode = mode == null ? AdaptiveGpuController.Mode.DISABLED : mode;
        }
        public boolean active() { return enabled && effectAcceleration && mode != AdaptiveGpuController.Mode.DISABLED; }
    }

    public record Settings(
            boolean adaptiveQuality,
            double targetFrameTimeMilliseconds,
            RenderingQuality minimumQuality,
            RenderingQuality maximumQuality,
            double qualityChangeCooldownSeconds
    ) {
        public Settings {
            targetFrameTimeMilliseconds = finiteClamp(targetFrameTimeMilliseconds, 5.0, 100.0);
            minimumQuality = minimumQuality == null ? RenderingQuality.LOW : minimumQuality;
            maximumQuality = maximumQuality == null ? RenderingQuality.CINEMATIC : maximumQuality;
            if (minimumQuality.ordinal() > maximumQuality.ordinal()) {
                RenderingQuality swap = minimumQuality;
                minimumQuality = maximumQuality;
                maximumQuality = swap;
            }
            qualityChangeCooldownSeconds = finiteClamp(qualityChangeCooldownSeconds, 1.0, 30.0);
        }

        public long targetFrameNanos() {
            return Math.round(targetFrameTimeMilliseconds * 1_000_000.0);
        }

        public long cooldownNanos() {
            return Math.round(qualityChangeCooldownSeconds * 1_000_000_000.0);
        }

        private static double finiteClamp(double value, double minimum, double maximum) {
            double safe = Double.isFinite(value) ? value : minimum;
            return Math.max(minimum, Math.min(maximum, safe));
        }
    }
}
