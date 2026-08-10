package com.thunder.wildernessodysseyapi.structuregen.cli;

import com.thunder.wildernessodysseyapi.structuregen.blueprint.BlueprintExporter;
import com.thunder.wildernessodysseyapi.structuregen.comparison.StructureComparator;
import com.thunder.wildernessodysseyapi.structuregen.comparison.StructureComparisonReport;
import com.thunder.wildernessodysseyapi.structuregen.diagnostic.DiagnosticSeverity;
import com.thunder.wildernessodysseyapi.structuregen.diagnostic.StructureDiagnostic;
import com.thunder.wildernessodysseyapi.structuregen.inspection.StructureInspectionReport;
import com.thunder.wildernessodysseyapi.structuregen.inspection.StructureInspector;
import com.thunder.wildernessodysseyapi.structuregen.inspection.StructureReportWriter;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureModel;
import com.thunder.wildernessodysseyapi.structuregen.nbt.MinecraftStructureNbtReader;
import com.thunder.wildernessodysseyapi.structuregen.pipeline.SafeFilePublisher;
import com.thunder.wildernessodysseyapi.structuregen.pipeline.StructureGenPaths;
import com.thunder.wildernessodysseyapi.structuregen.pipeline.StructureGenerationPipeline;
import com.thunder.wildernessodysseyapi.structuregen.pipeline.StructureGenerationResult;
import com.thunder.wildernessodysseyapi.structuregen.validation.BlueprintValidator;
import com.thunder.wildernessodysseyapi.structuregen.validation.MinecraftBlockStateResolver;
import net.minecraft.SharedConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Gradle-facing entry point for StructureGen generation, inspection, export, and comparison.
 *
 * <p>This is deliberately an offline development tool. It never opens a Minecraft level or
 * participates in the mod's runtime world-generation lifecycle.</p>
 */
public final class StructureGenCli {

    private StructureGenCli() {
    }

    /** Executes one StructureGen command and exits nonzero after actionable diagnostics on failure. */
    public static void main(String[] args) {
        try {
            SharedConstants.tryDetectVersion();
            int exitCode = execute(CliArguments.parse(args));
            if (exitCode != 0) {
                System.exit(exitCode);
            }
        } catch (IllegalArgumentException | IOException exception) {
            System.err.println("[StructureGen] ERROR " + exception.getMessage());
            System.exit(1);
        }
    }

    static int execute(CliArguments arguments) throws IOException {
        return switch (arguments.command()) {
            case "generate" -> generate(arguments);
            case "inspect" -> inspect(arguments);
            case "export" -> export(arguments);
            case "compare" -> compare(arguments);
            default -> throw new IllegalArgumentException(
                    "Unknown StructureGen command '" + arguments.command()
                            + "'. Expected generate, inspect, export, or compare."
            );
        };
    }

    // Generation has the strictest path boundary because it publishes packaged binary resources.
    private static int generate(CliArguments arguments) throws IOException {
        arguments.requireOnly(Set.of("project-dir", "blueprints", "output-resources"));
        Path projectRoot = Path.of(arguments.require("project-dir"));
        StructureGenPaths paths = new StructureGenPaths(
                projectRoot,
                Path.of(arguments.require("blueprints")),
                Path.of(arguments.require("output-resources"))
        );
        BlueprintValidator validator = new BlueprintValidator(new MinecraftBlockStateResolver());
        StructureGenerationPipeline pipeline = new StructureGenerationPipeline(paths, validator, System.out::println);
        StructureGenerationResult result = pipeline.generate();
        result.diagnostics().forEach(StructureGenCli::printDiagnostic);

        System.out.println();
        System.out.println("## StructureGen");
        System.out.println();
        System.out.printf(Locale.ROOT, "Blueprints found: %d%n", result.blueprintsFound());
        System.out.printf(Locale.ROOT, "Validated:        %d%n", result.validated());
        System.out.printf(Locale.ROOT, "Generated:        %d%n", result.generated().size());
        System.out.printf(Locale.ROOT, "Warnings:         %d%n", result.warningCount());
        System.out.printf(Locale.ROOT, "Errors:           %d%n", result.errorCount());
        return result.successful() ? 0 : 1;
    }

    // Inspection writes only build-owned reports and never rewrites the selected NBT input.
    private static int inspect(CliArguments arguments) throws IOException {
        arguments.requireOnly(Set.of("input", "reports", "palette"));
        Path input = Path.of(arguments.require("input")).toAbsolutePath().normalize();
        Path reports = requireReportOwned(Path.of(arguments.require("reports")), "Inspection report directory");
        MinecraftStructureNbtReader reader = new MinecraftStructureNbtReader();
        StructureModel model = reader.read(input);
        StructureInspectionReport report = new StructureInspector().inspect(input, model);
        StructureReportWriter reportWriter = new StructureReportWriter();
        String baseName = safeBaseName(model.name());
        StructureReportWriter.ReportPaths reportPaths = reportWriter.write(report, reports, baseName);

        printInspectionSummary(report);
        System.out.println();
        System.out.println("Text report: " + reportPaths.text());
        System.out.println("JSON report: " + reportPaths.json());
        if (arguments.optional("palette") != null) {
            int paletteIndex = parseNonNegativeInt(arguments.optional("palette"), "--palette");
            System.out.println();
            System.out.print(reportWriter.formatPaletteEntry(report, paletteIndex));
        }
        return 0;
    }

    // Blueprint export is staged and confined to build/reports so the input NBT cannot be an output target.
    private static int export(CliArguments arguments) throws IOException {
        arguments.requireOnly(Set.of("input", "output"));
        Path input = Path.of(arguments.require("input")).toAbsolutePath().normalize();
        Path output = requireReportOwned(Path.of(arguments.require("output")), "Blueprint export path");
        if (input.equals(output)) {
            throw new IllegalArgumentException("Blueprint export may not overwrite its NBT input: " + input);
        }
        StructureModel model = new MinecraftStructureNbtReader().read(input);
        Files.createDirectories(output.getParent());
        Path temporary = Files.createTempFile(output.getParent(), "." + safeBaseName(model.name()) + "-", ".json.tmp");
        boolean published = false;
        try {
            new BlueprintExporter().write(model, temporary);
            SafeFilePublisher.publish(temporary, output);
            published = true;
        } finally {
            if (!published) {
                Files.deleteIfExists(temporary);
            }
        }
        System.out.println("[StructureGen] Exported Blueprint v1 reference: " + output);
        if (!model.unsupportedFields().isEmpty()) {
            System.out.println("[StructureGen] WARNING Export contains " + model.unsupportedFields().size()
                    + " unsupported-field annotations; review them before reuse.");
        }
        return 0;
    }

    private static int compare(CliArguments arguments) throws IOException {
        arguments.requireOnly(Set.of("left", "right"));
        Path left = Path.of(arguments.require("left")).toAbsolutePath().normalize();
        Path right = Path.of(arguments.require("right")).toAbsolutePath().normalize();
        MinecraftStructureNbtReader reader = new MinecraftStructureNbtReader();
        // A common fallback name prevents differing filenames from becoming a false structural difference.
        StructureModel leftModel = reader.read(left, "comparison");
        StructureModel rightModel = reader.read(right, "comparison");
        StructureComparator comparator = new StructureComparator();
        StructureComparisonReport report = comparator.compare(leftModel, rightModel);
        System.out.print(comparator.format(report));
        return report.semanticallyMatches() ? 0 : 2;
    }

    private static void printInspectionSummary(StructureInspectionReport report) {
        System.out.println("## StructureGen Inspector");
        System.out.println();
        System.out.println("File: " + report.file());
        System.out.println("DataVersion: " + report.dataVersion());
        System.out.println("Size: " + report.size().x() + " x " + report.size().y() + " x " + report.size().z());
        System.out.println("Bounding volume: " + report.boundingVolume());
        System.out.println();
        System.out.println("Stored block records: " + report.storedBlocks());
        System.out.println("Explicit air records: " + report.explicitAirBlocks());
        System.out.println("Occupied blocks: " + report.occupiedBlocks());
        System.out.printf(Locale.ROOT, "Occupied density: %.6f%n", report.occupiedDensity());
        System.out.println("Palette entries: " + report.primaryPaletteSize());
        System.out.println("Unique block types: " + report.uniqueBlockTypes());
        System.out.println("Block-state variants: " + report.blockStateVariants());
        System.out.println("Block entities: " + report.blockEntityCount());
        System.out.println("Entities: " + report.entityCount());
        System.out.println();
        System.out.println("Most common blocks:");
        for (int index = 0; index < Math.min(20, report.blockFrequencies().size()); index++) {
            StructureInspectionReport.BlockFrequency frequency = report.blockFrequencies().get(index);
            System.out.println((index + 1) + ". " + frequency.block() + " - " + frequency.count());
        }
        if (!report.unknownOrUnsupportedTags().isEmpty()) {
            System.out.println();
            System.out.println("Unknown or unsupported tags:");
            report.unknownOrUnsupportedTags().forEach(tag -> System.out.println("- " + tag));
        }
    }

    private static Path requireReportOwned(Path candidate, String description) throws IOException {
        Path projectRoot = Path.of("").toAbsolutePath().normalize();
        Path buildRoot = projectRoot.resolve("build").normalize();
        Path reportRoot = buildRoot.resolve("reports/structuregen").normalize();
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!normalized.startsWith(reportRoot)) {
            throw new IllegalArgumentException(description + " must stay beneath " + reportRoot + ": " + normalized);
        }
        rejectExistingSymbolicLinks(buildRoot, normalized, description);
        return normalized;
    }

    private static void rejectExistingSymbolicLinks(
            Path allowedRoot,
            Path target,
            String description
    ) throws IOException {
        Path normalizedRoot = allowedRoot.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedRoot)) {
            throw new IOException(description + " escapes its allowed root: " + normalizedTarget);
        }
        Path current = normalizedRoot;
        if (Files.isSymbolicLink(current)) {
            throw new IOException("Refusing symbolic-link build directory: " + current);
        }
        for (Path segment : normalizedRoot.relativize(normalizedTarget)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Refusing symbolic-link " + description.toLowerCase(Locale.ROOT)
                        + ": " + current);
            }
        }
    }

    private static int parseNonNegativeInt(String value, String option) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new NumberFormatException("negative");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(option + " must be a non-negative integer; got '" + value + "'.");
        }
    }

    private static String safeBaseName(String name) {
        String normalized = name == null ? "structure" : name.replaceAll("[^a-zA-Z0-9_-]", "_");
        return normalized.isBlank() ? "structure" : normalized;
    }

    private static void printDiagnostic(StructureDiagnostic diagnostic) {
        if (diagnostic.severity() == DiagnosticSeverity.ERROR) {
            System.err.println(diagnostic.format());
        } else {
            System.out.println(diagnostic.format());
        }
    }
}
