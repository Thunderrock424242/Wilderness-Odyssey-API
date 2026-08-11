package com.thunder.wildernessodysseyapi.structuregen.inspection;

import com.thunder.wildernessodysseyapi.structuregen.content.ContentManifestStatus;
import com.thunder.wildernessodysseyapi.structuregen.content.ResolvedMaterial;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureSize;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        Map<String, NamespaceUsage> namespaceUsage,
        ContentManifestStatus contentManifestStatus,
        int contentManifestSchemaVersion,
        boolean allowInstalledModBlocks,
        List<String> requiredMods,
        List<String> externalNamespacesUsed,
        List<String> enabledFunctionalSystems,
        List<ResolvedMaterial> resolvedMaterials,
        List<PaletteReport> palettes,
        List<VerticalLayer> verticalDistribution,
        Map<String, Long> categoryCounts,
        Map<String, Long> blockEntityTypes,
        Map<String, Long> entityTypes
) {

    public StructureInspectionReport {
        unknownOrUnsupportedTags = List.copyOf(unknownOrUnsupportedTags);
        blockFrequencies = List.copyOf(blockFrequencies);
        namespaceUsage = Collections.unmodifiableMap(new LinkedHashMap<>(namespaceUsage));
        contentManifestStatus = Objects.requireNonNull(contentManifestStatus, "contentManifestStatus");
        requiredMods = List.copyOf(requiredMods);
        externalNamespacesUsed = List.copyOf(externalNamespacesUsed);
        enabledFunctionalSystems = List.copyOf(enabledFunctionalSystems);
        resolvedMaterials = List.copyOf(resolvedMaterials);
        palettes = List.copyOf(palettes);
        verticalDistribution = List.copyOf(verticalDistribution);
        categoryCounts = Collections.unmodifiableMap(new LinkedHashMap<>(categoryCounts));
        blockEntityTypes = Collections.unmodifiableMap(new LinkedHashMap<>(blockEntityTypes));
        entityTypes = Collections.unmodifiableMap(new LinkedHashMap<>(entityTypes));
    }

    /** Frequency for one block ID, including explicitly stored air. */
    public record BlockFrequency(String block, long count) {
    }

    /** Unique block types and stored block records supplied by one resource namespace. */
    public record NamespaceUsage(int blockTypes, long blockRecords) {
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
