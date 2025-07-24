package com.thunder.wildernessodysseyapi.intro.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class PlayOnJoinConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.ConfigValue<String> VIDEO_PATH;
    public static final ModConfigSpec.BooleanValue PLAY_ONLY_ONCE;
    public static final ModConfigSpec.BooleanValue SKIPPABLE;
    public static final ModConfigSpec.IntValue VOLUME;
    public static final ModConfigSpec SPEC;

    static {
        VIDEO_PATH = BUILDER.comment("Path to the video file relative to the config folder (e.g., 'playonjoin/intro.mp4')").define("videoPath", "playonjoin/intro.mp4");
        PLAY_ONLY_ONCE = BUILDER.comment("If true, the video will only play once per player").define("playOnlyOnce", true);
        SKIPPABLE = BUILDER.comment("If true, players can skip the video by pressing ESC").define("skippable", true);
        VOLUME = BUILDER.comment("Video volume (0-100)").defineInRange("volume", 100, 0, 100);
        SPEC = BUILDER.build();
    }
}
