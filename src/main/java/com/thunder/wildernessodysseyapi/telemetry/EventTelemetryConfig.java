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
                INCLUDE_PLAYER_IDENTIFIERS.get()
        );
    }

    public record EventTelemetryValues(
            boolean enabled,
            String webhookUrl,
            int requestTimeoutSeconds,
            boolean includePlayerIdentifiers
    ) {
    }
}
