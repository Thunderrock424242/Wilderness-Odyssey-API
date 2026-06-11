package com.thunder.wildernessodysseyapi.playtest.verification;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side playtest account verification settings.
 */
public final class MinecraftVerificationConfig {
    public static final ModConfigSpec CONFIG_SPEC;

    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.ConfigValue<String> API_BASE_URL;
    private static final ModConfigSpec.IntValue REQUEST_TIMEOUT_SECONDS;
    private static final ModConfigSpec.BooleanValue REMEMBER_LINKED_ACCOUNT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Discord-to-Minecraft playtest verification settings.").push("verification");

        ENABLED = builder
                .comment("Enable the /wo link command for local playtest account verification.")
                .define("enabled", true);

        API_BASE_URL = builder
                .comment(
                        "Base URL for the separately hosted Discord support bot API.",
                        "Example: https://your-bot-api.example.com",
                        "Do not put Discord bot tokens, webhook URLs, or shared secrets here.")
                .define("apiBaseUrl", "");

        REQUEST_TIMEOUT_SECONDS = builder
                .comment("HTTP request timeout in seconds for manual verification requests.")
                .defineInRange("requestTimeoutSeconds", 10, 1, 60);

        REMEMBER_LINKED_ACCOUNT = builder
                .comment("Store successful linked account state in a client-local file for future playtest reports.")
                .define("rememberLinkedAccount", true);

        builder.pop();

        CONFIG_SPEC = builder.build();
    }

    private MinecraftVerificationConfig() {
    }

    public static Values values() {
        return new Values(
                ENABLED.get(),
                API_BASE_URL.get(),
                REQUEST_TIMEOUT_SECONDS.get(),
                REMEMBER_LINKED_ACCOUNT.get()
        );
    }

    public record Values(
            boolean enabled,
            String apiBaseUrl,
            int requestTimeoutSeconds,
            boolean rememberLinkedAccount
    ) {
    }
}
