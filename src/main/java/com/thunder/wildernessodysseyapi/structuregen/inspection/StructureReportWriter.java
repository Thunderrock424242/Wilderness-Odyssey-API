package com.thunder.wildernessodysseyapi.structuregen.inspection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thunder.wildernessodysseyapi.structuregen.content.ContentManifestStatus;
import com.thunder.wildernessodysseyapi.structuregen.pipeline.SafeFilePublisher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/** Writes deterministic text and JSON inspection reports beneath a caller-owned report root. */
public final class StructureReportWriter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Writes {@code <base>-report.txt} and {@code <base>-report.json}. */
    public ReportPaths write(StructureInspectionReport report, Path reportRoot, String baseName) throws IOException {
        Path normalizedRoot = reportRoot.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalizedRoot)) {
            throw new IOException("Refusing symbolic-link inspection report root: " + normalizedRoot);
        }
        Files.createDirectories(normalizedRoot);
        Path textPath = normalizedRoot.resolve(baseName + "-report.txt").normalize();
        Path jsonPath = normalizedRoot.resolve(baseName + "-report.json").normalize();
        requireContained(normalizedRoot, textPath);
        requireContained(normalizedRoot, jsonPath);
        rejectSymbolicLinkDestination(textPath);
        rejectSymbolicLinkDestination(jsonPath);

        // Serialize both complete reports before replacing either predictable destination.
        Path temporaryText = Files.createTempFile(normalizedRoot, "." + baseName + "-", ".txt.tmp");
        Path temporaryJson = Files.createTempFile(normalizedRoot, "." + baseName + "-", ".json.tmp");
        try {
            Files.writeString(temporaryText, formatText(report), StandardCharsets.UTF_8);
            Files.writeString(temporaryJson, GSON.toJson(report) + System.lineSeparator(), StandardCharsets.UTF_8);
            SafeFilePublisher.publish(temporaryText, textPath);
            SafeFilePublisher.publish(temporaryJson, jsonPath);
        } finally {
            Files.deleteIfExists(temporaryText);
            Files.deleteIfExists(temporaryJson);
        }
        return new ReportPaths(textPath, jsonPath);
    }

    /** Formats the complete human-readable inspection and analysis report. */
    public String formatText(StructureInspectionReport report) {
        StringBuilder output = new StringBuilder("## StructureGen Inspector\n\n");
        output.append("File: ").append(report.file()).append('\n');
        output.append("Name: ").append(report.name()).append('\n');
        output.append("DataVersion: ").append(report.dataVersion()).append('\n');
        output.append("Size: ").append(report.size().x()).append(" x ")
                .append(report.size().y()).append(" x ").append(report.size().z()).append('\n');
        output.append("Bounding volume: ").append(report.boundingVolume()).append("\n\n");
        output.append("Stored block records: ").append(report.storedBlocks()).append('\n');
        output.append("Explicit air records: ").append(report.explicitAirBlocks()).append('\n');
        output.append("Occupied (non-air) blocks: ").append(report.occupiedBlocks()).append('\n');
        output.append("Occupied density: ")
                .append(String.format(Locale.ROOT, "%.6f", report.occupiedDensity()))
                .append(" (occupied / bounding volume)\n");
        output.append("Palettes: ").append(report.paletteCount()).append('\n');
        output.append("Primary palette entries: ").append(report.primaryPaletteSize()).append('\n');
        output.append("Unique block types: ").append(report.uniqueBlockTypes()).append('\n');
        output.append("Block-state variants used: ").append(report.blockStateVariants()).append('\n');
        output.append("Block entities: ").append(report.blockEntityCount()).append('\n');
        output.append("Entities: ").append(report.entityCount()).append("\n\n");

        output.append("Block usage by namespace (unique types / stored records):\n");
        if (report.namespaceUsage().isEmpty()) {
            output.append("  (none)\n");
        } else {
            report.namespaceUsage().forEach((namespace, usage) -> output.append("  ")
                    .append(namespace).append(": ")
                    .append(usage.blockTypes())
                    .append(usage.blockTypes() == 1 ? " block type / " : " block types / ")
                    .append(usage.blockRecords())
                    .append(usage.blockRecords() == 1L ? " record\n" : " records\n"));
        }

        output.append("\nUnknown or unsupported tags:\n");
        if (report.unknownOrUnsupportedTags().isEmpty()) {
            output.append("  (none)\n");
        } else {
            report.unknownOrUnsupportedTags().forEach(tag -> output.append("  - ").append(tag).append('\n'));
        }

        output.append("\nContent policy and dependencies:\n");
        if (report.contentManifestStatus() == ContentManifestStatus.ABSENT) {
            output.append("  Content manifest status: absent (no StructureGen content manifest)\n");
        } else if (report.contentManifestStatus() == ContentManifestStatus.PARTIAL) {
            output.append("  Content manifest status: partial (schemaVersion ")
                    .append(report.contentManifestSchemaVersion())
                    .append("; values below may be incomplete)\n");
        } else {
            output.append("  Content manifest status: verified (schemaVersion ")
                    .append(report.contentManifestSchemaVersion()).append(")\n");
        }
        output.append("  External namespaces used:\n");
        appendStrings(output, report.externalNamespacesUsed());
        if (report.contentManifestStatus() != ContentManifestStatus.ABSENT) {
            output.append("  Installed mod blocks allowed: ")
                    .append(report.allowInstalledModBlocks() ? "yes" : "no").append('\n');
            output.append("  Required external mod IDs:\n");
            appendStrings(output, report.requiredMods());
            output.append("  Explicitly enabled functional systems:\n");
            appendStrings(output, report.enabledFunctionalSystems());
            output.append("  Semantic material resolutions:\n");
            if (report.resolvedMaterials().isEmpty()) {
                output.append("    (none)\n");
            } else {
                report.resolvedMaterials().forEach(material -> {
                    output.append("    ").append(material.role()).append(" -> ")
                            .append(material.selectedBlock()).append(" [")
                            .append(material.intent()).append(", ").append(material.source())
                            .append(", fallback available: ")
                            .append(material.fallbackAvailable() ? "yes" : "no").append("]\n");
                    material.rejectedCandidates().forEach(rejected -> output.append("      skipped ")
                            .append(rejected.blockId()).append(": ").append(rejected.reason()).append('\n'));
                });
            }
        }

        output.append("\nMost common blocks:\n");
        int rank = 1;
        for (StructureInspectionReport.BlockFrequency frequency : report.blockFrequencies()) {
            output.append("  ").append(rank++).append(". ").append(frequency.block())
                    .append(" - ").append(frequency.count()).append('\n');
        }

        output.append("\nInferred categories (name-based; categories may overlap):\n");
        report.categoryCounts().forEach((category, count) -> output.append("  ")
                .append(category).append(": ").append(count).append('\n'));

        output.append("\nBlock entity types:\n");
        appendCounts(output, report.blockEntityTypes());
        output.append("\nEntity types:\n");
        appendCounts(output, report.entityTypes());

        output.append("\nVertical block distribution:\n");
        output.append("  Y | stored | occupied\n");
        for (StructureInspectionReport.VerticalLayer layer : report.verticalDistribution()) {
            output.append("  ").append(layer.y()).append(" | ").append(layer.storedBlocks())
                    .append(" | ").append(layer.occupiedBlocks()).append('\n');
        }

        output.append("\nComplete palettes:\n");
        for (StructureInspectionReport.PaletteReport palette : report.palettes()) {
            output.append("\n  Palette #").append(palette.index()).append('\n');
            for (StructureInspectionReport.PaletteEntry entry : palette.entries()) {
                output.append("    #").append(entry.index()).append(' ').append(entry.block());
                if (!entry.properties().isEmpty()) {
                    output.append(' ').append(entry.properties());
                }
                output.append('\n');
            }
        }
        return output.toString();
    }

    /** Formats one detailed palette entry for {@code -PpaletteIndex}. */
    public String formatPaletteEntry(StructureInspectionReport report, int entryIndex) {
        if (report.palettes().isEmpty() || entryIndex < 0
                || entryIndex >= report.palettes().getFirst().entries().size()) {
            throw new IllegalArgumentException("Primary palette index " + entryIndex + " is outside 0.."
                    + Math.max(-1, report.primaryPaletteSize() - 1));
        }
        StructureInspectionReport.PaletteEntry entry = report.palettes().getFirst().entries().get(entryIndex);
        StringBuilder output = new StringBuilder("Palette #").append(entry.index()).append("\n\n")
                .append(entry.block()).append("\n\nProperties:\n");
        if (entry.properties().isEmpty()) {
            output.append("  (none)\n");
        } else {
            entry.properties().forEach((name, value) -> output.append("  - ")
                    .append(name).append('=').append(value).append('\n'));
        }
        return output.toString();
    }

    private void appendCounts(StringBuilder output, Map<String, Long> counts) {
        if (counts.isEmpty()) {
            output.append("  (none)\n");
            return;
        }
        counts.forEach((name, count) -> output.append("  ").append(name).append(": ").append(count).append('\n'));
    }

    private void appendStrings(StringBuilder output, java.util.List<String> values) {
        if (values.isEmpty()) {
            output.append("    (none)\n");
            return;
        }
        values.forEach(value -> output.append("    - ").append(value).append('\n'));
    }

    private void requireContained(Path root, Path target) {
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Report path escapes the configured report directory: " + target);
        }
    }

    private void rejectSymbolicLinkDestination(Path destination) throws IOException {
        if (Files.isSymbolicLink(destination)) {
            throw new IOException("Refusing symbolic-link inspection report destination: " + destination);
        }
    }

    /** Paths produced by a successful report write. */
    public record ReportPaths(Path text, Path json) {
    }
}
