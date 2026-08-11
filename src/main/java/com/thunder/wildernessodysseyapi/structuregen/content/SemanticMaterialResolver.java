package com.thunder.wildernessodysseyapi.structuregen.content;

import com.thunder.wildernessodysseyapi.structuregen.blueprint.BlueprintContentPolicy;
import com.thunder.wildernessodysseyapi.structuregen.blueprint.BlueprintDocument;
import com.thunder.wildernessodysseyapi.structuregen.blueprint.BlueprintMaterialCandidate;
import com.thunder.wildernessodysseyapi.structuregen.blueprint.BlueprintMaterialDefinition;
import com.thunder.wildernessodysseyapi.structuregen.diagnostic.DiagnosticSeverity;
import com.thunder.wildernessodysseyapi.structuregen.diagnostic.StructureDiagnostic;
import com.thunder.wildernessodysseyapi.structuregen.diagnostic.StructureGenResult;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Resolves Blueprint semantic materials against an environment-specific, fail-closed block catalog.
 *
 * <p>Each referenced role is resolved exactly once. Ordered candidates and a stable preferred-mod
 * affinity make selection deterministic without introducing a random seed contract. Explicit
 * candidate affinities gate on installed mod IDs without treating block namespaces as ownership
 * metadata.</p>
 */
public final class SemanticMaterialResolver {

    private static final String MOD_NAMESPACE = "wildernessodysseyapi";
    private static final Pattern ROLE_NAME = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");
    private static final Pattern MOD_ID = Pattern.compile("[a-z][a-z0-9_]{1,63}");

    private final StructureBlockCatalog catalog;

    /** Creates a resolver backed by one verified registry snapshot or live registry view. */
    public SemanticMaterialResolver(StructureBlockCatalog catalog) {
        this.catalog = catalog;
    }

    /** Validates content policy and deterministically resolves every referenced {@code $role}. */
    public StructureGenResult<MaterialResolution> resolve(BlueprintDocument blueprint) {
        List<StructureDiagnostic> diagnostics = new ArrayList<>();
        BlueprintContentPolicy policy = blueprint.contentPolicy();
        validatePolicy(blueprint, policy, diagnostics);
        validateMaterialDefinitions(blueprint, diagnostics);

        Set<String> referencedRoles = new TreeSet<>();
        blueprint.blocks().stream()
                .map(block -> block.blockId())
                .filter(block -> block.startsWith("$"))
                .map(block -> block.substring(1))
                .forEach(referencedRoles::add);

        Map<String, ResolvedMaterial> resolved = new LinkedHashMap<>();
        for (String role : referencedRoles) {
            BlueprintMaterialDefinition definition = blueprint.materials().get(role);
            if (definition == null) {
                error(diagnostics, blueprint.source(), "blocks[*].block",
                        "Semantic material '$" + role + "' has no definition in materials.");
                continue;
            }
            ResolvedMaterial selection = resolveRole(blueprint, role, definition, diagnostics);
            if (selection != null) {
                resolved.put(role, selection);
            }
        }

        StructureContentManifest manifest = new StructureContentManifest(
                policy.allowInstalledModBlocks(),
                policy.preferredDecorativeMods(),
                policy.requiredMods(),
                policy.enabledFunctionalSystems(),
                List.copyOf(resolved.values())
        );
        MaterialResolution value = hasErrors(diagnostics)
                ? null
                : new MaterialResolution(resolved, manifest);
        return new StructureGenResult<>(value, diagnostics);
    }

    private void validatePolicy(
            BlueprintDocument blueprint,
            BlueprintContentPolicy policy,
            List<StructureDiagnostic> diagnostics
    ) {
        validateDistinctModIds(
                policy.preferredDecorativeMods(), "contentPolicy.preferredDecorativeMods",
                blueprint.source(), diagnostics
        );
        validateDistinctModIds(
                policy.requiredMods(), "contentPolicy.requiredMods", blueprint.source(), diagnostics
        );
        validateDistinctResourceIds(
                policy.enabledFunctionalSystems(), "contentPolicy.enabledFunctionalSystems",
                blueprint.source(), diagnostics
        );
        for (int index = 0; index < policy.requiredMods().size(); index++) {
            String modId = policy.requiredMods().get(index);
            if (validNamespace(modId) && !catalog.isModAvailable(modId)) {
                error(diagnostics, blueprint.source(), "contentPolicy.requiredMods[" + index + "]",
                        "Structure '" + blueprint.name() + "' requires mod '" + modId
                                + "', but that mod is not available in the verified content catalog.");
            }
        }
    }

    private void validateMaterialDefinitions(
            BlueprintDocument blueprint,
            List<StructureDiagnostic> diagnostics
    ) {
        blueprint.materials().forEach((role, definition) -> {
            String location = "materials." + role;
            if (!ROLE_NAME.matcher(role).matches()) {
                error(diagnostics, blueprint.source(), location,
                        "Material role names must be 1-64 lowercase resource-style characters.");
            }
            if (!Set.of("decorative", "functional").contains(definition.intent())) {
                error(diagnostics, blueprint.source(), location + ".intent",
                        "Intent must be either 'decorative' or 'functional'.");
            }
            if ("functional".equals(definition.intent())) {
                if (!validResourceId(definition.requiredSystem())) {
                    error(diagnostics, blueprint.source(), location + ".requiredSystem",
                            "Functional materials require an explicit namespaced system ID.");
                }
            } else if (definition.requiredSystem() != null) {
                error(diagnostics, blueprint.source(), location + ".requiredSystem",
                        "A decorative material may not name a functional system.");
            }
            if (definition.preferred().isEmpty() && definition.fallbacks().isEmpty()) {
                error(diagnostics, blueprint.source(), location,
                        "A material must declare at least one preferred candidate or fallback.");
            }
            validateCandidateSyntax(definition.preferred(), location + ".preferred", blueprint, diagnostics);
            validateCandidateSyntax(definition.fallbacks(), location + ".fallbacks", blueprint, diagnostics);
        });
    }

    private void validateCandidateSyntax(
            List<BlueprintMaterialCandidate> candidates,
            String location,
            BlueprintDocument blueprint,
            List<StructureDiagnostic> diagnostics
    ) {
        for (int index = 0; index < candidates.size(); index++) {
            BlueprintMaterialCandidate candidate = candidates.get(index);
            String blockId = candidate.blockId();
            if (!validResourceId(blockId)) {
                error(diagnostics, blueprint.source(), location + "[" + index + "].block",
                        "Invalid explicit block resource ID '" + blockId + "'.");
            }
            if (candidate.requiresMod() != null && !validNamespace(candidate.requiresMod())) {
                error(diagnostics, blueprint.source(), location + "[" + index + "].requiresMod",
                        "Required candidate mod IDs must be valid lowercase resource namespaces.");
            }
        }
    }

    private ResolvedMaterial resolveRole(
            BlueprintDocument blueprint,
            String role,
            BlueprintMaterialDefinition definition,
            List<StructureDiagnostic> diagnostics
    ) {
        String location = "materials." + role;
        if ("functional".equals(definition.intent())
                && !blueprint.contentPolicy().enabledFunctionalSystems().contains(definition.requiredSystem())) {
            error(diagnostics, blueprint.source(), location + ".requiredSystem",
                    "Functional system '" + definition.requiredSystem() + "' was not explicitly enabled in "
                            + "contentPolicy.enabledFunctionalSystems.");
            return null;
        }

        List<BlueprintMaterialCandidate> preferred = "decorative".equals(definition.intent())
                ? orderPreferredCandidates(
                        definition.preferred(), blueprint.contentPolicy().preferredDecorativeMods()
                )
                : definition.preferred();
        List<RejectedMaterialCandidate> rejected = new ArrayList<>();
        CandidateSelection selected = firstValid(preferred, "preferred", blueprint, definition, rejected);

        List<CandidateEvaluation> fallbackEvaluations = evaluateAll(
                definition.fallbacks(), blueprint.contentPolicy(), definition
        );
        boolean fallbackAvailable = fallbackEvaluations.stream().anyMatch(CandidateEvaluation::valid);
        if (selected == null) {
            selected = firstValidEvaluation(fallbackEvaluations, "fallback", rejected);
        } else {
            fallbackEvaluations.stream()
                    .filter(evaluation -> !evaluation.valid())
                    .map(CandidateEvaluation::rejection)
                    .forEach(rejected::add);
        }

        if (selected == null) {
            String reasons = rejected.isEmpty()
                    ? "no candidates were declared"
                    : rejected.stream().map(rejection -> rejection.blockId() + ": " + rejection.reason())
                            .reduce((left, right) -> left + "; " + right).orElse("no valid candidate");
            error(diagnostics, blueprint.source(), location,
                    "Semantic material '$" + role + "' has no valid candidate (" + reasons + ").");
            return null;
        }

        rejected.forEach(rejection -> warning(
                diagnostics,
                blueprint.source(),
                location,
                "Skipped optional candidate '" + rejection.blockId() + "': " + rejection.reason()
        ));
        return new ResolvedMaterial(
                role,
                definition.intent(),
                selected.candidate().blockId(),
                selected.candidate().properties(),
                selected.sourceNamespace(),
                selected.source(),
                fallbackAvailable,
                rejected
        );
    }

    private List<BlueprintMaterialCandidate> orderPreferredCandidates(
            List<BlueprintMaterialCandidate> candidates,
            List<String> preferredMods
    ) {
        List<BlueprintMaterialCandidate> ordered = new ArrayList<>(candidates.size());
        Set<BlueprintMaterialCandidate> added = new LinkedHashSet<>();
        for (String mod : preferredMods) {
            candidates.stream()
                    .filter(candidate -> mod.equals(preferenceAffinity(candidate)))
                    .forEach(candidate -> {
                        if (added.add(candidate)) {
                            ordered.add(candidate);
                        }
                    });
        }
        for (BlueprintMaterialCandidate candidate : candidates) {
            if (added.add(candidate)) {
                ordered.add(candidate);
            }
        }
        return List.copyOf(ordered);
    }

    // Namespace equality is only a legacy ordering hint when that exact mod ID is installed.
    // It is never persisted or reported as evidence that the mod owns the registry namespace.
    private String preferenceAffinity(BlueprintMaterialCandidate candidate) {
        if (candidate.requiresMod() != null) {
            return candidate.requiresMod();
        }
        String candidateNamespace = namespace(candidate.blockId());
        return catalog.isModAvailable(candidateNamespace) ? candidateNamespace : null;
    }

    private CandidateSelection firstValid(
            List<BlueprintMaterialCandidate> candidates,
            String source,
            BlueprintDocument blueprint,
            BlueprintMaterialDefinition definition,
            List<RejectedMaterialCandidate> rejected
    ) {
        for (BlueprintMaterialCandidate candidate : candidates) {
            CandidateEvaluation evaluation = evaluate(candidate, blueprint.contentPolicy(), definition);
            if (evaluation.valid()) {
                return new CandidateSelection(candidate, evaluation.sourceNamespace(), source);
            }
            rejected.add(evaluation.rejection());
        }
        return null;
    }

    private CandidateSelection firstValidEvaluation(
            List<CandidateEvaluation> evaluations,
            String source,
            List<RejectedMaterialCandidate> rejected
    ) {
        for (CandidateEvaluation evaluation : evaluations) {
            if (evaluation.valid()) {
                return new CandidateSelection(evaluation.candidate(), evaluation.sourceNamespace(), source);
            }
            rejected.add(evaluation.rejection());
        }
        return null;
    }

    private List<CandidateEvaluation> evaluateAll(
            List<BlueprintMaterialCandidate> candidates,
            BlueprintContentPolicy policy,
            BlueprintMaterialDefinition definition
    ) {
        return candidates.stream().map(candidate -> evaluate(candidate, policy, definition)).toList();
    }

    private CandidateEvaluation evaluate(
            BlueprintMaterialCandidate candidate,
            BlueprintContentPolicy policy,
            BlueprintMaterialDefinition definition
    ) {
        String candidateNamespace = namespace(candidate.blockId());
        if (candidate.requiresMod() != null) {
            if (!validNamespace(candidate.requiresMod())) {
                return CandidateEvaluation.rejected(candidate, "requiresMod is not a valid mod ID");
            }
            if (!catalog.isModAvailable(candidate.requiresMod())) {
                return CandidateEvaluation.rejected(candidate,
                        "required candidate mod '" + candidate.requiresMod() + "' is not available");
            }
        }
        if (!policy.allowInstalledModBlocks() && isThirdParty(candidateNamespace)) {
            return CandidateEvaluation.rejected(
                    candidate, "third-party content is disabled by allowInstalledModBlocks=false"
            );
        }
        StructureBlockCatalog.Validation validation = catalog.validate(candidate.blockId(), candidate.properties());
        if (!validation.isValid()) {
            return CandidateEvaluation.rejected(candidate, String.join(" ", validation.errors()));
        }
        ResourceLocation selectedId = validation.block().orElseThrow().id();
        var knownSystem = StructureFunctionalBlockPolicy.requiredSystem(selectedId);
        if (knownSystem.isPresent()) {
            if (!"functional".equals(definition.intent())) {
                return CandidateEvaluation.rejected(candidate,
                        "known Wilderness Odyssey gameplay content requires functional intent and system '"
                                + knownSystem.get() + "'");
            }
            if (!knownSystem.get().equals(definition.requiredSystem())) {
                return CandidateEvaluation.rejected(candidate,
                        "known Wilderness Odyssey gameplay content requires system '" + knownSystem.get() + "'");
            }
        }
        String sourceNamespace = selectedId.getNamespace();
        return CandidateEvaluation.valid(candidate, sourceNamespace);
    }

    private void validateDistinctModIds(
            List<String> modIds,
            String location,
            Path source,
            List<StructureDiagnostic> diagnostics
    ) {
        Set<String> unique = new HashSet<>();
        for (int index = 0; index < modIds.size(); index++) {
            String modId = modIds.get(index);
            if (!validNamespace(modId)) {
                error(diagnostics, source, location + "[" + index + "]",
                        "Mod IDs must be valid lowercase resource namespaces.");
            } else if (!unique.add(modId)) {
                error(diagnostics, source, location + "[" + index + "]", "Duplicate mod ID '" + modId + "'.");
            }
        }
    }

    private void validateDistinctResourceIds(
            List<String> ids,
            String location,
            Path source,
            List<StructureDiagnostic> diagnostics
    ) {
        Set<String> unique = new HashSet<>();
        for (int index = 0; index < ids.size(); index++) {
            String id = ids.get(index);
            if (!validResourceId(id)) {
                error(diagnostics, source, location + "[" + index + "]",
                        "Functional system IDs must be explicit lowercase resource locations.");
            } else if (!unique.add(id)) {
                error(diagnostics, source, location + "[" + index + "]", "Duplicate system ID '" + id + "'.");
            }
        }
    }

    private boolean validNamespace(String value) {
        return value != null && MOD_ID.matcher(value).matches();
    }

    private boolean validResourceId(String value) {
        if (value == null || !value.contains(":")) {
            return false;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        return parsed != null && parsed.toString().equals(value);
    }

    private String namespace(String blockId) {
        ResourceLocation parsed = ResourceLocation.tryParse(blockId);
        return parsed == null ? "" : parsed.getNamespace();
    }

    private boolean isThirdParty(String namespace) {
        return !"minecraft".equals(namespace) && !MOD_NAMESPACE.equals(namespace);
    }

    private boolean hasErrors(List<StructureDiagnostic> diagnostics) {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
    }

    private void error(List<StructureDiagnostic> diagnostics, Path source, String location, String message) {
        diagnostics.add(new StructureDiagnostic(DiagnosticSeverity.ERROR, source, location, message));
    }

    private void warning(List<StructureDiagnostic> diagnostics, Path source, String location, String message) {
        diagnostics.add(new StructureDiagnostic(DiagnosticSeverity.WARNING, source, location, message));
    }

    private record CandidateSelection(
            BlueprintMaterialCandidate candidate,
            String sourceNamespace,
            String source
    ) {
    }

    private record CandidateEvaluation(
            BlueprintMaterialCandidate candidate,
            String sourceNamespace,
            RejectedMaterialCandidate rejection
    ) {
        private static CandidateEvaluation valid(BlueprintMaterialCandidate candidate, String sourceNamespace) {
            return new CandidateEvaluation(candidate, sourceNamespace, null);
        }

        private static CandidateEvaluation rejected(BlueprintMaterialCandidate candidate, String reason) {
            return new CandidateEvaluation(
                    candidate, null, new RejectedMaterialCandidate(candidate.blockId(), reason)
            );
        }

        private boolean valid() {
            return rejection == null;
        }
    }
}
