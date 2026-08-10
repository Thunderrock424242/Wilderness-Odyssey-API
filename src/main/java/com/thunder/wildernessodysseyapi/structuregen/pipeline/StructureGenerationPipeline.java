package com.thunder.wildernessodysseyapi.structuregen.pipeline;

import com.thunder.wildernessodysseyapi.structuregen.blueprint.BlueprintDocument;
import com.thunder.wildernessodysseyapi.structuregen.blueprint.BlueprintParser;
import com.thunder.wildernessodysseyapi.structuregen.diagnostic.DiagnosticSeverity;
import com.thunder.wildernessodysseyapi.structuregen.diagnostic.StructureDiagnostic;
import com.thunder.wildernessodysseyapi.structuregen.diagnostic.StructureGenResult;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureModel;
import com.thunder.wildernessodysseyapi.structuregen.validation.BlueprintValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Two-phase generation pipeline: every blueprint validates and every target preflights
 * before the first generated output is published.
 */
public final class StructureGenerationPipeline {

    private final StructureGenPaths paths;
    private final BlueprintParser parser;
    private final BlueprintValidator validator;
    private final SafeStructureOutput output;
    private final Consumer<String> logger;

    /** Creates the production generation pipeline. */
    public StructureGenerationPipeline(
            StructureGenPaths paths,
            BlueprintValidator validator,
            Consumer<String> logger
    ) {
        this(paths, new BlueprintParser(), validator, new SafeStructureOutput(paths), logger);
    }

    StructureGenerationPipeline(
            StructureGenPaths paths,
            BlueprintParser parser,
            BlueprintValidator validator,
            SafeStructureOutput output,
            Consumer<String> logger
    ) {
        this.paths = paths;
        this.parser = parser;
        this.validator = validator;
        this.output = output;
        this.logger = logger;
    }

    /** Discovers, validates, compiles, re-reads, and publishes every Blueprint v1 file. */
    public StructureGenerationResult generate() throws IOException {
        List<Path> blueprintFiles = discoverBlueprints();
        List<StructureDiagnostic> diagnostics = new ArrayList<>();
        List<ValidatedBlueprint> validated = new ArrayList<>();

        if (blueprintFiles.isEmpty()) {
            diagnostics.add(new StructureDiagnostic(
                    DiagnosticSeverity.ERROR,
                    paths.blueprintRoot(),
                    "blueprints",
                    "No .json blueprint files were found."
            ));
            return new StructureGenerationResult(0, 0, List.of(), diagnostics);
        }

        // Parse and validate the complete batch before any compiler write occurs.
        for (Path blueprintFile : blueprintFiles) {
            logger.accept("[StructureGen] Reading blueprint: " + blueprintFile.getFileName());
            StructureGenResult<BlueprintDocument> parsed = parser.parse(blueprintFile);
            diagnostics.addAll(parsed.diagnostics());
            if (parsed.hasErrors()) {
                continue;
            }
            logger.accept("[StructureGen] Validating structure: " + parsed.value().name());
            StructureGenResult<StructureModel> validation = validator.validate(parsed.value());
            diagnostics.addAll(validation.diagnostics());
            if (!validation.hasErrors()) {
                validated.add(new ValidatedBlueprint(blueprintFile, validation.value()));
                logger.accept("[StructureGen] Validation passed: " + validation.value().name());
            }
        }
        validateUniqueNames(validated, diagnostics);
        if (hasErrors(diagnostics)) {
            return new StructureGenerationResult(blueprintFiles.size(), validated.size(), List.of(), diagnostics);
        }

        // Preflight every target so one manual-resource collision aborts the batch before publication.
        for (ValidatedBlueprint blueprint : validated) {
            try {
                output.preflight(blueprint.model());
            } catch (IOException | IllegalArgumentException exception) {
                diagnostics.add(new StructureDiagnostic(
                        DiagnosticSeverity.ERROR,
                        blueprint.source(),
                        "output",
                        exception.getMessage()
                ));
            }
        }
        if (hasErrors(diagnostics)) {
            return new StructureGenerationResult(blueprintFiles.size(), validated.size(), List.of(), diagnostics);
        }

        List<GeneratedStructure> generated = new ArrayList<>();
        for (ValidatedBlueprint blueprint : validated) {
            logger.accept("[StructureGen] Compiling Minecraft structure: " + blueprint.model().name());
            try {
                GeneratedStructure result = output.writeVerified(blueprint.model());
                generated.add(result);
                logger.accept("[StructureGen] Verification passed: " + result.output());
            } catch (IOException | IllegalArgumentException exception) {
                diagnostics.add(new StructureDiagnostic(
                        DiagnosticSeverity.ERROR,
                        blueprint.source(),
                        "compiler",
                        exception.getMessage()
                ));
                // Stop after a compiler failure; later outputs are not attempted.
                break;
            }
        }
        if (!hasErrors(diagnostics) && generated.size() == validated.size()) {
            reconcileObsoleteGeneratedStructures(generated, diagnostics);
        }
        return new StructureGenerationResult(blueprintFiles.size(), validated.size(), generated, diagnostics);
    }

    // The generated resource directory is persistent between builds. Remove
    // only safe-name NBT files no longer owned by a current, successful blueprint
    // so renamed/deleted blueprints cannot remain packaged or shadow manual data.
    private void reconcileObsoleteGeneratedStructures(
            List<GeneratedStructure> generated,
            List<StructureDiagnostic> diagnostics
    ) {
        Path generatedRoot = paths.generatedStructureRoot();
        if (!Files.isDirectory(generatedRoot, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Set<Path> activeOutputs = new HashSet<>();
        generated.forEach(result -> activeOutputs.add(result.output().toAbsolutePath().normalize()));

        try (Stream<Path> entries = Files.list(generatedRoot)) {
            for (Path candidate : entries.sorted().toList()) {
                Path normalized = candidate.toAbsolutePath().normalize();
                String fileName = normalized.getFileName().toString();
                if (!fileName.endsWith(".nbt") || activeOutputs.contains(normalized)) {
                    continue;
                }
                String structureName = fileName.substring(0, fileName.length() - ".nbt".length());
                Path ownedTarget;
                try {
                    ownedTarget = paths.generatedStructure(structureName);
                } catch (IllegalArgumentException exception) {
                    throw new IOException("Refusing to reconcile unsafe generated NBT name: " + normalized, exception);
                }
                if (!normalized.equals(ownedTarget)
                        || Files.isSymbolicLink(normalized)
                        || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Refusing to reconcile non-regular generated NBT: " + normalized);
                }
                Files.delete(normalized);
                logger.accept("[StructureGen] Removed obsolete generated structure: " + normalized);
            }
        } catch (IOException exception) {
            diagnostics.add(new StructureDiagnostic(
                    DiagnosticSeverity.ERROR,
                    generatedRoot,
                    "output cleanup",
                    exception.getMessage()
            ));
        }
    }

    private List<Path> discoverBlueprints() throws IOException {
        if (!Files.isDirectory(paths.blueprintRoot())) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(paths.blueprintRoot())) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> paths.blueprintRoot().relativize(path).toString()))
                    .toList();
        }
    }

    private boolean hasErrors(List<StructureDiagnostic> diagnostics) {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
    }

    // Distinct source files may not silently compete for the same generated resource.
    private void validateUniqueNames(
            List<ValidatedBlueprint> validated,
            List<StructureDiagnostic> diagnostics
    ) {
        Map<String, Path> firstSourceByName = new LinkedHashMap<>();
        for (ValidatedBlueprint blueprint : validated) {
            Path firstSource = firstSourceByName.putIfAbsent(blueprint.model().name(), blueprint.source());
            if (firstSource != null) {
                diagnostics.add(new StructureDiagnostic(
                        DiagnosticSeverity.ERROR,
                        blueprint.source(),
                        "name",
                        "Duplicate generated structure name '" + blueprint.model().name()
                                + "' is already owned by " + firstSource + "."
                ));
            }
        }
    }

    private record ValidatedBlueprint(Path source, StructureModel model) {
    }
}
