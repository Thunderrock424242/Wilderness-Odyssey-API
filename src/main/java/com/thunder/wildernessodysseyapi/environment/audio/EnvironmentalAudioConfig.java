package com.thunder.wildernessodysseyapi.environment.audio;

import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Local sound presentation options composed into the existing client config. */
public final class EnvironmentalAudioConfig {

    public static ModConfigSpec.BooleanValue ENABLE_OCCLUSION;
    public static ModConfigSpec.DoubleValue OCCLUSION_STRENGTH;

    static {
        WildernessConfigSpecs.initialize();
    }

    private EnvironmentalAudioConfig() {
    }

    /** Defines options for the shared outdoor-sound obstruction check. */
    public static void define(ModConfigSpec.Builder builder) {
        ENABLE_OCCLUSION = builder
                .comment("Trace through nearby loaded blocks to damp wave, river, rain and distant-thunder sounds behind barriers.",
                        "Automatically yields to Sound Physics Remastered when that mod is installed.")
                .define("enable_occlusion", true);
        OCCLUSION_STRENGTH = builder
                .comment("Obstruction strength: 0 preserves the original mix; 1 applies full wall-thickness attenuation.",
                        "This changes volume only, without low-pass filtering or reflected echoes.")
                .defineInRange("occlusion_strength", 1.0, 0.0, 1.0);
    }
}
