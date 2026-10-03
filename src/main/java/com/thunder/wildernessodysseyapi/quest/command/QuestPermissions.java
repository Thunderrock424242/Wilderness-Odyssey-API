package com.thunder.wildernessodysseyapi.quest.command;

import com.thunder.wildernessodysseyapi.quest.config.QuestConfig;
import net.minecraft.server.level.ServerPlayer;

/** One permission policy for commands and authenticated requests; clients cannot choose levels or owners. */
public final class QuestPermissions {
    private QuestPermissions() { }
    public static boolean mayPerform(ServerPlayer player, QuestOperation operation) {
        int level = 0;
        for (int candidate = 1; candidate <= 4; candidate++) if (player.hasPermissions(candidate)) level = candidate;
        return allowed(level, operation, QuestConfig.values());
    }
    public static boolean allowed(int level, QuestOperation operation, QuestConfig.Settings settings) {
        if (!settings.enabled() && operation != QuestOperation.VIEW && operation != QuestOperation.RECEIPTS) return false;
        int minimum = switch (operation) {
            case VIEW, TRACK, UNTRACK, CLAIM -> 0;
            case VALIDATE -> settings.editLevel();
            case PUBLISH -> settings.publishLevel();
            case RECEIPTS -> 4;
        };
        return level >= minimum;
    }
}
