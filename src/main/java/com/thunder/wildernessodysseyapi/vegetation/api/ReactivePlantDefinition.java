package com.thunder.wildernessodysseyapi.vegetation.api;

import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable compatibility definition for one registered plant block.
 *
 * @param traits declared environment reactions used by the plant
 * @param behavior bounded state resolver invoked only when the plant is sampled
 */
public record ReactivePlantDefinition(
        Set<ReactivePlantTrait> traits,
        ReactivePlantBehavior behavior
) {

    /** Validates and defensively copies compatibility metadata. */
    public ReactivePlantDefinition {
        traits = Set.copyOf(Objects.requireNonNull(traits, "traits"));
        if (traits.isEmpty()) {
            throw new IllegalArgumentException("A reactive plant needs at least one trait");
        }
        behavior = Objects.requireNonNull(behavior, "behavior");
    }

    /** Creates a definition whose behavior is supplied by the compatibility module. */
    public static ReactivePlantDefinition of(
            Set<ReactivePlantTrait> traits,
            ReactivePlantBehavior behavior
    ) {
        return new ReactivePlantDefinition(traits, behavior);
    }

    /** Creates metadata-only registration that never replaces a block state. */
    public static ReactivePlantDefinition observe(Set<ReactivePlantTrait> traits) {
        return new ReactivePlantDefinition(traits, ReactivePlantUpdateContext::state);
    }

    /** Safely resolves a behavior that returned {@code null}. */
    BlockState resolve(ReactivePlantUpdateContext context) {
        BlockState desired = behavior.update(context);
        return desired == null ? context.state() : desired;
    }
}
