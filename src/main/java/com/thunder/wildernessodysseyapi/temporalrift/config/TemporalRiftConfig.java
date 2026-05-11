package com.thunder.wildernessodysseyapi.temporalrift.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TemporalRiftConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec CONFIG_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_RIFT_SYSTEM;
    public static final ModConfigSpec.IntValue RIFT_INTERVAL_DAYS;
    public static final ModConfigSpec.IntValue RIFT_OPEN_DURATION_TICKS;
    public static final ModConfigSpec.IntValue RIFT_SPAWN_RADIUS;
    public static final ModConfigSpec.BooleanValue ENABLE_RIFT_SINKHOLE;
    public static final ModConfigSpec.IntValue RIFT_SINKHOLE_RADIUS;
    public static final ModConfigSpec.IntValue RIFT_SINKHOLE_DEPTH;
    public static final ModConfigSpec.BooleanValue ENABLE_RIFT_PULL_EFFECT;
    public static final ModConfigSpec.BooleanValue ENABLE_RIFT_TERRAIN_AGING;
    public static final ModConfigSpec.IntValue TIME_CAPSULE_DELAY_DAYS;
    public static final ModConfigSpec.BooleanValue RETURN_ONLY_ACTIVE_RIFT;
    public static final ModConfigSpec.BooleanValue CHAT_BROADCASTS;
    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING;

    static {
        BUILDER.comment("Wilderness Odyssey - Temporal Rift System").push("temporal_rift");

        ENABLE_RIFT_SYSTEM = BUILDER
                .comment("Enable or disable the entire Temporal Rift system.")
                .define("enableRiftSystem", true);

        RIFT_INTERVAL_DAYS = BUILDER
                .comment("Minecraft days between each rift opening.")
                .defineInRange("riftIntervalDays", 10, 1, 1000);

        RIFT_OPEN_DURATION_TICKS = BUILDER
                .comment("How long the rift stays open in ticks. 24000 = 1 Minecraft day.")
                .defineInRange("riftOpenDurationTicks", 24000, 100, 2_400_000);

        RIFT_SPAWN_RADIUS = BUILDER
                .comment("Max block radius from world spawn where the rift can appear.")
                .defineInRange("riftSpawnRadius", 64, 0, 10_000);

        ENABLE_RIFT_SINKHOLE = BUILDER
                .comment("If true, opening a rift carves a small sinkhole and places the rift in its center.")
                .define("enableRiftSinkhole", true);

        RIFT_SINKHOLE_RADIUS = BUILDER
                .comment("Radius, in blocks, of the terrain collapse around a newly opened rift.")
                .defineInRange("riftSinkholeRadius", 7, 3, 24);

        RIFT_SINKHOLE_DEPTH = BUILDER
                .comment("Maximum depth, in blocks, of the rift sinkhole.")
                .defineInRange("riftSinkholeDepth", 6, 3, 32);

        ENABLE_RIFT_PULL_EFFECT = BUILDER
                .comment("If true, an open rift gently pulls nearby players toward the tear.")
                .define("enableRiftPullEffect", true);

        ENABLE_RIFT_TERRAIN_AGING = BUILDER
                .comment("If true, an open rift slowly ages nearby stone and soil into darker materials.")
                .define("enableRiftTerrainAging", true);

        TIME_CAPSULE_DELAY_DAYS = BUILDER
                .comment("Minecraft days before a sealed time capsule transfers to the Overworld.")
                .defineInRange("timeCapsuleDelayDays", 3, 1, 1000);

        RETURN_ONLY_ACTIVE_RIFT = BUILDER
                .comment("If true, players can only return from the past dimension during an active rift.")
                .define("returnOnlyDuringActiveRift", true);

        CHAT_BROADCASTS = BUILDER
                .comment("Broadcast rift open/close messages to all online players.")
                .define("enableChatBroadcasts", true);

        DEBUG_LOGGING = BUILDER
                .comment("Enable verbose debug logging for development.")
                .define("debugLogging", false);

        BUILDER.pop();
        CONFIG_SPEC = BUILDER.build();
    }

    private TemporalRiftConfig() {
    }
}
