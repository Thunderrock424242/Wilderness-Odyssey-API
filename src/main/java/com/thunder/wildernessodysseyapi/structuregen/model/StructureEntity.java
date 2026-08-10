package com.thunder.wildernessodysseyapi.structuregen.model;

import java.util.List;

/**
 * Entity entry stored by a Minecraft structure template.
 *
 * @param position exact local entity position
 * @param blockPosition integer anchor used by the template format
 * @param entityNbtSnbt typed entity payload encoded as SNBT
 * @param rawEntrySnbt original entity-list entry for unknown-field preservation
 */
public record StructureEntity(
        List<Double> position,
        StructurePosition blockPosition,
        String entityNbtSnbt,
        String rawEntrySnbt
) {

    public StructureEntity {
        position = List.copyOf(position);
    }
}
