package com.thunder.wildernessodysseyapi.meteor.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class MeteorConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // Event spawning
    public static final ModConfigSpec.IntValue EVENT_CHECK_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue EVENT_CHANCE_PER_CHECK;   // 1-in-N chance
    public static final ModConfigSpec.IntValue MIN_METEORS;
    public static final ModConfigSpec.IntValue MAX_METEORS;
    public static final ModConfigSpec.IntValue SPAWN_RADIUS;             // blocks around a player to aim meteors

    // Impact destruction
    public static final ModConfigSpec.EnumValue<DestructionLevel> DESTRUCTION_LEVEL;
    public static final ModConfigSpec.IntValue CRATER_RADIUS_MIN;
    public static final ModConfigSpec.IntValue CRATER_RADIUS_MAX;
    public static final ModConfigSpec.IntValue GOUGE_LENGTH_MULTIPLIER;  // multiplied by horizontal speed

    // Player safety
    public static final ModConfigSpec.IntValue PLAYER_AVOID_RADIUS;      // meteors won't land within this many blocks of a player
    public static final ModConfigSpec.IntValue CRYING_OBSIDIAN_SEARCH_RADIUS; // radius to look for crying obsidian bias

    // Visual / sound
    public static final ModConfigSpec.BooleanValue SHOW_WARNING_MESSAGE;
    public static final ModConfigSpec.IntValue WARNING_TICKS_BEFORE_IMPACT; // ticks before impact to send warning

    static {
        BUILDER.comment("Meteor Impact Weather Event Configuration").push("meteor_event");

        EVENT_CHECK_INTERVAL_TICKS = BUILDER
                .comment("How often (in ticks) the game checks whether to start a meteor event. 20 ticks = 1 second.")
                .defineInRange("eventCheckIntervalTicks", 72000, 200, 720000); // default: every hour

        EVENT_CHANCE_PER_CHECK = BUILDER
                .comment("1-in-N chance of a meteor event occurring each check interval.")
                .defineInRange("eventChancePerCheck", 3, 1, 1000);

        MIN_METEORS = BUILDER
                .comment("Minimum number of meteors per event.")
                .defineInRange("minMeteors", 2, 1, 20);

        MAX_METEORS = BUILDER
                .comment("Maximum number of meteors per event.")
                .defineInRange("maxMeteors", 5, 1, 20);

        SPAWN_RADIUS = BUILDER
                .comment("Radius around each player to spawn meteors (blocks).")
                .defineInRange("spawnRadius", 150, 50, 500);

        BUILDER.comment("Controls how much terrain the impact destroys.").push("destruction");

        DESTRUCTION_LEVEL = BUILDER
                .comment("Destruction preset. LIGHT = small craters, MEDIUM = moderate, HEAVY = large. " +
                        "CUSTOM uses the crater radius values below.")
                .defineEnum("destructionLevel", DestructionLevel.MEDIUM);

        CRATER_RADIUS_MIN = BUILDER
                .comment("(CUSTOM only) Minimum crater radius in blocks.")
                .defineInRange("craterRadiusMin", 4, 1, 30);

        CRATER_RADIUS_MAX = BUILDER
                .comment("(CUSTOM only) Maximum crater radius in blocks.")
                .defineInRange("craterRadiusMax", 8, 1, 30);

        GOUGE_LENGTH_MULTIPLIER = BUILDER
                .comment("How long the ground gouge trail is relative to horizontal entry speed. Higher = longer trench.")
                .defineInRange("gougeLengthMultiplier", 3, 1, 10);

        BUILDER.pop();

        BUILDER.comment("Player safety settings.").push("safety");

        PLAYER_AVOID_RADIUS = BUILDER
                .comment("Meteors will not land within this many blocks of any player. Set to 0 to disable.")
                .defineInRange("playerAvoidRadius", 8, 0, 64);

        CRYING_OBSIDIAN_SEARCH_RADIUS = BUILDER
                .comment("Radius to scan for crying obsidian when biasing landing spots.")
                .defineInRange("cryingObsidianSearchRadius", 64, 16, 256);

        BUILDER.pop();

        BUILDER.comment("Visual and notification settings.").push("visual");

        SHOW_WARNING_MESSAGE = BUILDER
                .comment("Show a chat/title warning to players when a meteor event starts.")
                .define("showWarningMessage", true);

        WARNING_TICKS_BEFORE_IMPACT = BUILDER
                .comment("How many ticks before impact to show the warning. Set to 0 to warn at spawn.")
                .defineInRange("warningTicksBeforeImpact", 60, 0, 400);

        BUILDER.pop();
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public enum DestructionLevel {
        LIGHT(3, 5),
        MEDIUM(5, 9),
        HEAVY(9, 16),
        CUSTOM(-1, -1); // uses crater radius values from config

        public final int minRadius;
        public final int maxRadius;

        DestructionLevel(int min, int max) {
            this.minRadius = min;
            this.maxRadius = max;
        }
    }
}