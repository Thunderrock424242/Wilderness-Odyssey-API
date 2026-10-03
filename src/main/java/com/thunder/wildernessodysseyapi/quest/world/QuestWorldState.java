package com.thunder.wildernessodysseyapi.quest.world;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Shared facts never identify an arbitrary credited player; durable store provisions world identity. */
public record QuestWorldState(UUID worldId, Set<ResourceLocation> facts) {
    public QuestWorldState {
        Objects.requireNonNull(worldId);
        facts = Set.copyOf(facts);
    }

    public static QuestWorldState empty(UUID world) {
        return new QuestWorldState(world, Set.of());
    }
}
