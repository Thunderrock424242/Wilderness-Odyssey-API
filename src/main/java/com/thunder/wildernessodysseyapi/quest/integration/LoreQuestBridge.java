package com.thunder.wildernessodysseyapi.quest.integration;

import com.thunder.wildernessodysseyapi.quest.objective.QuestEvent;
import com.thunder.wildernessodysseyapi.quest.runtime.QuestServerEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/** Maps legacy lore IDs without rewriting the lore owner's serialized collection. */
public final class LoreQuestBridge {
    private LoreQuestBridge() { }
    public static Optional<ResourceLocation> map(String legacy) {
        if (legacy == null || !legacy.matches("lore_[0-9]{3,6}")) return Optional.empty();
        return Optional.of(ResourceLocation.fromNamespaceAndPath("wildernessodysseyapi", legacy));
    }
    public static void onCollected(ServerPlayer player, String legacy) {
        map(legacy).ifPresent(id -> QuestServerEvents.observe(player, new QuestEvent.Lore(id)));
    }
}
