package com.thunder.wildernessodysseyapi.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class DebugOverlayConfig {
    public static final ModConfigSpec CONFIG_SPEC;
    public static final ModConfigSpec.BooleanValue ENABLE_ANIMATION;
    public static final ModConfigSpec.IntValue ANIMATION_TICKS;
    public static final ModConfigSpec.BooleanValue SHOW_HELP_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_VERSION_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_FPS_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_ENTITY_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_XYZ_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_BLOCK_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_CHUNK_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_FACING_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_LIGHT_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_BIOME_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_DIFFICULTY_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_LOCAL_DIFFICULTY_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_DAYTIME_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_TARGETED_BLOCK;
    public static final ModConfigSpec.BooleanValue SHOW_TARGETED_FLUID;
    public static final ModConfigSpec.BooleanValue SHOW_TARGETED_ENTITY;

    public static final ModConfigSpec.BooleanValue SHOW_JAVA_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_MEMORY_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_ALLOCATION_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_CPU_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_DISPLAY_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_GPU_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_OPENGL_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_RENDERER_LINE;
    public static final ModConfigSpec.BooleanValue SHOW_SERVER_LINE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("F3 debug overlay settings.").push("debug_overlay");

        builder.comment("Overlay animation options.")
                .push("animation");
        ENABLE_ANIMATION = builder
                .comment("Enable the opening/closing animation for the debug overlay.")
                .define("enableAnimation", true);
        ANIMATION_TICKS = builder
                .comment("Number of client ticks for the open/close animation.")
                .defineInRange("animationTicks", 6, 1, 40);
        builder.pop();

        builder.comment("Left column (game information).")
                .push("game_info");
        SHOW_HELP_LINE = builder
                .comment("Show the help hint line (F3 + Q).")
                .define("showHelpLine", true);
        SHOW_VERSION_LINE = builder
                .comment("Show the Minecraft version and modded status line.")
                .define("showVersionLine", true);
        SHOW_FPS_LINE = builder
                .comment("Show the FPS line.")
                .define("showFpsLine", true);
        SHOW_ENTITY_LINE = builder
                .comment("Show the entity statistics line (E:...).")
                .define("showEntityLine", true);
        SHOW_XYZ_LINE = builder
                .comment("Show the player XYZ line.")
                .define("showXyzLine", true);
        SHOW_BLOCK_LINE = builder
                .comment("Show the block position line.")
                .define("showBlockLine", true);
        SHOW_CHUNK_LINE = builder
                .comment("Show the chunk information lines.")
                .define("showChunkLine", true);
        SHOW_FACING_LINE = builder
                .comment("Show the facing direction line.")
                .define("showFacingLine", true);
        SHOW_LIGHT_LINE = builder
                .comment("Show the light level lines.")
                .define("showLightLine", true);
        SHOW_BIOME_LINE = builder
                .comment("Show the biome line.")
                .define("showBiomeLine", true);
        SHOW_DIFFICULTY_LINE = builder
                .comment("Show the difficulty line.")
                .define("showDifficultyLine", true);
        SHOW_LOCAL_DIFFICULTY_LINE = builder
                .comment("Show the local difficulty line.")
                .define("showLocalDifficultyLine", true);
        SHOW_DAYTIME_LINE = builder
                .comment("Show the day/time line.")
                .define("showDaytimeLine", true);
        SHOW_TARGETED_BLOCK = builder
                .comment("Show the targeted block section.")
                .define("showTargetedBlock", true);
        SHOW_TARGETED_FLUID = builder
                .comment("Show the targeted fluid section.")
                .define("showTargetedFluid", true);
        SHOW_TARGETED_ENTITY = builder
                .comment("Show the targeted entity section.")
                .define("showTargetedEntity", true);
        builder.pop();

        builder.comment("Right column (system information).")
                .push("system_info");
        SHOW_JAVA_LINE = builder
                .comment("Show the Java runtime line.")
                .define("showJavaLine", true);
        SHOW_MEMORY_LINE = builder
                .comment("Show the memory usage line.")
                .define("showMemoryLine", true);
        SHOW_ALLOCATION_LINE = builder
                .comment("Show the allocation rate line.")
                .define("showAllocationLine", true);
        SHOW_CPU_LINE = builder
                .comment("Show the CPU line.")
                .define("showCpuLine", true);
        SHOW_DISPLAY_LINE = builder
                .comment("Show the display line.")
                .define("showDisplayLine", true);
        SHOW_GPU_LINE = builder
                .comment("Show the GPU line.")
                .define("showGpuLine", true);
        SHOW_OPENGL_LINE = builder
                .comment("Show the OpenGL/renderer line.")
                .define("showOpenGlLine", true);
        SHOW_RENDERER_LINE = builder
                .comment("Show the graphics renderer line.")
                .define("showRendererLine", true);
        SHOW_SERVER_LINE = builder
                .comment("Show the server brand line.")
                .define("showServerLine", true);
        builder.pop();

        builder.pop();
        CONFIG_SPEC = builder.build();
    }

    private DebugOverlayConfig() {
    }
}
