package com.thunder.wildernessodysseyapi.debugoverlay.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only compatibility and presentation settings for the paged F3 HUD. */
public final class DebugOverlayConfig {
    public static final ModConfigSpec CONFIG_SPEC;
    public static final ModConfigSpec.BooleanValue ENABLE_CUSTOM_DEBUG_HUD;
    public static final ModConfigSpec.BooleanValue REMEMBER_LAST_DEBUG_PAGE;
    public static final ModConfigSpec.BooleanValue SHOW_PAGE_HINTS;
    public static final ModConfigSpec.BooleanValue DEBUG_HUD_BACKGROUND;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Paged Wilderness replacement for the vanilla F3 text presentation.")
                .push("debug_hud");

        ENABLE_CUSTOM_DEBUG_HUD = builder
                .comment("Replace vanilla F3 text with the categorized Wilderness debug HUD. Disable for compatibility testing.")
                .define("enableCustomDebugHud", true);
        REMEMBER_LAST_DEBUG_PAGE = builder
                .comment("Keep the selected page when F3 is closed and reopened during this client session.")
                .define("rememberLastDebugPage", true);
        SHOW_PAGE_HINTS = builder
                .comment("Show the previous/next page controls in the footer.")
                .define("showPageHints", true);
        DEBUG_HUD_BACKGROUND = builder
                .comment("Draw the subtle translucent panel behind debug text.")
                .define("debugHudBackground", true);

        builder.pop();
        CONFIG_SPEC = builder.build();
    }

    private DebugOverlayConfig() {
    }
}
