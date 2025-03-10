package com.thunder.wildernessodysseyapi.NovaAPI.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class NovaAPIConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec CONFIG;

    // 🔹 General Settings
    public static final ModConfigSpec.BooleanValue ENABLE_NOVA_API;

    // 🔹 Dedicated Server Settings
    public static final ModConfigSpec.BooleanValue ENABLE_DEDICATED_SERVER;
    public static final ModConfigSpec.ConfigValue<String> DEDICATED_SERVER_IP;

    // 🔹 Chunk Optimization Settings
    public static final ModConfigSpec.BooleanValue ENABLE_CHUNK_OPTIMIZATIONS;
    public static final ModConfigSpec.BooleanValue ASYNC_CHUNK_LOADING;
    public static final ModConfigSpec.BooleanValue SMART_CHUNK_RETENTION;

    // 🔹 AI Pathfinding Optimization
    public static final ModConfigSpec.BooleanValue ENABLE_AI_OPTIMIZATIONS;
    public static final ModConfigSpec.IntValue PATHFINDING_THREAD_COUNT;

    static {
        BUILDER.push("General Settings");
        ENABLE_NOVA_API = BUILDER
                .comment("Enable Nova API. If false, all features are disabled.")
                .define("enableNovaAPI", true);
        BUILDER.pop();

        BUILDER.push("Dedicated Server Settings");
        ENABLE_DEDICATED_SERVER = BUILDER
                .comment("Enable Dedicated Mode (Connect to Nova API Server).")
                .define("enableDedicatedServer", false);
        DEDICATED_SERVER_IP = BUILDER
                .comment("Dedicated Nova API Server Address (if Dedicated Mode is enabled).")
                .define("dedicatedServerIP", "127.0.0.1");
        BUILDER.pop();

        BUILDER.push("Chunk Optimization Settings");
        ENABLE_CHUNK_OPTIMIZATIONS = BUILDER
                .comment("Enable optimized chunk loading and retention.")
                .define("enableChunkOptimizations", true);
        ASYNC_CHUNK_LOADING = BUILDER
                .comment("Use multi-threaded chunk loading to reduce lag.")
                .define("asyncChunkLoading", true);
        SMART_CHUNK_RETENTION = BUILDER
                .comment("Keep frequently accessed chunks loaded for longer to prevent constant reloads.")
                .define("smartChunkRetention", true);
        BUILDER.pop();

        BUILDER.push("AI Pathfinding Optimization");
        ENABLE_AI_OPTIMIZATIONS = BUILDER
                .comment("Enable multi-threaded AI pathfinding for better performance.")
                .define("enableAIOptimizations", true);
        PATHFINDING_THREAD_COUNT = BUILDER
                .comment("Number of threads for AI pathfinding (Higher = better performance but more CPU usage).")
                .defineInRange("pathfindingThreadCount", 4, 1, 16);
        BUILDER.pop();

        CONFIG = BUILDER.build();
    }
}