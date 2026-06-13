package com.thunder.wildernessodysseyapi.playtest.verification;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side Discord webhook relay settings for official playtest verification.
 */
public final class MinecraftVerificationRelayConfig {
    public static final ModConfigSpec CONFIG_SPEC;

    private static final ModConfigSpec.BooleanValue ENABLE_SERVER_VERIFICATION_RELAY;
    private static final ModConfigSpec.ConfigValue<String> DISCORD_VERIFICATION_WEBHOOK_URL;
    private static final ModConfigSpec.IntValue REQUEST_TIMEOUT_SECONDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Server-side Minecraft account verification relay settings.").push("verificationRelay");

        ENABLE_SERVER_VERIFICATION_RELAY = builder
                .comment("Enable /wo link code relay messages to the private Discord verification webhook.")
                .define("enableServerVerificationRelay", true);

        DISCORD_VERIFICATION_WEBHOOK_URL = builder
                .comment(
                        "Private Discord webhook URL for the verification relay channel.",
                        "Keep this value on the official server only. Do not ship it in client configs.")
                .define("discordVerificationWebhookUrl", "");

        REQUEST_TIMEOUT_SECONDS = builder
                .comment("HTTP request timeout in seconds for verification relay webhook posts.")
                .defineInRange("requestTimeoutSeconds", 10, 1, 60);

        builder.pop();

        CONFIG_SPEC = builder.build();
    }

    private MinecraftVerificationRelayConfig() {
    }

    public static Values values() {
        return new Values(
                ENABLE_SERVER_VERIFICATION_RELAY.get(),
                DISCORD_VERIFICATION_WEBHOOK_URL.get(),
                REQUEST_TIMEOUT_SECONDS.get()
        );
    }

    public record Values(
            boolean enableServerVerificationRelay,
            String discordVerificationWebhookUrl,
            int requestTimeoutSeconds
    ) {
    }
}
