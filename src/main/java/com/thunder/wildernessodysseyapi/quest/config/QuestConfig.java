package com.thunder.wildernessodysseyapi.quest.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-authoritative permission and scheduling budgets composed into the existing server specification. */
public final class QuestConfig {
    public static ModConfigSpec CONFIG_SPEC;
    private static ModConfigSpec.BooleanValue enabled;
    private static ModConfigSpec.IntValue editLevel, publishLevel, requestsPerSecond, burst, inventoryPlayers;
    private QuestConfig() { }
    public record Settings(boolean enabled, int editLevel, int publishLevel, int requestsPerSecond, int burst, int inventoryPlayersPerTick) {
        public Settings {
            editLevel = Math.clamp(editLevel, 2, 4); publishLevel = Math.max(editLevel, Math.clamp(publishLevel, 3, 4));
            requestsPerSecond = Math.clamp(requestsPerSecond, 1, 20); burst = Math.clamp(burst, 1, 40); inventoryPlayersPerTick = Math.clamp(inventoryPlayersPerTick, 1, 64);
        }
    }
    public static void define(ModConfigSpec.Builder builder) {
        builder.push("quests");
        enabled = builder.comment("Enables optional custom quest progression and claims. Existing files are preserved when disabled.").define("enabled", true);
        editLevel = builder.comment("Minimum operator level for candidate validation and later authoring.").defineInRange("editPermissionLevel", 2, 2, 4);
        publishLevel = builder.comment("Minimum publication level; the effective requirement is never below editPermissionLevel.").defineInRange("publishPermissionLevel", 3, 3, 4);
        requestsPerSecond = builder.comment("Accepted player requests per second.").defineInRange("requestsPerSecond", 10, 1, 20);
        burst = builder.comment("Maximum request burst per player.").defineInRange("requestBurst", 20, 1, 40);
        inventoryPlayers = builder.comment("Maximum interested players reconciled per server tick; each player is checked no more often than every 20 ticks.").defineInRange("inventoryPlayersPerTick", 8, 1, 64);
        builder.pop();
    }
    public static Settings values() {
        if (enabled == null || CONFIG_SPEC == null || !CONFIG_SPEC.isLoaded()) return new Settings(true, 2, 3, 10, 20, 8);
        return new Settings(enabled.get(), editLevel.get(), publishLevel.get(), requestsPerSecond.get(), burst.get(), inventoryPlayers.get());
    }
}
