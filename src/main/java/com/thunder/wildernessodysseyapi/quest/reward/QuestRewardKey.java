package com.thunder.wildernessodysseyapi.quest.reward;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/** Permanent claim identity; publication revisions never create another claim. */
public record QuestRewardKey(UUID worldId, UUID playerId, ResourceLocation questId, long runNumber, ResourceLocation rewardId) {
    public QuestRewardKey {
        Objects.requireNonNull(worldId);
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(questId);
        Objects.requireNonNull(rewardId);
        if (runNumber < 0 || runNumber > 100_000) throw new IllegalArgumentException("Unsupported reward run number.");
    }
}
