package com.thunder.wildernessodysseyapi.quest.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Original earned value is persisted independently of later definition edits. No command reward exists. */
public record RewardDefinition(ResourceLocation id, Value value) {
    public RewardDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(value);
    }

    public sealed interface Value permits Item, Experience, Lore { }
    public record Item(ResourceLocation item, int count) implements Value { }
    public record Experience(int amount) implements Value { }
    public record Lore(ResourceLocation lore) implements Value { }
}
