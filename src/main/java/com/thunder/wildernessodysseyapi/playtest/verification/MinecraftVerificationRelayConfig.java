package com.thunder.wildernessodysseyapi.playtest.verification;

import com.thunder.wildernessodysseyapi.config.WildernessConfigSpecs;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side Discord webhook relay settings for official playtest verification.
 */
public final class MinecraftVerificationRelayConfig {
    public static ModConfigSpec CONFIG_SPEC;

    private static ModConfigSpec.BooleanValue ENABLE_SERVER_VERIFICATION_RELAY;
    private static ModConfigSpec.ConfigValue<String> DISCORD_VERIFICATION_WEBHOOK_URL;
    private static ModConfigSpec.IntValue REQUEST_TIMEOUT_SECONDS;

    static {
        WildernessConfigSpecs.initialize();
    }

    /** Defines the verification-relay category in the unified server config. */
    public static void define(ModConfigSpec.Builder builder) {

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
