package com.thunder.wildernessodysseyapi.telemetry;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server configuration for player telemetry exports.
 */
public final class PlayerTelemetryConfig {
    public static final ModConfigSpec CONFIG_SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.ConfigValue<String> GEO_IP_ENDPOINT;
    public static final ModConfigSpec.ConfigValue<String> ACCOUNT_AGE_ENDPOINT;
    public static final ModConfigSpec.ConfigValue<String> SHEET_WEBHOOK_URL;
    public static final ModConfigSpec.IntValue REQUEST_TIMEOUT_SECONDS;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("playerTelemetry");

        ENABLED = BUILDER.comment("Master toggle for exporting player telemetry to a Google Sheets webhook.")
                .define("enabled", false);

        GEO_IP_ENDPOINT = BUILDER.comment(
                        "Geo IP lookup endpoint used to resolve state/country. Use {ip} as a placeholder.",
                        "Example: https://ipapi.co/{ip}/json/")
                .define("geoIpEndpoint", "https://ipapi.co/{ip}/json/");

        ACCOUNT_AGE_ENDPOINT = BUILDER.comment(
                        "Endpoint for player name history lookup to estimate account age. Use {uuid} placeholder.",
                        "Example: https://api.mojang.com/user/profiles/{uuid}/names")
                .define("accountAgeEndpoint", "https://api.mojang.com/user/profiles/{uuid}/names");

        SHEET_WEBHOOK_URL = BUILDER.comment(
                        "Google Sheets webhook URL (Apps Script or other endpoint) that accepts JSON payloads.",
                        "Leave blank to disable exports even when telemetry is enabled.")
                .define("sheetWebhookUrl", "");

        REQUEST_TIMEOUT_SECONDS = BUILDER.comment("HTTP request timeout in seconds for telemetry lookups.")
                .defineInRange("requestTimeoutSeconds", 10, 1, 60);

        BUILDER.pop();

        CONFIG_SPEC = BUILDER.build();
    }

    private PlayerTelemetryConfig() {
    }

    public static TelemetryConfigValues values() {
        return new TelemetryConfigValues(
                ENABLED.get(),
                GEO_IP_ENDPOINT.get(),
                ACCOUNT_AGE_ENDPOINT.get(),
                SHEET_WEBHOOK_URL.get(),
                REQUEST_TIMEOUT_SECONDS.get()
        );
    }

    public record TelemetryConfigValues(
            boolean enabled,
            String geoIpEndpoint,
            String accountAgeEndpoint,
            String sheetWebhookUrl,
            int requestTimeoutSeconds
    ) {
    }
}
