package com.thunder.wildernessodysseyapi.structuregen.inspection;

import com.thunder.wildernessodysseyapi.structuregen.model.StructureSize;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Complete serializable inspection and statistical analysis of one structure template. */
public record StructureInspectionReport(
        String file,
        String name,
        int dataVersion,
        StructureSize size,
        long boundingVolume,
        int storedBlocks,
        int explicitAirBlocks,
        int occupiedBlocks,
        double occupiedDensity,
        int paletteCount,
        int primaryPaletteSize,
        int uniqueBlockTypes,
        int blockStateVariants,
        int blockEntityCount,
        int entityCount,
        List<String> unknownOrUnsupportedTags,
        List<BlockFrequency> blockFrequencies,
        List<PaletteReport> palettes,
        List<VerticalLayer> verticalDistribution,
        Map<String, Long> categoryCounts,
        Map<String, Long> blockEntityTypes,
        Map<String, Long> entityTypes
) {

    public StructureInspectionReport {
        unknownOrUnsupportedTags = List.copyOf(unknownOrUnsupportedTags);
        blockFrequencies = List.copyOf(blockFrequencies);
        palettes = List.copyOf(palettes);
        verticalDistribution = List.copyOf(verticalDistribution);
        categoryCounts = Collections.unmodifiableMap(new LinkedHashMap<>(categoryCounts));
        blockEntityTypes = Collections.unmodifiableMap(new LinkedHashMap<>(blockEntityTypes));
        entityTypes = Collections.unmodifiableMap(new LinkedHashMap<>(entityTypes));
    }

    /** Frequency for one block ID, including explicitly stored air. */
    public record BlockFrequency(String block, long count) {
    }

    /** Full state entries for one source palette. */
    public record PaletteReport(int index, List<PaletteEntry> entries) {

        public PaletteReport {
            entries = List.copyOf(entries);
        }
    }

    /** One indexed Minecraft palette entry. */
    public record PaletteEntry(int index, String block, Map<String, String> properties) {

        public PaletteEntry {
            properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
        }
    }

    /** Stored and occupied block totals for one local Y layer. */
    public record VerticalLayer(int y, long storedBlocks, long occupiedBlocks) {
    }
}
