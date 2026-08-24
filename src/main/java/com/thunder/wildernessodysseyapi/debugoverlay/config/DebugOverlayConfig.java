package com.thunder.wildernessodysseyapi.debugoverlay.config;

import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only compatibility and presentation settings for the paged F3 HUD. */
public final class DebugOverlayConfig {
    public static ModConfigSpec CONFIG_SPEC;
    public static ModConfigSpec.BooleanValue ENABLE_CUSTOM_DEBUG_HUD;
    public static ModConfigSpec.BooleanValue REMEMBER_LAST_DEBUG_PAGE;
    public static ModConfigSpec.BooleanValue SHOW_PAGE_HINTS;
    public static ModConfigSpec.BooleanValue DEBUG_HUD_BACKGROUND;

    static {
        WildernessConfigSpecs.initialize();
    }

    /** Defines the debug-overlay category in the unified client config. */
    public static void define(ModConfigSpec.Builder builder) {
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
    }

    private DebugOverlayConfig() {
    }
}
