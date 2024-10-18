package net.mcreator.wildernessodysseyapi;

import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.ModContainer;

public class ConfigGenerator {
    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue AGREE_TO_TERMS;
    public static final ModConfigSpec.BooleanValue ANTI_CHEAT_ENABLED;
    public static final ModConfigSpec.BooleanValue GLOBAL_LOGGING_ENABLED;

    static {
        COMMON_BUILDER.comment("Wilderness Odyssey API Anti-Cheat Configuration").push("anti_cheat");

        // Agreement to Terms and Privacy Policy
        AGREE_TO_TERMS = COMMON_BUILDER
                .comment("You must agree to the terms and privacy policy outlined in the README.md file to use this mod.",
                        "Set this to true to confirm your agreement. The server will not start unless this is set to true.")
                .define("agreeToTerms", false);

        // Enable or disable anti-cheat features (only applicable if the server is whitelisted)
        ANTI_CHEAT_ENABLED = COMMON_BUILDER
                .comment("Enable or disable the anti-cheat functionality. Note: Anti-cheat will only be active if the server is on the hardcoded whitelist.")
                .define("antiCheatEnabled", true);

        // Enable or disable global logging for player violations
        GLOBAL_LOGGING_ENABLED = COMMON_BUILDER
                .comment("Enable or disable global logging for player violations.")
                .define("globalLoggingEnabled", true);

        COMMON_BUILDER.pop();
    }

    public static final ModConfigSpec COMMON_CONFIG = COMMON_BUILDER.build();

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, COMMON_BUILDER.build(), "anti-cheat.toml");
    }
}
