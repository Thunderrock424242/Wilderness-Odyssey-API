package com.thunder.wildernessodysseyapi.cinematic;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Process-wide registry of immutable cinematic definitions. */
public final class CinematicSequenceRegistry {
    private static final Map<ResourceLocation, CinematicSequence> SEQUENCES = new LinkedHashMap<>();

    private CinematicSequenceRegistry() {
    }

    /** Registers one immutable sequence definition during mod bootstrap. */
    public static synchronized void register(CinematicSequence sequence) {
        CinematicSequence previous = SEQUENCES.putIfAbsent(sequence.id(), sequence);
        if (previous != null && previous != sequence) {
            throw new IllegalStateException("Duplicate cinematic sequence id: " + sequence.id());
        }
    }

    public static synchronized Optional<CinematicSequence> get(ResourceLocation id) {
        return Optional.ofNullable(SEQUENCES.get(id));
    }

    public static synchronized Collection<ResourceLocation> ids() {
        return List.copyOf(SEQUENCES.keySet());
    }
}
