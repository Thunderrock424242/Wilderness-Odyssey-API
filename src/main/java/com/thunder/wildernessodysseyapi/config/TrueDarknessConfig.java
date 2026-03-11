package com.thunder.wildernessodysseyapi.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TrueDarknessConfig {
    public static final ModConfigSpec CONFIG_SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue DARKNESS_STRENGTH;
    public static final ModConfigSpec.DoubleValue MOONLIGHT_INFLUENCE;
    public static final ModConfigSpec.DoubleValue MIN_NIGHT_BRIGHTNESS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("true_darkness");

        ENABLED = builder
                .comment("Enable darker nights similar to True Darkness.")
                .define("enabled", true);

        DARKNESS_STRENGTH = builder
                .comment("How much darker night sky darkening becomes. 0.0 = vanilla, 1.0 = very dark.")
                .defineInRange("darknessStrength", 0.8D, 0.0D, 1.0D);

        MOONLIGHT_INFLUENCE = builder
                .comment("How much moon phase reduces darkness. 0.0 = ignore moon, 1.0 = strongest moonlight impact.")
                .defineInRange("moonlightInfluence", 0.55D, 0.0D, 1.0D);

        MIN_NIGHT_BRIGHTNESS = builder
                .comment("Minimum brightness multiplier at midnight. Lower values produce truer black nights.")
                .defineInRange("minNightBrightness", 0.02D, 0.0D, 1.0D);

        builder.pop();
        CONFIG_SPEC = builder.build();
    }

    private TrueDarknessConfig() {
    }
}
