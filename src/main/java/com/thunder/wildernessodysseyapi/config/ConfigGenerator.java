package com.thunder.wildernessodysseyapi.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * The Config Generator class.
 */
public class ConfigGenerator {


    public static final ModConfigSpec COMMON_CONFIG;
    public static final AntiCheatConfig COMMON;
    public static final ModConfigSpec CLIENT_CONFIG;
    public static final ClientConfig CLIENT;
    public static final boolean AGREE_TO_TERMS = false;
    public static final boolean GLOBAL_LOGGING_ENABLED = true;

    static {
        // Build common config
        Pair<AntiCheatConfig, ModConfigSpec> commonPair = new ModConfigSpec.Builder().configure(AntiCheatConfig::new);
        COMMON = commonPair.getLeft();
        COMMON_CONFIG = commonPair.getRight();

        // Build client config
        Pair<ClientConfig, ModConfigSpec> clientPair = new ModConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT = clientPair.getLeft();
        CLIENT_CONFIG = clientPair.getRight();
    }

    /**
     * Register the configuration.
     *
     * @param container the mod container
     */
   public static void register(ModContainer container) {
        // Register common config
        container.registerConfig(ModConfig.Type.COMMON, COMMON_CONFIG, "anti-cheat.toml");

        // Register client config
        container.registerConfig(ModConfig.Type.CLIENT, CLIENT_CONFIG, "world-preset.toml");
        // Register tool damage mixin config
        container.registerConfig(ModConfig.Type.CLIENT, COMMON_CONFIG, "Tool-Damage.toml");
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
                    .comment("You must agree to the terms and privacy policy outlined in the README.md file.",
                            "Set this to true to confirm your agreement. The server will not start unless this is set to true.")
                    .define("agreeToTerms", false);

            // Enable or disable anti-cheat features
            antiCheatEnabled = builder
                    .comment("Enable or disable the anti-cheat functionality. Only active if the server is on the whitelist.")
                    .define("antiCheatEnabled", true);

            // Enable or disable global logging for player violations
            globalLoggingEnabled = builder
                    .comment("Enable or disable global logging for player violations.")
                    .define("globalLoggingEnabled", true);

            builder.pop();
        }
    }
}
