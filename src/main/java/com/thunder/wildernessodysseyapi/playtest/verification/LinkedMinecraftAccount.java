package com.thunder.wildernessodysseyapi.playtest.verification;

import com.google.gson.JsonObject;

import java.util.Optional;

/**
 * Client-local account link state produced by the Discord support bot verification flow.
 */
public record LinkedMinecraftAccount(
        String discordUserId,
        String minecraftUuid,
        String minecraftName,
        String verifiedAt,
        String apiBaseUrl
) {
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("discordUserId", discordUserId);
        json.addProperty("minecraftUuid", minecraftUuid);
        json.addProperty("minecraftName", minecraftName);
        json.addProperty("verifiedAt", verifiedAt);
        json.addProperty("apiBaseUrl", apiBaseUrl);
        return json;
    }

    public static Optional<LinkedMinecraftAccount> fromJson(JsonObject json) {
        if (json == null) {
            return Optional.empty();
        }
        String discordUserId = getString(json, "discordUserId");
        String minecraftUuid = getString(json, "minecraftUuid");
        String minecraftName = getString(json, "minecraftName");
        String verifiedAt = getString(json, "verifiedAt");
        String apiBaseUrl = getString(json, "apiBaseUrl");

        if (isBlank(discordUserId) || isBlank(minecraftUuid) || isBlank(minecraftName)
                || isBlank(verifiedAt) || isBlank(apiBaseUrl)) {
            return Optional.empty();
        }
        return Optional.of(new LinkedMinecraftAccount(
                discordUserId,
                minecraftUuid,
                minecraftName,
                verifiedAt,
                apiBaseUrl
        ));
    }

    private static String getString(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return "";
        }
        return json.get(key).getAsString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
