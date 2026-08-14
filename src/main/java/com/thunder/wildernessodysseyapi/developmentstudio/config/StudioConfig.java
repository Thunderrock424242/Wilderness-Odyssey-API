package com.thunder.wildernessodysseyapi.developmentstudio.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned safety and capacity settings for Development Studio access. */
public final class StudioConfig {
    public static final ModConfigSpec CONFIG_SPEC;
    public static final ModConfigSpec.BooleanValue ALLOW_IN_NORMAL_WORLDS;
    public static final ModConfigSpec.IntValue MAX_BOOKMARKS;
    public static final ModConfigSpec.IntValue MAX_ENTITY_LAB_ENTITIES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("access");
        ALLOW_IN_NORMAL_WORLDS = builder.comment(
                        "Allows authorized operators to use Studio tools in a non-Studio test world.",
                        "Keep false on production servers. Permission level 2 is still required."
                )
                .define("allowInNormalWorlds", false);
        builder.pop();

        builder.push("bookmarks");
        MAX_BOOKMARKS = builder.comment(
                        "Maximum number of persistent Studio bookmarks stored in one world."
                )
                .defineInRange("maximumPerWorld", 128, 1, 512);
        builder.pop();

        builder.push("labs");
        MAX_ENTITY_LAB_ENTITIES = builder.comment(
                        "Maximum number of Studio-tagged entities allowed inside the Entity Lab."
                )
                .defineInRange("maximumEntityLabEntities", 48, 1, 128);
        builder.pop();
        CONFIG_SPEC = builder.build();
    }

    private StudioConfig() {
    }
}
