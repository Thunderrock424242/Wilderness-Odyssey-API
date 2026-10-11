package com.thunder.wildernessodysseyapi.anomaly.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common-safe client config surface; all options affect presentation only. */
public final class AnomalyClientConfig {
    public static ModConfigSpec CONFIG_SPEC;
    private static ModConfigSpec.BooleanValue FOG;
    private static ModConfigSpec.BooleanValue PARTICLES;
    private static ModConfigSpec.BooleanValue COLOR_GRADING;
    private static ModConfigSpec.BooleanValue POST_EFFECTS;
    private static ModConfigSpec.BooleanValue MOTION_DISTORTION;
    private static ModConfigSpec.BooleanValue PULSES;
    private static ModConfigSpec.DoubleValue INTENSITY_SCALE;
    private static volatile float accessibilityScale = 1.0F;

    private AnomalyClientConfig() {
    }

    /** Defines the anomaly presentation category in the unified client spec. */
    public static void define(ModConfigSpec.Builder builder) {
        builder.comment("Client presentation for server-synchronized anomaly zones. Gameplay is unchanged.")
                .push("anomaly");
        FOG = builder.comment("Blend a restrained cool tint into air fog only.").define("fog", true);
        PARTICLES = builder.comment("Allow sparse ambient motes and distorted-block crack particles.")
                .define("particles", true);
        COLOR_GRADING = builder.comment("Apply subtle cyan/violet scene grading when the post pass is available.")
                .define("colorGrading", true);
        POST_EFFECTS = builder.comment("Enable the independently owned anomaly post pass. Active shader packs keep ownership.")
                .define("postEffects", true);
        MOTION_DISTORTION = builder.comment("Allow tiny, slow local-rift displacement pulses. Off by default for comfort.")
                .define("motionDistortion", false);
        PULSES = builder.comment("Allow low-frequency crack and motion pulses. Screen Effects at zero also disables them.")
                .define("pulses", true);
        INTENSITY_SCALE = builder.comment("Visual intensity multiplier from 0 to 1; does not change server state.")
                .defineInRange("intensityScale", 1.0, 0.0, 1.0);
        builder.pop();
    }

    public static void attachSpec(ModConfigSpec spec) {
        CONFIG_SPEC = spec;
    }

    public static boolean fogEnabled() {
        return value(FOG, true);
    }

    public static boolean particlesEnabled() {
        return value(PARTICLES, true);
    }

    /** Safe for block animateTick code without linking Minecraft client classes. */
    public static boolean crackParticlesEnabled() {
        return particlesEnabled() && pulsesEnabled() && intensityScale() > 0.01F && accessibilityScale > 0.01F;
    }

    public static boolean colorGradingEnabled() {
        return value(COLOR_GRADING, true);
    }

    public static boolean postEffectsEnabled() {
        return value(POST_EFFECTS, true);
    }

    public static boolean motionDistortionEnabled() {
        return value(MOTION_DISTORTION, false);
    }

    public static boolean pulsesEnabled() {
        return value(PULSES, true) && accessibilityScale > 0.01F;
    }

    public static float intensityScale() {
        if (INTENSITY_SCALE == null) {
            return 1.0F;
        }
        try {
            return INTENSITY_SCALE.get().floatValue();
        } catch (RuntimeException earlyStartup) {
            return INTENSITY_SCALE.getDefault().floatValue();
        }
    }

    /** Client tick publishes its accessibility option without making this class client-only. */
    public static void setAccessibilityScale(float scale) {
        accessibilityScale = Float.isFinite(scale) ? Math.max(0.0F, Math.min(1.0F, scale)) : 0.0F;
    }

    private static boolean value(ModConfigSpec.BooleanValue option, boolean fallback) {
        if (option == null) {
            return fallback;
        }
        try {
            return option.get();
        } catch (RuntimeException earlyStartup) {
            return option.getDefault();
        }
    }
}
