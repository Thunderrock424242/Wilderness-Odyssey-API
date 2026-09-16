package com.thunder.wildernessodysseyapi.temporalrift.config;

import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class TemporalRiftConfig {
    private static ModConfigSpec.Builder BUILDER;
    public static ModConfigSpec CONFIG_SPEC;

    public static ModConfigSpec.BooleanValue ENABLE_RIFT_SYSTEM;
    public static ModConfigSpec.IntValue RIFT_INTERVAL_DAYS;
    public static ModConfigSpec.IntValue RIFT_OPEN_DURATION_TICKS;
    public static ModConfigSpec.IntValue RIFT_SPAWN_RADIUS;
    public static ModConfigSpec.BooleanValue ENABLE_RIFT_SINKHOLE;
    public static ModConfigSpec.IntValue RIFT_SINKHOLE_RADIUS;
    public static ModConfigSpec.IntValue RIFT_SINKHOLE_DEPTH;
    public static ModConfigSpec.BooleanValue ENABLE_RIFT_OPENING_DISASTER;
    public static ModConfigSpec.BooleanValue ENABLE_RIFT_PULL_EFFECT;
    public static ModConfigSpec.BooleanValue ENABLE_RIFT_TERRAIN_AGING;
    public static ModConfigSpec.IntValue TIME_CAPSULE_DELAY_DAYS;
    public static ModConfigSpec.IntValue TIME_CAPSULE_BURIAL_DEPTH;
    public static ModConfigSpec.BooleanValue ENABLE_TEMPORAL_ECHOES;
    public static ModConfigSpec.IntValue TEMPORAL_ECHO_DELAY_DAYS;
    public static ModConfigSpec.IntValue TEMPORAL_ECHO_BURIAL_DEPTH;
    public static ModConfigSpec.ConfigValue<List<? extends String>> BEFORE_ALLOWED_MOBS;
    public static ModConfigSpec.ConfigValue<List<? extends String>> BEFORE_ALLOWED_STRUCTURES;
    public static ModConfigSpec.ConfigValue<List<? extends String>> ECHO_ALLOWED_MOBS;
    public static ModConfigSpec.BooleanValue ENABLE_ECHO_CHUNK_DISTORTION;
    public static ModConfigSpec.BooleanValue ENABLE_ECHO_BUILD_ECHOES;
    public static ModConfigSpec.BooleanValue ENABLE_ECHO_STABILITY_SYSTEM;
    public static ModConfigSpec.BooleanValue ENABLE_ECHO_REALITY_ECHOES;
    public static ModConfigSpec.BooleanValue ENABLE_ECHO_TIME_ANOMALIES;
    public static ModConfigSpec.BooleanValue ENABLE_ECHO_ATMOSPHERIC_DISTORTION;
    public static ModConfigSpec.BooleanValue ENABLE_ECHO_FRACTURE_INFLUENCE;
    public static ModConfigSpec.IntValue ECHO_STABLE_REGION_WEIGHT;
    public static ModConfigSpec.IntValue ECHO_DESYNCED_REGION_WEIGHT;
    public static ModConfigSpec.IntValue ECHO_FRACTURED_REGION_WEIGHT;
    public static ModConfigSpec.IntValue ECHO_FRACTURE_INFLUENCE_RADIUS;
    public static ModConfigSpec.DoubleValue ECHO_REALITY_ECHO_CHANCE;
    public static ModConfigSpec.IntValue ECHO_MAX_PENDING_REALITY_ECHOES;
    public static ModConfigSpec.IntValue ECHO_REALITY_ECHO_RETENTION_DAYS;
    public static ModConfigSpec.BooleanValue RETURN_ONLY_ACTIVE_RIFT;
    public static ModConfigSpec.BooleanValue CHAT_BROADCASTS;
    public static ModConfigSpec.BooleanValue DEBUG_LOGGING;

    static {
        WildernessConfigSpecs.initialize();
    }

    /** Defines temporal-rift settings in the unified server config. */
    public static void define(ModConfigSpec.Builder builder) {
        BUILDER = builder;
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
                .comment("If true, opening a rift tears open a major sinkhole and places the rift in its basin.")
                .define("enableRiftSinkhole", true);

        RIFT_SINKHOLE_RADIUS = BUILDER
                .comment("Radius, in blocks, of the terrain collapse around a newly opened rift.")
                .defineInRange("riftSinkholeRadius", 42, 6, 96);

        RIFT_SINKHOLE_DEPTH = BUILDER
                .comment("Maximum depth, in blocks, of the rift sinkhole.")
                .defineInRange("riftSinkholeDepth", 30, 6, 64);

        ENABLE_RIFT_OPENING_DISASTER = BUILDER
                .comment("If true, a new Overworld rift triggers a brief large-scale disaster pulse: shockwave, dust, startled wildlife, and light rim damage.")
                .define("enableRiftOpeningDisaster", true);

        ENABLE_RIFT_PULL_EFFECT = BUILDER
                .comment("If true, an open rift gently pulls nearby players toward the tear.")
                .define("enableRiftPullEffect", true);

        ENABLE_RIFT_TERRAIN_AGING = BUILDER
                .comment("If true, an open rift slowly ages nearby stone and soil into darker materials.")
                .define("enableRiftTerrainAging", true);

        TIME_CAPSULE_DELAY_DAYS = BUILDER
                .comment("Minecraft days before a sealed time capsule transfers to the Overworld.")
                .defineInRange("timeCapsuleDelayDays", 3, 1, 1000);

        TIME_CAPSULE_BURIAL_DEPTH = BUILDER
                .comment("How many blocks below the Overworld surface delivered ancient time capsules are buried.")
                .defineInRange("timeCapsuleBurialDepth", 3, 1, 16);

        ENABLE_TEMPORAL_ECHOES = BUILDER
                .comment("If true, player-placed blocks in The Before later appear in the Overworld as buried ruined echoes.")
                .define("enableTemporalEchoes", true);

        TEMPORAL_ECHO_DELAY_DAYS = BUILDER
                .comment("Minecraft days before player-built changes in The Before echo into the Overworld.")
                .defineInRange("temporalEchoDelayDays", 5, 1, 1000);

        TEMPORAL_ECHO_BURIAL_DEPTH = BUILDER
                .comment("How far below the original Y level temporal echoes are placed in the Overworld.")
                .defineInRange("temporalEchoBurialDepth", 2, 0, 32);

        BEFORE_ALLOWED_MOBS = BUILDER
                .comment("Entity ids allowed to spawn naturally in The Before. Empty by default, so mobs are disabled until explicitly listed.")
                .defineListAllowEmpty("beforeAllowedMobs", List.of(), value -> value instanceof String string && string.contains(":"));

        BEFORE_ALLOWED_STRUCTURES = BUILDER
                .comment("Structure ids intended to be allowed in The Before. Empty by default. Runtime structure filtering requires a dedicated biome-source/structure layer; this list is stored now for that bridge.")
                .defineListAllowEmpty("beforeAllowedStructures", List.of(), value -> value instanceof String string && string.contains(":"));

        ECHO_ALLOWED_MOBS = BUILDER
                .comment("Entity ids allowed by natural spawn checks in Echo Earth. Existing village inhabitants are removed; wildlife and iron golems are allowed by default.")
                .defineListAllowEmpty("echoAllowedMobs", List.of(
                        "minecraft:iron_golem",
                        "minecraft:allay",
                        "minecraft:armadillo",
                        "minecraft:axolotl",
                        "minecraft:bat",
                        "minecraft:bee",
                        "minecraft:camel",
                        "minecraft:cat",
                        "minecraft:chicken",
                        "minecraft:cod",
                        "minecraft:cow",
                        "minecraft:donkey",
                        "minecraft:fox",
                        "minecraft:frog",
                        "minecraft:glow_squid",
                        "minecraft:goat",
                        "minecraft:horse",
                        "minecraft:llama",
                        "minecraft:mooshroom",
                        "minecraft:mule",
                        "minecraft:ocelot",
                        "minecraft:panda",
                        "minecraft:parrot",
                        "minecraft:pig",
                        "minecraft:polar_bear",
                        "minecraft:pufferfish",
                        "minecraft:rabbit",
                        "minecraft:salmon",
                        "minecraft:sheep",
                        "minecraft:skeleton_horse",
                        "minecraft:sniffer",
                        "minecraft:squid",
                        "minecraft:strider",
                        "minecraft:tadpole",
                        "minecraft:tropical_fish",
                        "minecraft:turtle",
                        "minecraft:wolf"
                ), value -> value instanceof String string && string.contains(":"));

        ENABLE_ECHO_CHUNK_DISTORTION = BUILDER
                .comment("Decorate new desynced/fractured Echo Earth chunks once with sampled open doors and dead leaf patches. Existing chunks and stable regions are preserved.")
                .define("enableEchoChunkDistortion", true);

        ENABLE_ECHO_BUILD_ECHOES = BUILDER
                .comment("Legacy master switch for sampled Overworld construction appearing as damaged material synchronization in Echo Earth.")
                .define("enableEchoBuildEchoes", true);

        ENABLE_ECHO_STABILITY_SYSTEM = BUILDER.comment("Enable deterministic regional instability in Echo Earth.")
                .define("enableEchoStabilitySystem", true);
        ENABLE_ECHO_REALITY_ECHOES = BUILDER.comment("Enable delayed reality echoes; enableEchoBuildEchoes must also be true.")
                .define("enableEchoRealityEchoes", true);
        ENABLE_ECHO_TIME_ANOMALIES = BUILDER.comment("Enable rare brief celestial pauses in fractured regions. Cosmetic only; server time and scheduled ticks remain unchanged.")
                .define("enableEchoTimeAnomalies", false);
        ENABLE_ECHO_ATMOSPHERIC_DISTORTION = BUILDER.comment("Scale Echo fog, sky, sound attenuation and anomaly particles by server-reported stability.")
                .define("enableEchoAtmosphericDistortion", true);
        ENABLE_ECHO_FRACTURE_INFLUENCE = BUILDER.comment("Increase Echo instability near active Temporal Rifts and remembered major fracture sites at corresponding Earth coordinates.")
                .define("enableEchoFractureInfluence", true);
        ECHO_STABLE_REGION_WEIGHT = BUILDER.comment("Relative stable-region weight. All weights zero means stable everywhere.")
                .defineInRange("echoStableRegionWeight", 75, 0, 1000);
        ECHO_DESYNCED_REGION_WEIGHT = BUILDER.defineInRange("echoDesyncedRegionWeight", 22, 0, 1000);
        ECHO_FRACTURED_REGION_WEIGHT = BUILDER.defineInRange("echoFracturedRegionWeight", 3, 0, 1000);
        ECHO_FRACTURE_INFLUENCE_RADIUS = BUILDER.comment("Horizontal influence radius in blocks; sampled at chunk centers.")
                .defineInRange("echoFractureInfluenceRadius", 384, 16, 4096);
        ECHO_REALITY_ECHO_CHANCE = BUILDER.comment("Probability from 0 to 1 of sampling a placed solid block; breaks use half this probability.")
                .defineInRange("echoRealityEchoChance", 0.35, 0.0, 1.0);
        ECHO_MAX_PENDING_REALITY_ECHOES = BUILDER.comment("Maximum pending sampled edits. Overflow declines new samples without evicting accepted work; a fixed 65536-record load safety cap applies to legacy data.")
                .defineInRange("echoMaxPendingRealityEchoes", 8192, 128, 65536);
        ECHO_REALITY_ECHO_RETENTION_DAYS = BUILDER.comment("Minecraft game-time days to retain due echoes while their destination is unloaded. Does not delete placed blocks.")
                .defineInRange("echoRealityEchoRetentionDays", 30, 1, 365);

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
        BUILDER = null;
    }

    private TemporalRiftConfig() {
    }
}
