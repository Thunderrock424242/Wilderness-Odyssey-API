package com.thunder.wildernessodysseyapi.quest.objective;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable complete inventory sample, captured on the server thread with each slot included once. */
public record ItemObservation(List<Stack> stacks) {
    public ItemObservation {
        stacks = List.copyOf(stacks);
        if (stacks.size() > 256) {
            throw new IllegalArgumentException("Too many inventory slots.");
        }
    }

    public static ItemObservation empty() {
        return new ItemObservation(List.of());
    }

    public record Stack(ResourceLocation item, long count, Set<ResourceLocation> tags) {
        public Stack {
            Objects.requireNonNull(item);
            if (count < 0) {
                throw new IllegalArgumentException("Negative inventory count.");
            }
            tags = Set.copyOf(tags);
        }
    }
}
