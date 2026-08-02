package com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla;

import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StructureBlock;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts explicit structure data markers into Wilderness source water.
 *
 * <p>Template authors place a structure block in DATA mode with metadata
 * {@value #WATER_MARKER}. Conversion runs on the processed placement list, so
 * it happens once per placement and never scans an already-loaded structure.</p>
 */
public final class StructureWaterMarkerAdapter {

    public static final String WATER_MARKER = "wildernessodysseyapi:water";

    private StructureWaterMarkerAdapter() {
    }

    /** Returns whether one processed structure entry is the explicit water marker. */
    public static boolean isWaterMarker(StructureTemplate.StructureBlockInfo info) {
        if (info == null
                || !info.state().is(Blocks.STRUCTURE_BLOCK)
                || !info.state().hasProperty(StructureBlock.MODE)
                || info.state().getValue(StructureBlock.MODE) != StructureMode.DATA) {
            return false;
        }
        CompoundTag nbt = info.nbt();
        return nbt != null && WATER_MARKER.equals(nbt.getString("metadata").trim());
    }

    /** Replaces one marker while preserving its already-transformed world position. */
    public static StructureTemplate.StructureBlockInfo convertMarker(
            StructureTemplate.StructureBlockInfo info
    ) {
        if (!isWaterMarker(info)) {
            return info;
        }
        return new StructureTemplate.StructureBlockInfo(
                info.pos(),
                WildernessFluidRegistry.WILDERNESS_WATER_BLOCK.get().defaultBlockState(),
                null
        );
    }

    /**
     * Converts markers with a lazy copy, returning the original list when none exist.
     */
    public static List<StructureTemplate.StructureBlockInfo> convertMarkers(
            List<StructureTemplate.StructureBlockInfo> processed
    ) {
        List<StructureTemplate.StructureBlockInfo> converted = null;
        for (int index = 0; index < processed.size(); index++) {
            StructureTemplate.StructureBlockInfo original = processed.get(index);
            StructureTemplate.StructureBlockInfo replacement = convertMarker(original);
            if (replacement == original) {
                continue;
            }
            if (converted == null) {
                converted = new ArrayList<>(processed);
            }
            converted.set(index, replacement);
        }
        return converted == null ? processed : converted;
    }
}
