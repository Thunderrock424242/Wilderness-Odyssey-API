package com.thunder.wildernessodysseyapi.structuregen.model;

import java.util.List;

/**
 * One explicitly stored structure block, including optional block-entity and raw entry data.
 *
 * @param position local integer position
 * @param state canonical block state from the primary palette
 * @param blockEntitySnbt optional typed block-entity compound encoded as SNBT
 * @param markers StructureGen authoring markers attached to this coordinate
 * @param rawEntrySnbt original block-list entry for unknown-field preservation
 * @param sourcePaletteIndex original palette index, or {@code -1} for authored blocks
 */
public record StructureBlock(
        StructurePosition position,
        StructureBlockState state,
        String blockEntitySnbt,
        List<String> markers,
        String rawEntrySnbt,
        int sourcePaletteIndex
) {

    public StructureBlock {
        markers = List.copyOf(markers);
    }

    /** Creates a normal authored block without imported raw NBT details. */
    public StructureBlock(
            StructurePosition position,
            StructureBlockState state,
            String blockEntitySnbt,
            List<String> markers
    ) {
        this(position, state, blockEntitySnbt, markers, null, -1);
    }

    /** Returns whether this is explicitly represented air rather than an absent coordinate. */
    public boolean isExplicitAir() {
        return "minecraft:air".equals(state.blockId());
    }
}
