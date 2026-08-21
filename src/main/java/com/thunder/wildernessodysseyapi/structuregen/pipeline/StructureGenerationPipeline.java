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

    // The generated resource directory is persistent between builds. Inventory
    // every StructureGen-owned structure tree before deleting any obsolete NBT
    // so renamed, nested, or legacy-plural outputs cannot remain packaged.
    private void reconcileObsoleteGeneratedStructures(
            List<GeneratedStructure> generated,
            List<StructureDiagnostic> diagnostics
    ) {
        Set<Path> activeOutputs = new HashSet<>();
        generated.forEach(result -> activeOutputs.add(result.output().toAbsolutePath().normalize()));

        try {
            List<Path> obsoleteOutputs = new ArrayList<>();
            for (Path generatedRoot : paths.generatedStructureRoots()) {
                obsoleteOutputs.addAll(findObsoleteGeneratedStructures(generatedRoot, activeOutputs));
            }

            // Deletion starts only after every owned tree has passed containment,
            // type, and symbolic-link validation.
            for (Path obsoleteOutput : obsoleteOutputs.stream().sorted().toList()) {
                Files.delete(obsoleteOutput);
                Path normalized = obsoleteOutput.toAbsolutePath().normalize();
                logger.accept("[StructureGen] Removed obsolete generated structure: " + normalized);
            }
        } catch (IOException exception) {
            diagnostics.add(new StructureDiagnostic(
                    DiagnosticSeverity.ERROR,
                    paths.outputResourceRoot(),
                    "output cleanup",
                    exception.getMessage()
            ));
        }
    }

    private List<Path> findObsoleteGeneratedStructures(
            Path generatedRoot,
            Set<Path> activeOutputs
    ) throws IOException {
        Path normalizedRoot = generatedRoot.toAbsolutePath().normalize();
        requireSafeOwnedRoot(normalizedRoot);
        if (!Files.exists(normalizedRoot, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        if (!Files.isDirectory(normalizedRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Refusing non-directory generated structure root: " + normalizedRoot);
        }

        List<Path> obsoleteOutputs = new ArrayList<>();
        try (Stream<Path> entries = Files.walk(normalizedRoot)) {
            for (Path candidate : entries.sorted().toList()) {
                Path normalized = candidate.toAbsolutePath().normalize();
                if (!normalized.startsWith(normalizedRoot)) {
                    throw new IOException("Generated structure entry escapes owned root "
                            + normalizedRoot + ": " + normalized);
                }
                if (Files.isSymbolicLink(candidate)) {
                    throw new IOException("Refusing symbolic link in generated structure tree: " + normalized);
                }
                if (Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }

                String fileName = normalized.getFileName().toString();
                if (!fileName.endsWith(".nbt")) {
                    continue;
                }
                if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Refusing non-regular generated NBT: " + normalized);
                }
                if (!activeOutputs.contains(normalized)) {
                    obsoleteOutputs.add(normalized);
                }
            }
        }
        return obsoleteOutputs;
    }

    private void requireSafeOwnedRoot(Path generatedRoot) throws IOException {
        Path buildRoot = paths.buildRoot();
        Path outputRoot = paths.outputResourceRoot().toAbsolutePath().normalize();
        if (!outputRoot.startsWith(buildRoot) || !generatedRoot.startsWith(outputRoot)) {
            throw new IOException("Generated structure root escapes the output resource root: " + generatedRoot);
        }

        // Start at build/ rather than the deeper output root so an ancestor
        // junction or symbolic link cannot redirect cleanup outside the checkout.
        Path current = buildRoot;
        if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
            throw new IOException("Refusing symbolic-link generated output root: " + current);
        }
        for (Path segment : buildRoot.relativize(generatedRoot)) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IOException("Refusing symbolic-link generated structure directory: " + current);
            }
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
