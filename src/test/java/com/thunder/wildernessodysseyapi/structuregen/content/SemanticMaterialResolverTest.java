package com.thunder.wildernessodysseyapi.structuregen.content;

import com.thunder.wildernessodysseyapi.structuregen.StructureGenConstants;
import com.thunder.wildernessodysseyapi.structuregen.blueprint.BlueprintBlock;
import com.thunder.wildernessodysseyapi.structuregen.blueprint.BlueprintContentPolicy;
import com.thunder.wildernessodysseyapi.structuregen.blueprint.BlueprintDocument;
import com.thunder.wildernessodysseyapi.structuregen.blueprint.BlueprintExporter;
import com.thunder.wildernessodysseyapi.structuregen.blueprint.BlueprintMaterialCandidate;
import com.thunder.wildernessodysseyapi.structuregen.blueprint.BlueprintMaterialDefinition;
import com.thunder.wildernessodysseyapi.structuregen.comparison.StructureComparator;
import com.thunder.wildernessodysseyapi.structuregen.diagnostic.DiagnosticSeverity;
import com.thunder.wildernessodysseyapi.structuregen.diagnostic.StructureDiagnostic;
import com.thunder.wildernessodysseyapi.structuregen.diagnostic.StructureGenResult;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlock;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlockState;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureModel;
import com.thunder.wildernessodysseyapi.structuregen.model.StructurePosition;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureSize;
import com.thunder.wildernessodysseyapi.structuregen.nbt.MinecraftStructureNbtReader;
import com.thunder.wildernessodysseyapi.structuregen.nbt.MinecraftStructureNbtWriter;
import com.thunder.wildernessodysseyapi.structuregen.validation.BlueprintValidator;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers deterministic, fail-closed selection of optional mod content for StructureGen.
 *
 * <p>The in-memory catalog deliberately models a completed registry snapshot. These tests do not
 * depend on the development runtime's currently installed mods and therefore prove the policy and
 * resolution rules without accidentally treating the test classpath as an availability signal.</p>
 */
class SemanticMaterialResolverTest {

    private static final Path SOURCE = Path.of("src", "test", "fixtures", "mod_aware.blueprint.json");
    private static final AvailableBlockDescriptor STONE = block("minecraft:stone");
    private static final AvailableBlockDescriptor DECORATIVE_PANEL = block(
            "decorplus:panel",
            Map.of("facing", List.of("north", "south")),
            Map.of("facing", "north")
    );

    @TempDir
    Path tempDirectory;

    @Test
    void installedAndValidThirdPartyCandidateIsSelectedByBlueprintValidation() {
        StructureBlockCatalog catalog = catalog(Set.of("decorplus"), STONE, DECORATIVE_PANEL);
        BlueprintDocument blueprint = blueprint(
                "installed_selection",
                policy(true, List.of("decorplus"), List.of(), List.of()),
                decorative(
                        List.of(candidate("decorplus:panel", Map.of("facing", "south"))),
                        List.of(candidate("minecraft:stone"))
                )
        );

        StructureGenResult<StructureModel> result = new BlueprintValidator(catalog).validate(blueprint);

        assertFalse(result.hasErrors(), () -> diagnostics(result));
        assertNotNull(result.value());
        assertEquals("decorplus:panel", result.value().blocks().getFirst().state().blockId());
        assertEquals(Map.of("facing", "south"), result.value().blocks().getFirst().state().properties());
        assertEquals("decorplus", result.value().contentManifest().resolvedMaterials().getFirst().sourceNamespace());
        assertEquals("preferred", result.value().contentManifest().resolvedMaterials().getFirst().source());
    }

    @Test
    void missingOptionalModUsesTheDeclaredVanillaFallback() {
        StructureBlockCatalog catalog = catalog(Set.of(), STONE);
        BlueprintDocument blueprint = blueprint(
                "missing_mod_fallback",
                BlueprintContentPolicy.defaults(),
                decorative(
                        List.of(candidate("decorplus:panel")),
                        List.of(candidate("minecraft:stone"))
                )
        );

        StructureGenResult<MaterialResolution> result = resolve(catalog, blueprint);
        ResolvedMaterial selected = resolved(result);

        assertEquals("minecraft:stone", selected.selectedBlock());
        assertEquals("fallback", selected.source());
        assertTrue(selected.fallbackAvailable());
        assertTrue(selected.rejectedCandidates().stream().anyMatch(rejected ->
                rejected.blockId().equals("decorplus:panel")
                        && rejected.reason().contains("not available")));
        assertEquals(1, result.warningCount());
    }

    @Test
    void missingRegisteredBlockFallsBackAndMalformedIdsFailClosed() {
        StructureBlockCatalog catalog = catalog(Set.of("decorplus"), STONE);
        BlueprintDocument blueprint = blueprint(
                "missing_block_fallback",
                BlueprintContentPolicy.defaults(),
                decorative(
                        List.of(candidate("decorplus:not_registered")),
                        List.of(candidate("minecraft:stone"))
                )
        );

        ResolvedMaterial selected = resolved(resolve(catalog, blueprint));
        StructureBlockCatalog.Validation malformed = catalog.validate("Decor Plus Panel", Map.of());
        StructureGenResult<MaterialResolution> malformedCandidate = resolve(catalog, blueprint(
                "malformed_candidate",
                BlueprintContentPolicy.defaults(),
                decorative(
                        List.of(candidate("Decor Plus Panel")),
                        List.of(candidate("minecraft:stone"))
                )
        ));

        assertEquals("minecraft:stone", selected.selectedBlock());
        assertTrue(selected.rejectedCandidates().getFirst().reason().contains("not available"));
        assertFalse(malformed.isValid());
        assertTrue(malformed.block().isEmpty());
        assertTrue(malformed.errors().getFirst().contains("Invalid explicit block resource ID"));
        assertTrue(malformedCandidate.hasErrors());
        assertDiagnostic(malformedCandidate, DiagnosticSeverity.ERROR, "Invalid explicit block resource ID");
    }

    @Test
    void invalidModdedStateFallsBackAndErrorsWhenNoValidCandidateRemains() {
        StructureBlockCatalog catalog = catalog(Set.of("decorplus"), STONE, DECORATIVE_PANEL);
        BlueprintMaterialCandidate invalidState = candidate(
                "decorplus:panel", Map.of("facing", "sideways")
        );
        StructureGenResult<MaterialResolution> fallbackResult = resolve(catalog, blueprint(
                "invalid_state_fallback",
                BlueprintContentPolicy.defaults(),
                decorative(List.of(invalidState), List.of(candidate("minecraft:stone")))
        ));
        StructureGenResult<MaterialResolution> errorResult = resolve(catalog, blueprint(
                "invalid_state_error",
                BlueprintContentPolicy.defaults(),
                decorative(List.of(invalidState), List.of())
        ));

        ResolvedMaterial selected = resolved(fallbackResult);
        assertEquals("minecraft:stone", selected.selectedBlock());
        assertTrue(selected.rejectedCandidates().getFirst().reason().contains("Invalid value 'sideways'"));
        assertTrue(errorResult.hasErrors());
        assertNull(errorResult.value());
        assertDiagnostic(errorResult, DiagnosticSeverity.ERROR, "no valid candidate");
    }

    @Test
    void decorativeModBlockNeedsNoFunctionalSystemAuthorization() {
        AvailableBlockDescriptor lamp = block(
                "decorplus:lamp",
                Map.of("lit", List.of("false", "true")),
                Map.of("lit", "false")
        );
        StructureBlockCatalog catalog = catalog(Set.of("decorplus"), STONE, lamp);
        BlueprintDocument blueprint = blueprint(
                "decorative_without_system",
                policy(true, List.of(), List.of(), List.of()),
                decorative(
                        List.of(candidate("decorplus:lamp", Map.of("lit", "true"))),
                        List.of(candidate("minecraft:stone"))
                )
        );

        ResolvedMaterial selected = resolved(resolve(catalog, blueprint));

        assertEquals("decorplus:lamp", selected.selectedBlock());
        assertEquals("decorative", selected.intent());
        assertEquals(Map.of("lit", "true"), selected.properties());
    }

    @Test
    void directThirdPartyLiteralRequiresExplicitUsageIntent() {
        StructureBlockCatalog catalog = catalog(Set.of("decorplus"), STONE, DECORATIVE_PANEL);
        BlueprintDocument blueprint = concreteBlueprint(
                "unclassified_literal",
                BlueprintContentPolicy.defaults(),
                "decorplus:panel",
                Map.of("facing", "north")
        );

        StructureGenResult<StructureModel> result = new BlueprintValidator(catalog).validate(blueprint);

        assertTrue(result.hasErrors());
        assertDiagnostic(result, DiagnosticSeverity.ERROR,
                "direct third-party block requires an explicit decorative or functional usageIntent");
    }

    @Test
    void functionalLiteralRequiresADeclaredAndEnabledSystemToken() {
        AvailableBlockDescriptor shaft = block("create:shaft");
        StructureBlockCatalog catalog = catalog(Set.of("create"), STONE, shaft);
        BlueprintValidator validator = new BlueprintValidator(catalog);

        StructureGenResult<StructureModel> missingSystem = validator.validate(concreteBlueprint(
                "functional_missing_system",
                BlueprintContentPolicy.defaults(),
                "create:shaft",
                Map.of(),
                "functional",
                null,
                null
        ));
        StructureGenResult<StructureModel> disabledSystem = validator.validate(concreteBlueprint(
                "functional_disabled_system",
                BlueprintContentPolicy.defaults(),
                "create:shaft",
                Map.of(),
                "functional",
                "create:kinetics",
                null
        ));

        assertDiagnostic(missingSystem, DiagnosticSeverity.ERROR,
                "requires an explicit namespaced system ID");
        assertDiagnostic(disabledSystem, DiagnosticSeverity.ERROR,
                "was not explicitly enabled");
    }

    @Test
    void functionalLiteralIsAllowedAfterItsExactSystemTokenIsEnabled() {
        AvailableBlockDescriptor shaft = block("create:shaft");
        StructureBlockCatalog catalog = catalog(Set.of("create"), STONE, shaft);
        BlueprintDocument blueprint = concreteBlueprint(
                "functional_enabled",
                policy(true, List.of(), List.of(), List.of("create:kinetics")),
                "create:shaft",
                Map.of(),
                "functional",
                "create:kinetics",
                "{id:'create:shaft'}"
        );

        StructureGenResult<StructureModel> result = new BlueprintValidator(catalog).validate(blueprint);

        assertFalse(result.hasErrors(), () -> diagnostics(result));
        assertNotNull(result.value());
        assertEquals("create:shaft", result.value().blocks().getFirst().state().blockId());
    }

    @Test
    void nonVanillaBlockEntityDataRequiresFunctionalOptInEvenForWildernessBlocks() {
        AvailableBlockDescriptor wildernessMachine = block("wildernessodysseyapi:machine_fixture");
        StructureBlockCatalog catalog = catalog(Set.of(), STONE, wildernessMachine);
        BlueprintDocument blueprint = concreteBlueprint(
                "decorative_block_entity",
                BlueprintContentPolicy.defaults(),
                "wildernessodysseyapi:machine_fixture",
                Map.of(),
                "decorative",
                null,
                "{id:'wildernessodysseyapi:machine_fixture'}"
        );

        StructureGenResult<StructureModel> result = new BlueprintValidator(catalog).validate(blueprint);

        assertDiagnostic(result, DiagnosticSeverity.ERROR,
                "non-vanilla literal carrying blockEntitySnbt must declare functional usageIntent");
    }

    @Test
    void knownWildernessGameplayBlockCannotBeMislabeledAsDecorative() {
        AvailableBlockDescriptor cryoTube = block("wildernessodysseyapi:cryo_tube");
        StructureBlockCatalog catalog = catalog(Set.of(), STONE, cryoTube);
        BlueprintDocument blueprint = concreteBlueprint(
                "decorative_cryo_tube",
                BlueprintContentPolicy.defaults(),
                "wildernessodysseyapi:cryo_tube",
                Map.of(),
                "decorative",
                null,
                null
        );

        StructureGenResult<StructureModel> result = new BlueprintValidator(catalog).validate(blueprint);

        assertDiagnostic(result, DiagnosticSeverity.ERROR,
                "requires functional usageIntent and system 'wildernessodysseyapi:cryo_spawn'");
    }

    @Test
    void knownWildernessGameplayBlockUsesItsExactEnabledSystem() {
        AvailableBlockDescriptor cryoTube = block("wildernessodysseyapi:cryo_tube");
        StructureBlockCatalog catalog = catalog(Set.of(), STONE, cryoTube);
        BlueprintDocument blueprint = concreteBlueprint(
                "functional_cryo_tube",
                policy(true, List.of(), List.of(), List.of("wildernessodysseyapi:cryo_spawn")),
                "wildernessodysseyapi:cryo_tube",
                Map.of(),
                "functional",
                "wildernessodysseyapi:cryo_spawn",
                null
        );

        StructureGenResult<StructureModel> result = new BlueprintValidator(catalog).validate(blueprint);

        assertFalse(result.hasErrors(), () -> diagnostics(result));
        assertNotNull(result.value());
    }

    @Test
    void decorativeSemanticRoleSkipsKnownWildernessGameplayContent() {
        AvailableBlockDescriptor cryoTube = block("wildernessodysseyapi:cryo_tube");
        StructureBlockCatalog catalog = catalog(Set.of(), STONE, cryoTube);
        BlueprintDocument blueprint = blueprint(
                "decorative_cryo_role",
                BlueprintContentPolicy.defaults(),
                decorative(
                        List.of(candidate("wildernessodysseyapi:cryo_tube")),
                        List.of(candidate("minecraft:stone"))
                )
        );

        StructureGenResult<StructureModel> result = new BlueprintValidator(catalog).validate(blueprint);

        assertFalse(result.hasErrors(), () -> diagnostics(result));
        assertEquals("minecraft:stone", result.value().blocks().getFirst().state().blockId());
        assertDiagnostic(result, DiagnosticSeverity.WARNING,
                "requires functional intent and system 'wildernessodysseyapi:cryo_spawn'");
    }

    @Test
    void decorativeSemanticSelectionExportsOnlyManifestBackedLiteralIntent() {
        StructureBlockCatalog catalog = catalog(Set.of("decorplus"), STONE, DECORATIVE_PANEL);
        StructureGenResult<StructureModel> result = new BlueprintValidator(catalog).validate(blueprint(
                "exported_decorative_selection",
                BlueprintContentPolicy.defaults(),
                decorative(
                        List.of(candidate("decorplus:panel", Map.of("facing", "north"))),
                        List.of(candidate("minecraft:stone"))
                )
        ));

        assertFalse(result.hasErrors(), () -> diagnostics(result));
        String usageIntent = new BlueprintExporter().toJson(result.value())
                .getAsJsonArray("blocks")
                .get(0)
                .getAsJsonObject()
                .get("usageIntent")
                .getAsString();
        assertEquals("decorative", usageIntent);

        StructureContentManifest mixedIntentManifest = new StructureContentManifest(
                result.value().contentManifest().allowInstalledModBlocks(),
                result.value().contentManifest().preferredDecorativeMods(),
                result.value().contentManifest().requiredMods(),
                List.of("create:kinetics"),
                result.value().contentManifest().resolvedMaterials()
        );
        StructureModel mixedIntentModel = copyWithManifest(result.value(), mixedIntentManifest);
        assertFalse(new BlueprintExporter().toJson(mixedIntentModel)
                .getAsJsonArray("blocks")
                .get(0)
                .getAsJsonObject()
                .has("usageIntent"));

        StructureModel unclassifiedImport = new StructureModel(
                "unclassified_import",
                new StructureSize(1, 1, 1),
                List.of(new StructureBlock(
                        new StructurePosition(0, 0, 0),
                        new StructureBlockState("decorplus:panel", Map.of("facing", "north")),
                        null,
                        List.of()
                )),
                List.of(),
                StructureGenConstants.MINECRAFT_DATA_VERSION,
                Map.of(),
                List.of(),
                List.of(),
                null,
                List.of()
        );
        assertFalse(new BlueprintExporter().toJson(unclassifiedImport)
                .getAsJsonArray("blocks")
                .get(0)
                .getAsJsonObject()
                .has("usageIntent"));
    }

    @Test
    void functionalMaterialIsNotEnabledUntilItsSystemIsExplicitlyRequested() {
        AvailableBlockDescriptor shaft = block(
                "create:shaft",
                Map.of("axis", List.of("x", "y", "z")),
                Map.of("axis", "y")
        );
        StructureBlockCatalog catalog = catalog(Set.of("create"), STONE, shaft);
        BlueprintMaterialDefinition functional = new BlueprintMaterialDefinition(
                "functional",
                "create:kinetics",
                List.of(candidate("create:shaft", Map.of("axis", "x"))),
                List.of(candidate("minecraft:stone"))
        );

        StructureGenResult<MaterialResolution> implicit = resolve(catalog, blueprint(
                "functional_implicit",
                policy(true, List.of(), List.of(), List.of()),
                functional
        ));
        StructureGenResult<MaterialResolution> explicit = resolve(catalog, blueprint(
                "functional_explicit",
                policy(true, List.of(), List.of(), List.of("create:kinetics")),
                functional
        ));

        assertTrue(implicit.hasErrors());
        assertNull(implicit.value());
        assertDiagnostic(implicit, DiagnosticSeverity.ERROR, "was not explicitly enabled");
        assertEquals("create:shaft", resolved(explicit).selectedBlock());
        assertEquals(List.of("create:kinetics"), explicit.value().manifest().enabledFunctionalSystems());
    }

    @Test
    void allowInstalledModBlocksFalseRestrictsSelectionToVanillaFallback() {
        AvailableBlockDescriptor wildernessBlock = block("wildernessodysseyapi:reinforced_concrete");
        StructureBlockCatalog catalog = catalog(
                Set.of("decorplus"), STONE, wildernessBlock, DECORATIVE_PANEL
        );
        BlueprintDocument blueprint = blueprint(
                "third_party_disabled",
                policy(false, List.of("decorplus"), List.of(), List.of()),
                decorative(
                        List.of(candidate("decorplus:panel", Map.of("facing", "north"))),
                        List.of(candidate("minecraft:stone"))
                )
        );

        ResolvedMaterial selected = resolved(resolve(catalog, blueprint));

        assertEquals("minecraft:stone", selected.selectedBlock());
        assertTrue(selected.rejectedCandidates().getFirst().reason()
                .contains("allowInstalledModBlocks=false"));

        BlueprintValidator validator = new BlueprintValidator(catalog);
        StructureGenResult<StructureModel> explicitThirdParty = validator.validate(concreteBlueprint(
                "explicit_third_party_disabled",
                policy(false, List.of(), List.of(), List.of()),
                "decorplus:panel",
                Map.of("facing", "north")
        ));
        StructureGenResult<StructureModel> vanilla = validator.validate(concreteBlueprint(
                "vanilla_still_allowed",
                policy(false, List.of(), List.of(), List.of()),
                "minecraft:stone",
                Map.of()
        ));
        StructureGenResult<StructureModel> wilderness = validator.validate(concreteBlueprint(
                "wilderness_still_allowed",
                policy(false, List.of(), List.of(), List.of()),
                "wildernessodysseyapi:reinforced_concrete",
                Map.of()
        ));

        assertTrue(explicitThirdParty.hasErrors());
        assertDiagnostic(explicitThirdParty, DiagnosticSeverity.ERROR,
                "is not allowed when contentPolicy.allowInstalledModBlocks is false");
        assertFalse(vanilla.hasErrors(), () -> diagnostics(vanilla));
        assertFalse(wilderness.hasErrors(), () -> diagnostics(wilderness));
    }

    @Test
    void missingRequiredModProducesAClearFailure() {
        StructureBlockCatalog catalog = catalog(Set.of(), STONE);
        BlueprintDocument blueprint = blueprint(
                "required_mod_missing",
                policy(true, List.of(), List.of("decorplus"), List.of()),
                decorative(List.of(), List.of(candidate("minecraft:stone")))
        );

        StructureGenResult<MaterialResolution> result = resolve(catalog, blueprint);

        assertTrue(result.hasErrors());
        assertNull(result.value());
        assertDiagnostic(result, DiagnosticSeverity.ERROR,
                "requires mod 'decorplus', but that mod is not available");
    }

    @Test
    void identicalBlueprintAndEnvironmentResolveDeterministically() {
        StructureBlockCatalog catalog = catalog(Set.of("decorplus"), STONE, DECORATIVE_PANEL);
        BlueprintDocument blueprint = blueprint(
                "deterministic_selection",
                policy(true, List.of("decorplus"), List.of(), List.of()),
                decorative(
                        List.of(
                                candidate("minecraft:stone"),
                                candidate("decorplus:panel", Map.of("facing", "north"))
                        ),
                        List.of(candidate("minecraft:stone"))
                )
        );

        StructureGenResult<MaterialResolution> first = resolve(catalog, blueprint);
        StructureGenResult<MaterialResolution> second = resolve(catalog, blueprint);

        assertFalse(first.hasErrors(), () -> diagnostics(first));
        assertEquals(first, second);
        assertEquals("decorplus:panel", resolved(first).selectedBlock(),
                "The declared preferred-mod order must be stable and take precedence");
    }

    @Test
    void explicitRequiresModOrdersARegistryNamespaceThatDiffersFromTheModId() {
        AvailableBlockDescriptor crossNamespacePanel = block("shareddecor:panel");
        StructureBlockCatalog catalog = catalog(Set.of("decor_provider"), STONE, crossNamespacePanel);
        BlueprintDocument blueprint = blueprint(
                "cross_namespace_preference",
                policy(true, List.of("decor_provider"), List.of(), List.of()),
                decorative(
                        List.of(
                                candidate("minecraft:stone"),
                                candidate("shareddecor:panel", Map.of(), "decor_provider")
                        ),
                        List.of(candidate("minecraft:stone"))
                )
        );

        ResolvedMaterial selected = resolved(resolve(catalog, blueprint));

        assertEquals("shareddecor:panel", selected.selectedBlock());
        assertEquals("shareddecor", selected.sourceNamespace(),
                "Registry namespace provenance must remain separate from the mod-ID affinity");
    }

    @Test
    void unavailableRequiresModRejectsRegisteredCandidateAndUsesFallback() {
        AvailableBlockDescriptor crossNamespacePanel = block("shareddecor:panel");
        StructureBlockCatalog catalog = catalog(Set.of(), STONE, crossNamespacePanel);
        BlueprintDocument blueprint = blueprint(
                "missing_candidate_mod_gate",
                policy(true, List.of("decor_provider"), List.of(), List.of()),
                decorative(
                        List.of(candidate("shareddecor:panel", Map.of(), "decor_provider")),
                        List.of(candidate("minecraft:stone"))
                )
        );

        ResolvedMaterial selected = resolved(resolve(catalog, blueprint));

        assertEquals("minecraft:stone", selected.selectedBlock());
        assertTrue(selected.rejectedCandidates().getFirst().reason()
                .contains("required candidate mod 'decor_provider' is not available"));
    }

    @Test
    void differentInstalledModSetsCanResolveToDifferentValidBlocks() {
        AvailableBlockDescriptor alternatePanel = block("alternatedecor:panel");
        BlueprintDocument blueprint = blueprint(
                "environment_specific_selection",
                policy(true, List.of("decorplus", "alternatedecor"), List.of(), List.of()),
                decorative(
                        List.of(
                                candidate("decorplus:panel", Map.of("facing", "north")),
                                candidate("alternatedecor:panel")
                        ),
                        List.of(candidate("minecraft:stone"))
                )
        );

        StructureGenResult<MaterialResolution> decorPlusResult = resolve(
                catalog(Set.of("decorplus"), STONE, DECORATIVE_PANEL), blueprint
        );
        StructureGenResult<MaterialResolution> alternateResult = resolve(
                catalog(Set.of("alternatedecor"), STONE, alternatePanel), blueprint
        );

        ResolvedMaterial decorPlus = resolved(decorPlusResult);
        ResolvedMaterial alternate = resolved(alternateResult);
        assertEquals("decorplus:panel", decorPlus.selectedBlock());
        assertEquals("alternatedecor:panel", alternate.selectedBlock());
        assertNotEquals(decorPlus.selectedBlock(), alternate.selectedBlock());
        assertFalse(decorPlusResult.hasErrors());
        assertFalse(alternateResult.hasErrors());
    }

    @Test
    void contentManifestSurvivesNbtRoundTripAndSemanticComparison() throws IOException {
        ResolvedMaterial selection = new ResolvedMaterial(
                "detail",
                "decorative",
                "decorplus:panel",
                Map.of("facing", "north"),
                "decorplus",
                "preferred",
                true,
                List.of(new RejectedMaterialCandidate("missingdecor:panel", "supplying mod is not installed"))
        );
        StructureContentManifest manifest = new StructureContentManifest(
                true,
                List.of("decorplus"),
                List.of("decorplus"),
                List.of(),
                List.of(selection)
        );
        StructureModel expected = new StructureModel(
                "manifest_fixture",
                new StructureSize(1, 1, 1),
                List.of(new StructureBlock(
                        new StructurePosition(0, 0, 0),
                        new StructureBlockState("decorplus:panel", Map.of("facing", "north")),
                        null,
                        List.of()
                )),
                List.of(),
                StructureGenConstants.MINECRAFT_DATA_VERSION,
                Map.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                manifest
        );
        Path output = tempDirectory.resolve("manifest_fixture.nbt");

        new MinecraftStructureNbtWriter().writeCompressed(expected, output);
        StructureModel reread = new MinecraftStructureNbtReader().read(output, expected.name());

        assertEquals(manifest, reread.contentManifest());
        assertTrue(new StructureComparator().compare(expected, reread).semanticallyMatches());
    }

    private StructureModel copyWithManifest(StructureModel source, StructureContentManifest manifest) {
        return new StructureModel(
                source.name(),
                source.size(),
                source.blocks(),
                source.entities(),
                source.dataVersion(),
                source.metadata(),
                source.markers(),
                source.sourcePalettes(),
                source.rawRootSnbt(),
                source.unsupportedFields(),
                manifest
        );
    }

    private StructureGenResult<MaterialResolution> resolve(
            StructureBlockCatalog catalog,
            BlueprintDocument blueprint
    ) {
        return new SemanticMaterialResolver(catalog).resolve(blueprint);
    }

    private ResolvedMaterial resolved(StructureGenResult<MaterialResolution> result) {
        assertFalse(result.hasErrors(), () -> diagnostics(result));
        assertNotNull(result.value());
        ResolvedMaterial material = result.value().materials().get("detail");
        assertNotNull(material, "Expected the referenced $detail role to resolve");
        return material;
    }

    private BlueprintDocument blueprint(
            String name,
            BlueprintContentPolicy policy,
            BlueprintMaterialDefinition material
    ) {
        return new BlueprintDocument(
                SOURCE,
                StructureGenConstants.BLUEPRINT_FORMAT_VERSION,
                name,
                new StructureSize(1, 1, 1),
                StructureGenConstants.MINECRAFT_DATA_VERSION,
                Map.of("fixture", "mod_aware"),
                List.of("test"),
                List.of(new BlueprintBlock(
                        new StructurePosition(0, 0, 0),
                        "$detail",
                        Map.of(),
                        null,
                        List.of(),
                        null
                )),
                List.of(),
                null,
                policy,
                Map.of("detail", material)
        );
    }

    private BlueprintDocument concreteBlueprint(
            String name,
            BlueprintContentPolicy policy,
            String blockId,
            Map<String, String> properties
    ) {
        return concreteBlueprint(name, policy, blockId, properties, null, null, null);
    }

    private BlueprintDocument concreteBlueprint(
            String name,
            BlueprintContentPolicy policy,
            String blockId,
            Map<String, String> properties,
            String usageIntent,
            String requiredSystem,
            String blockEntitySnbt
    ) {
        return new BlueprintDocument(
                SOURCE,
                StructureGenConstants.BLUEPRINT_FORMAT_VERSION,
                name,
                new StructureSize(1, 1, 1),
                StructureGenConstants.MINECRAFT_DATA_VERSION,
                Map.of(),
                List.of(),
                List.of(new BlueprintBlock(
                        new StructurePosition(0, 0, 0),
                        blockId,
                        properties,
                        blockEntitySnbt,
                        List.of(),
                        null,
                        usageIntent,
                        requiredSystem
                )),
                List.of(),
                null,
                policy,
                Map.of()
        );
    }

    private BlueprintContentPolicy policy(
            boolean allowInstalledModBlocks,
            List<String> preferredMods,
            List<String> requiredMods,
            List<String> enabledSystems
    ) {
        return new BlueprintContentPolicy(
                allowInstalledModBlocks, preferredMods, requiredMods, enabledSystems
        );
    }

    private BlueprintMaterialDefinition decorative(
            List<BlueprintMaterialCandidate> preferred,
            List<BlueprintMaterialCandidate> fallbacks
    ) {
        return new BlueprintMaterialDefinition("decorative", null, preferred, fallbacks);
    }

    private BlueprintMaterialCandidate candidate(String blockId) {
        return candidate(blockId, Map.of());
    }

    private BlueprintMaterialCandidate candidate(String blockId, Map<String, String> properties) {
        return new BlueprintMaterialCandidate(blockId, properties);
    }

    private BlueprintMaterialCandidate candidate(
            String blockId,
            Map<String, String> properties,
            String requiresMod
    ) {
        return new BlueprintMaterialCandidate(blockId, properties, requiresMod);
    }

    private static AvailableBlockDescriptor block(String blockId) {
        return block(blockId, Map.of(), Map.of());
    }

    private static AvailableBlockDescriptor block(
            String blockId,
            Map<String, List<String>> properties,
            Map<String, String> defaults
    ) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null) {
            throw new IllegalArgumentException("Invalid fixture block ID " + blockId);
        }
        return new AvailableBlockDescriptor(id, properties, defaults);
    }

    private StructureBlockCatalog catalog(
            Set<String> thirdPartyMods,
            AvailableBlockDescriptor... blocks
    ) {
        return new InMemoryStructureBlockCatalog(thirdPartyMods, List.of(blocks));
    }

    private void assertDiagnostic(
            StructureGenResult<?> result,
            DiagnosticSeverity severity,
            String messageFragment
    ) {
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.severity() == severity && diagnostic.message().contains(messageFragment)),
                () -> "Missing " + severity + " diagnostic containing '" + messageFragment
                        + "': " + diagnostics(result));
    }

    private String diagnostics(StructureGenResult<?> result) {
        return result.diagnostics().stream().map(StructureDiagnostic::format).toList().toString();
    }

    /** Deterministic registry-snapshot stand-in used to keep mod availability explicit per test. */
    private static final class InMemoryStructureBlockCatalog implements StructureBlockCatalog {

        private final Map<String, String> installedMods;
        private final Map<ResourceLocation, AvailableBlockDescriptor> blocks;

        private InMemoryStructureBlockCatalog(
                Set<String> thirdPartyMods,
                List<AvailableBlockDescriptor> descriptors
        ) {
            Map<String, String> versions = new TreeMap<>();
            versions.put("minecraft", "1.21.1");
            thirdPartyMods.forEach(mod -> versions.put(mod, "test"));
            installedMods = Collections.unmodifiableMap(versions);

            Map<ResourceLocation, AvailableBlockDescriptor> indexed = new LinkedHashMap<>();
            descriptors.forEach(descriptor -> indexed.put(descriptor.id(), descriptor));
            blocks = Collections.unmodifiableMap(indexed);
        }

        @Override
        public Map<String, String> installedMods() {
            return installedMods;
        }

        @Override
        public Map<ResourceLocation, AvailableBlockDescriptor> blocks() {
            return blocks;
        }
    }
}
