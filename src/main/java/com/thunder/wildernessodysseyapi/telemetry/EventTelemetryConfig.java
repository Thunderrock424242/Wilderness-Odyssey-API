package com.thunder.wildernessodysseyapi.telemetry;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server configuration for event-based telemetry exports.
 */
public final class EventTelemetryConfig {
    public static final ModConfigSpec CONFIG_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.ConfigValue<String> WEBHOOK_URL;
    public static final ModConfigSpec.IntValue REQUEST_TIMEOUT_SECONDS;
    public static final ModConfigSpec.BooleanValue INCLUDE_PLAYER_IDENTIFIERS;
    public static final ModConfigSpec.DoubleValue SAMPLE_RATE_PERCENT;
    public static final ModConfigSpec.IntValue SAMPLE_EVERY_NTH;
    public static final ModConfigSpec.BooleanValue HASH_PLAYER_IDENTIFIERS;
    public static final ModConfigSpec.ConfigValue<String> IDENTIFIER_HASH_SALT;
    public static final ModConfigSpec.IntValue RETRY_MAX_ATTEMPTS;
    public static final ModConfigSpec.IntValue RETRY_BASE_DELAY_MS;
    public static final ModConfigSpec.IntValue RETRY_MAX_DELAY_MS;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("eventTelemetry");

        ENABLED = BUILDER.comment("Master toggle for event-based telemetry.")
                .define("enabled", false);

        WEBHOOK_URL = BUILDER.comment(
                        "Webhook URL that receives event telemetry payloads.",
                        "Leave blank to disable event telemetry even when enabled.")
                .define("webhookUrl", "");

        REQUEST_TIMEOUT_SECONDS = BUILDER.comment("HTTP request timeout in seconds for event telemetry.")
                .defineInRange("requestTimeoutSeconds", 10, 1, 60);

        INCLUDE_PLAYER_IDENTIFIERS = BUILDER.comment(
                        "When true, include player name/UUID for events if they consented to telemetry.")
                .define("includePlayerIdentifiers", false);

        SAMPLE_RATE_PERCENT = BUILDER.comment(
                        "Sampling rate percentage for event telemetry (0-100).")
                .defineInRange("sampleRatePercent", 100.0, 0.0, 100.0);

        SAMPLE_EVERY_NTH = BUILDER.comment(
                        "Only export every Nth event telemetry payload.",
                        "Set to 1 to disable Nth sampling.")
                .defineInRange("sampleEveryNth", 1, 1, 1000000);

        HASH_PLAYER_IDENTIFIERS = BUILDER.comment(
                        "Hash player identifiers (UUID/name) before sending event telemetry.")
                .define("hashPlayerIdentifiers", false);

        IDENTIFIER_HASH_SALT = BUILDER.comment(
                        "Optional salt appended before hashing identifiers.")
                .define("identifierHashSalt", "");

        RETRY_MAX_ATTEMPTS = BUILDER.comment(
                        "Maximum retry attempts for event telemetry HTTP requests.")
                .defineInRange("retryMaxAttempts", 2, 0, 10);

        RETRY_BASE_DELAY_MS = BUILDER.comment(
                        "Base delay in milliseconds for exponential backoff retries.")
                .defineInRange("retryBaseDelayMs", 500, 50, 10000);

        RETRY_MAX_DELAY_MS = BUILDER.comment(
                        "Maximum delay in milliseconds for exponential backoff retries.")
                .defineInRange("retryMaxDelayMs", 5000, 100, 60000);

        BUILDER.pop();

        CONFIG_SPEC = BUILDER.build();
    }

    private EventTelemetryConfig() {
    }

    public static EventTelemetryValues values() {
        return new EventTelemetryValues(
                ENABLED.get(),
                WEBHOOK_URL.get(),
                REQUEST_TIMEOUT_SECONDS.get(),
                INCLUDE_PLAYER_IDENTIFIERS.get(),
                SAMPLE_RATE_PERCENT.get(),
                SAMPLE_EVERY_NTH.get(),
                HASH_PLAYER_IDENTIFIERS.get(),
                IDENTIFIER_HASH_SALT.get(),
                RETRY_MAX_ATTEMPTS.get(),
                RETRY_BASE_DELAY_MS.get(),
                RETRY_MAX_DELAY_MS.get()
        );
    }

    public record EventTelemetryValues(
            boolean enabled,
            String webhookUrl,
            int requestTimeoutSeconds,
            boolean includePlayerIdentifiers,
            double sampleRatePercent,
            int sampleEveryNth,
            boolean hashPlayerIdentifiers,
            String identifierHashSalt,
            int retryMaxAttempts,
            int retryBaseDelayMs,
            int retryMaxDelayMs
    ) {
    }
}
