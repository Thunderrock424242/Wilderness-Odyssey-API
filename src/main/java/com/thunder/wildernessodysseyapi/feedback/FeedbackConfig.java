package com.thunder.wildernessodysseyapi.feedback;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server configuration for player feedback submissions.
 */
public final class FeedbackConfig {
    public static final ModConfigSpec CONFIG_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.ConfigValue<String> WEBHOOK_URL;
    public static final ModConfigSpec.IntValue REQUEST_TIMEOUT_SECONDS;
    public static final ModConfigSpec.IntValue MAX_MESSAGE_LENGTH;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("feedback");

        ENABLED = BUILDER.comment("Master toggle for player feedback submissions.")
                .define("enabled", true);

        WEBHOOK_URL = BUILDER.comment(
                        "Discord webhook URL that receives feedback submissions.",
                        "Leave blank to disable feedback submissions.")
                .define("webhookUrl", "");

        REQUEST_TIMEOUT_SECONDS = BUILDER.comment("HTTP request timeout in seconds for feedback submissions.")
                .defineInRange("requestTimeoutSeconds", 10, 1, 60);

        MAX_MESSAGE_LENGTH = BUILDER.comment("Maximum feedback message length (Discord has a 2000 char limit).")
                .defineInRange("maxMessageLength", 500, 20, 2000);

        BUILDER.pop();

        CONFIG_SPEC = BUILDER.build();
    }

    private FeedbackConfig() {
    }

    public static FeedbackConfigValues values() {
        return new FeedbackConfigValues(
                ENABLED.get(),
                WEBHOOK_URL.get(),
                REQUEST_TIMEOUT_SECONDS.get(),
                MAX_MESSAGE_LENGTH.get()
        );
    }

    public record FeedbackConfigValues(
            boolean enabled,
            String webhookUrl,
            int requestTimeoutSeconds,
            int maxMessageLength
    ) {
    }
}
