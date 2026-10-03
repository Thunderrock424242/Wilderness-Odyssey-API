package com.thunder.wildernessodysseyapi.quest.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Typed, immutable server evidence contract. Clients cannot supply objective completion. */
public record ObjectiveDefinition(ResourceLocation id, long goal, Parameters parameters,
                                  List<ResourceLocation> dependsOn) {
    public ObjectiveDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(parameters);
        if (goal <= 0) {
            throw new IllegalArgumentException("Objective goal must be positive.");
        }
        dependsOn = List.copyOf(dependsOn);
    }

    public sealed interface Parameters permits Possession, Dimension, Lore, CustomEvent { }

    public record Possession(Set<ResourceLocation> items, Set<ResourceLocation> tags) implements Parameters {
        public Possession {
            items = Set.copyOf(items);
            tags = Set.copyOf(tags);
        }
    }

    public record Dimension(ResourceLocation dimension) implements Parameters { }
    public record Lore(ResourceLocation lore) implements Parameters { }
    public record CustomEvent(ResourceLocation producer) implements Parameters { }
}
