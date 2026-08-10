package com.thunder.wildernessodysseyapi.structuregen.blueprint;

import com.thunder.wildernessodysseyapi.structuregen.model.StructurePosition;

import java.util.List;

/** Optional exported/imported entity entry in Blueprint Format v1. */
public record BlueprintEntity(
        List<Double> position,
        StructurePosition blockPosition,
        String entityNbtSnbt,
        String rawEntrySnbt
) {

    public BlueprintEntity {
        position = List.copyOf(position);
    }
}
