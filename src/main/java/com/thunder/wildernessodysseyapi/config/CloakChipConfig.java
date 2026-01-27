package com.thunder.wildernessodysseyapi.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CloakChipConfig {
    public static final ModConfigSpec CONFIG_SPEC;
    public static final ModConfigSpec.BooleanValue ENABLE_NAUSEA;
    public static final int NAUSEA_DURATION_TICKS = 200;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("cloak_chip");
        ENABLE_NAUSEA = builder.comment("Apply nausea when equipping or removing the cloak chip.")
                .define("enableNausea", true);
        builder.pop();
        CONFIG_SPEC = builder.build();
    }

    private CloakChipConfig() {
    }
}
