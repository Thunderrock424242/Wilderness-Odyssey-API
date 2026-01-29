package com.thunder.wildernessodysseyapi.ModPackPatches.telemetry;

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
    public static final ModConfigSpec.BooleanValue EXPORT_ON_LOGOUT;
    public static final ModConfigSpec.BooleanValue INCLUDE_SPARK_REPORT;
    public static final ModConfigSpec.DoubleValue SAMPLE_RATE_PERCENT;
    public static final ModConfigSpec.IntValue SAMPLE_EVERY_NTH;
    public static final ModConfigSpec.BooleanValue HASH_PLAYER_IDENTIFIERS;
    public static final ModConfigSpec.ConfigValue<String> IDENTIFIER_HASH_SALT;
    public static final ModConfigSpec.IntValue GEO_CACHE_TTL_SECONDS;
    public static final ModConfigSpec.IntValue ACCOUNT_AGE_CACHE_TTL_SECONDS;
    public static final ModConfigSpec.IntValue RETRY_MAX_ATTEMPTS;
    public static final ModConfigSpec.IntValue RETRY_BASE_DELAY_MS;
    public static final ModConfigSpec.IntValue RETRY_MAX_DELAY_MS;

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

        EXPORT_ON_LOGOUT = BUILDER.comment(
                        "Send a telemetry export when a player logs out.",
                        "If disabled, telemetry only exports on login.")
                .define("exportOnLogout", true);

        INCLUDE_SPARK_REPORT = BUILDER.comment(
                        "When Spark is installed, attach a Spark report URL to logout exports.",
                        "Requires exportOnLogout to be enabled.")
                .define("includeSparkReport", false);

        SAMPLE_RATE_PERCENT = BUILDER.comment(
                        "Sampling rate percentage for player telemetry (0-100).",
                        "Use together with sampleEveryNth for large servers.")
                .defineInRange("sampleRatePercent", 100.0, 0.0, 100.0);

        SAMPLE_EVERY_NTH = BUILDER.comment(
                        "Only export every Nth player telemetry event.",
                        "Set to 1 to disable Nth sampling.")
                .defineInRange("sampleEveryNth", 1, 1, 1000000);

        HASH_PLAYER_IDENTIFIERS = BUILDER.comment(
                        "Hash player identifiers (UUID/name) before sending telemetry.")
                .define("hashPlayerIdentifiers", false);

        IDENTIFIER_HASH_SALT = BUILDER.comment(
                        "Optional salt appended before hashing identifiers.")
                .define("identifierHashSalt", "");

        GEO_CACHE_TTL_SECONDS = BUILDER.comment(
                        "Cache GeoIP lookup results per UUID for this many seconds.")
                .defineInRange("geoCacheTtlSeconds", 21600, 0, 604800);

        ACCOUNT_AGE_CACHE_TTL_SECONDS = BUILDER.comment(
                        "Cache account age lookup results per UUID for this many seconds.")
                .defineInRange("accountAgeCacheTtlSeconds", 86400, 0, 604800);

        RETRY_MAX_ATTEMPTS = BUILDER.comment(
                        "Maximum retry attempts for telemetry HTTP requests.")
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

    private PlayerTelemetryConfig() {
    }

    public static TelemetryConfigValues values() {
        return new TelemetryConfigValues(
                ENABLED.get(),
                GEO_IP_ENDPOINT.get(),
                ACCOUNT_AGE_ENDPOINT.get(),
                SHEET_WEBHOOK_URL.get(),
                REQUEST_TIMEOUT_SECONDS.get(),
                EXPORT_ON_LOGOUT.get(),
                INCLUDE_SPARK_REPORT.get(),
                SAMPLE_RATE_PERCENT.get(),
                SAMPLE_EVERY_NTH.get(),
                HASH_PLAYER_IDENTIFIERS.get(),
                IDENTIFIER_HASH_SALT.get(),
                GEO_CACHE_TTL_SECONDS.get(),
                ACCOUNT_AGE_CACHE_TTL_SECONDS.get(),
                RETRY_MAX_ATTEMPTS.get(),
                RETRY_BASE_DELAY_MS.get(),
                RETRY_MAX_DELAY_MS.get()
        );
    }

    public record TelemetryConfigValues(
            boolean enabled,
            String geoIpEndpoint,
            String accountAgeEndpoint,
            String sheetWebhookUrl,
            int requestTimeoutSeconds,
            boolean exportOnLogout,
            boolean includeSparkReport,
            double sampleRatePercent,
            int sampleEveryNth,
            boolean hashPlayerIdentifiers,
            String identifierHashSalt,
            int geoCacheTtlSeconds,
            int accountAgeCacheTtlSeconds,
            int retryMaxAttempts,
            int retryBaseDelayMs,
            int retryMaxDelayMs
    ) {
    }
}
