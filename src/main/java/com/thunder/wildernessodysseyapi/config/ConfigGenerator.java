package com.thunder.wildernessodysseyapi.config;

import com.thunder.wildernessodysseyapi.network.WildernessOdysseyApiModVariables;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * The Config Generator class.
 */
public class ConfigGenerator {

    public static final ModConfigSpec COMMON_CONFIG;
    public static final AntiCheatConfig CONFIG;
    public static final boolean AGREE_TO_TERMS = false;
    public static final boolean GLOBAL_LOGGING_ENABLED = true;

    static {
        // Create a builder and configure the config class
        Pair<AntiCheatConfig, ModConfigSpec> configPair = new ModConfigSpec.Builder().configure(AntiCheatConfig::new);
        CONFIG = configPair.getLeft();
        COMMON_CONFIG = configPair.getRight();
    }

    /**
     * Register the configuration.
     *
     * @param container the mod container
     */
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, COMMON_CONFIG, "anti-cheat.toml");
    }

    /**
     * Inner class for anti-cheat config values.
     */
    public static class AntiCheatConfig {

        // Define config values
        public final ModConfigSpec.BooleanValue agreeToTerms;
        public final ModConfigSpec.BooleanValue antiCheatEnabled;
        public final ModConfigSpec.BooleanValue globalLoggingEnabled;

        /**
         * Constructor for AntiCheatConfig.
         *
         * @param builder the ModConfigSpec builder
         */
        public AntiCheatConfig(ModConfigSpec.Builder builder) {
            builder.comment("Wilderness Odyssey API Anti-Cheat Configuration").push("anti_cheat");

            // Agreement to Terms and Privacy Policy
            agreeToTerms = builder
                    .comment("You must agree to the terms and privacy policy outlined in the README.md file to use this mod.",
                            "Set this to true to confirm your agreement. The server will not start unless this is set to true.")
                    .define("agreeToTerms", false);

            // Enable or disable anti-cheat features (only applicable if the server is whitelisted)
            antiCheatEnabled = builder
                    .comment("Enable or disable the anti-cheat functionality. Note: Anti-cheat will only be active if the server is on the hardcoded whitelist.")
                    .define("antiCheatEnabled", true);

            // Enable or disable global logging for player violations
            globalLoggingEnabled = builder
                    .comment("Enable or disable global logging for player violations.")
                    .define("globalLoggingEnabled", true);

            builder.pop();
        }
    }
}
