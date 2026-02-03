package com.thunder.wildernessodysseyapi.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CurioRenderConfig {
    public static final ModConfigSpec CONFIG_SPEC;
    public static final ModConfigSpec.BooleanValue RENDER_NEURAL_FRAME;
    public static final ModConfigSpec.BooleanValue RENDER_CHIP_SET;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("curio_rendering");
        RENDER_NEURAL_FRAME = builder.comment("Render the neural frame on the player.")
                .define("renderNeuralFrame", true);
        RENDER_CHIP_SET = builder.comment("Render chip set curios on the player.")
                .define("renderChipSet", true);
        builder.pop();
        CONFIG_SPEC = builder.build();
    }

    private CurioRenderConfig() {
    }
}
