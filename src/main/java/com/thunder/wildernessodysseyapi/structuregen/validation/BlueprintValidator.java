package com.thunder.wildernessodysseyapi.structuregen.validation;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.thunder.wildernessodysseyapi.structuregen.StructureGenConstants;
import com.thunder.wildernessodysseyapi.structuregen.blueprint.BlueprintBlock;
import com.thunder.wildernessodysseyapi.structuregen.blueprint.BlueprintDocument;
import com.thunder.wildernessodysseyapi.structuregen.blueprint.BlueprintEntity;
import com.thunder.wildernessodysseyapi.structuregen.content.MaterialResolution;
import com.thunder.wildernessodysseyapi.structuregen.content.ResolvedMaterial;
import com.thunder.wildernessodysseyapi.structuregen.content.SemanticMaterialResolver;
import com.thunder.wildernessodysseyapi.structuregen.content.StructureBlockCatalog;
import com.thunder.wildernessodysseyapi.structuregen.content.StructureFunctionalBlockPolicy;
import com.thunder.wildernessodysseyapi.structuregen.content.StructureContentManifest;
import com.thunder.wildernessodysseyapi.structuregen.diagnostic.DiagnosticSeverity;
import com.thunder.wildernessodysseyapi.structuregen.diagnostic.StructureDiagnostic;
import com.thunder.wildernessodysseyapi.structuregen.diagnostic.StructureGenResult;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlock;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlockState;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureEntity;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureModel;
import com.thunder.wildernessodysseyapi.structuregen.model.StructurePosition;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureSize;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Converts an untrusted parsed blueprint into the canonical model only after all checks pass.
 *
 * <p>No compiler or file-writing API is reachable through a failed result, making validation
 * the required authorization boundary for generated NBT.</p>
 */
public final class BlueprintValidator {

    private static final Pattern SAFE_NAME = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Pattern SAFE_MARKER = Pattern.compile("[a-z0-9_.:-]{1,128}");
    private static final Set<String> ROOT_MODEL_FIELDS = Set.of(
            "size", "blocks", "palette", "palettes", "entities", "DataVersion"
    );
    private static final Set<String> BLOCK_ENTRY_FIELDS = Set.of("pos", "state", "nbt");
    private static final Set<String> ENTITY_ENTRY_FIELDS = Set.of("pos", "blockPos", "nbt");

    private final BlockStateResolver blockStateResolver;
    private final StructureBlockCatalog contentCatalog;

    /** Creates a validator with the supplied registry boundary. */
    public BlueprintValidator(BlockStateResolver blockStateResolver) {
        this.blockStateResolver = blockStateResolver;
        this.contentCatalog = null;
    }

    /** Creates a validator with a fail-closed, mod-aware content catalog. */
    public BlueprintValidator(StructureBlockCatalog contentCatalog) {
        this.contentCatalog = contentCatalog;
        this.blockStateResolver = (blockId, properties) -> {
            StructureBlockCatalog.Validation validation = contentCatalog.validate(blockId, properties);
            return new BlockStateResolver.Resolution(validation.errors(), List.of());
        };
    }

    /** Validates every field and produces an internal model only when no errors remain. */
    public StructureGenResult<StructureModel> validate(BlueprintDocument blueprint) {
        List<StructureDiagnostic> diagnostics = new ArrayList<>();
        Path source = blueprint.source();

        validateVersion(blueprint, diagnostics);
        validateDataVersion(blueprint, diagnostics);
        validateName(blueprint.name(), source, diagnostics);
        validateSize(blueprint.size(), source, diagnostics);
        validateMarkers(blueprint.markers(), source, "markers", diagnostics);

        MaterialResolution materialResolution = resolveMaterials(blueprint, diagnostics);
        Map<String, ResolvedMaterial> resolvedMaterials = materialResolution == null
                ? Map.of()
                : materialResolution.materials();

        List<StructureBlock> blocks = validateBlocks(blueprint, resolvedMaterials, diagnostics);
        List<StructureEntity> entities = validateEntities(blueprint, diagnostics);
        validateRawRootSnbt(blueprint.rawRootSnbt(), source, diagnostics);

        if (hasErrors(diagnostics)) {
            return new StructureGenResult<>(null, diagnostics);
        }
        int dataVersion = blueprint.dataVersion() == null
                ? StructureGenConstants.MINECRAFT_DATA_VERSION
                : blueprint.dataVersion();
        StructureModel model = new StructureModel(
                blueprint.name(),
                blueprint.size(),
                blocks,
                entities,
                dataVersion,
                blueprint.metadata(),
                blueprint.markers(),
                List.of(),
                blueprint.rawRootSnbt(),
                List.of(),
                materialResolution == null
                        ? manifestWithoutSelections(blueprint)
                        : materialResolution.manifest()
        );
        return new StructureGenResult<>(model, diagnostics);
    }

    private void validateVersion(BlueprintDocument blueprint, List<StructureDiagnostic> diagnostics) {
        if (blueprint.formatVersion() != StructureGenConstants.BLUEPRINT_FORMAT_VERSION) {
            error(diagnostics, blueprint.source(), "formatVersion",
                    "Unsupported Blueprint formatVersion " + blueprint.formatVersion()
                            + "; supported version is " + StructureGenConstants.BLUEPRINT_FORMAT_VERSION + ".");
        }
    }

    private void validateDataVersion(BlueprintDocument blueprint, List<StructureDiagnostic> diagnostics) {
        if (blueprint.dataVersion() != null && blueprint.dataVersion() < 0) {
            error(diagnostics, blueprint.source(), "dataVersion",
                    "DataVersion must be zero or greater when explicitly supplied; got "
                            + blueprint.dataVersion() + ".");
        }
    }

    private void validateName(String name, Path source, List<StructureDiagnostic> diagnostics) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            error(diagnostics, source, "name",
                    "Must be a simple lowercase name using only letters, digits, '_' or '-' (maximum 64 characters).");
            return;
        }
        if (StructureGenConstants.PROTECTED_BUNKER_NAME.equals(name)) {
            error(diagnostics, source, "name",
                    "'bunker' is reserved for the read-only hand-authored reference structure.");
        }
    }

    private void validateSize(StructureSize size, Path source, List<StructureDiagnostic> diagnostics) {
        if (size == null) {
            return;
        }
        if (size.x() <= 0 || size.y() <= 0 || size.z() <= 0) {
            error(diagnostics, source, "size", "All structure dimensions must be greater than zero; got " + size.display() + ".");
            return;
        }
        if (size.x() > StructureGenConstants.MAX_DIMENSION
                || size.y() > StructureGenConstants.MAX_DIMENSION
                || size.z() > StructureGenConstants.MAX_DIMENSION) {
            error(diagnostics, source, "size", "Each authored dimension must be at most "
                    + StructureGenConstants.MAX_DIMENSION + "; got " + size.display() + ".");
        }
        if (size.volume() > StructureGenConstants.MAX_VOLUME) {
            error(diagnostics, source, "size", "Bounding volume " + size.volume() + " exceeds the authored limit of "
                    + StructureGenConstants.MAX_VOLUME + ".");
        }
    }

    private List<StructureBlock> validateBlocks(
            BlueprintDocument blueprint,
            Map<String, ResolvedMaterial> resolvedMaterials,
            List<StructureDiagnostic> diagnostics
    ) {
        List<StructureBlock> blocks = new ArrayList<>();
        if (blueprint.blocks().isEmpty()) {
            error(diagnostics, blueprint.source(), "blocks", "Structure must contain at least one explicitly stored block.");
            return blocks;
        }
        if (blueprint.blocks().size() > StructureGenConstants.MAX_BLOCKS) {
            error(diagnostics, blueprint.source(), "blocks", "Block count " + blueprint.blocks().size()
                    + " exceeds the authored limit of " + StructureGenConstants.MAX_BLOCKS + ".");
        }

        Set<StructurePosition> positions = new HashSet<>();
        for (int index = 0; index < blueprint.blocks().size(); index++) {
            BlueprintBlock block = blueprint.blocks().get(index);
            String location = "blocks[" + index + "]";
            if (!positions.add(block.position())) {
                error(diagnostics, blueprint.source(), location + ".pos",
                        "Duplicate block position " + block.position().display() + ".");
            }
            if (blueprint.size() != null && !blueprint.size().contains(block.position())) {
                error(diagnostics, blueprint.source(), location + ".pos",
                        "Block at " + block.position().display() + " lies outside structure bounds "
                                + blueprint.size().display() + ".");
            }
            StructureBlockState resolvedState = resolveBlockState(
                    block, blueprint, resolvedMaterials, location, diagnostics
            );
            validateSnbt(block.blockEntitySnbt(), blueprint.source(), location + ".blockEntitySnbt", diagnostics);
            validateRawEntrySnbt(
                    block.rawEntrySnbt(), BLOCK_ENTRY_FIELDS, blueprint.source(),
                    location + ".rawEntrySnbt", diagnostics
            );
            validateMarkers(block.markers(), blueprint.source(), location + ".markers", diagnostics);
            blocks.add(new StructureBlock(
                    block.position(),
                    resolvedState,
                    block.blockEntitySnbt(),
                    block.markers(),
                    block.rawEntrySnbt(),
                    -1
            ));
        }
        return blocks;
    }

    private StructureBlockState resolveBlockState(
            BlueprintBlock block,
            BlueprintDocument blueprint,
            Map<String, ResolvedMaterial> resolvedMaterials,
            String location,
            List<StructureDiagnostic> diagnostics
    ) {
        String blockId = block.blockId();
        Path source = blueprint.source();
        if (blockId.startsWith("$")) {
            String role = blockId.substring(1);
            ResolvedMaterial resolved = resolvedMaterials.get(role);
            if (block.usageIntent() != null) {
                error(diagnostics, source, location + ".usageIntent",
                        "A semantic material reference inherits intent from its material definition.");
            }
            if (block.requiredSystem() != null) {
                error(diagnostics, source, location + ".requiredSystem",
                        "A semantic material reference inherits its required system from its material definition.");
            }
            if (!block.properties().isEmpty()) {
                error(diagnostics, source, location + ".properties",
                        "A semantic material reference uses the complete candidate state and may not add properties.");
            }
            if (block.blockEntitySnbt() != null) {
                error(diagnostics, source, location + ".blockEntitySnbt",
                        "A semantic material reference may not attach block-entity data; use an explicit block when requested.");
            }
            if (resolved == null) {
                error(diagnostics, source, location + ".block",
                        "Semantic material '" + blockId + "' did not resolve to a valid catalog state.");
                return new StructureBlockState("minecraft:air", Map.of());
            }
            return new StructureBlockState(resolved.selectedBlock(), resolved.properties());
        }

        ResourceLocation parsed = ResourceLocation.tryParse(blockId);
        if (parsed == null || !blockId.contains(":") || !parsed.toString().equals(blockId)) {
            error(diagnostics, source, location + ".block",
                    "Invalid Minecraft resource location '" + blockId + "'; use an explicit lowercase namespace such as minecraft:stone.");
            return new StructureBlockState("minecraft:air", Map.of());
        }
        if (!blueprint.contentPolicy().allowInstalledModBlocks()
                && !Set.of("minecraft", "wildernessodysseyapi").contains(parsed.getNamespace())) {
            error(diagnostics, source, location + ".block",
                    "Third-party block '" + blockId
                            + "' is not allowed when contentPolicy.allowInstalledModBlocks is false.");
        }
        validateLiteralUsage(block, blueprint, parsed, location, diagnostics);
        BlockStateResolver.Resolution resolution = blockStateResolver.validate(blockId, block.properties());
        resolution.errors().forEach(message -> error(diagnostics, source, location + ".properties", message));
        resolution.warnings().forEach(message -> warning(diagnostics, source, location + ".properties", message));
        return new StructureBlockState(blockId, block.properties());
    }

    /**
     * Enforces the literal-block opt-in boundary without guessing which installed blocks are safe.
     *
     * <p>Vanilla literals retain their original Blueprint v1 compatibility. Third-party literals
     * must classify their use, and every functional literal must name a system that the Blueprint
     * explicitly enabled. Known Wilderness Odyssey gameplay blocks additionally use the maintained
     * first-party policy so they cannot be mislabeled as decorative.</p>
     */
    private void validateLiteralUsage(
            BlueprintBlock block,
            BlueprintDocument blueprint,
            ResourceLocation blockId,
            String location,
            List<StructureDiagnostic> diagnostics
    ) {
        String intent = block.usageIntent();
        StructureBlockCatalog.ContentFamily family = contentCatalog == null
                ? familyFromNamespace(blockId.getNamespace())
                : contentCatalog.family(blockId);

        if (intent != null && !Set.of("decorative", "functional").contains(intent)) {
            error(diagnostics, blueprint.source(), location + ".usageIntent",
                    "Literal block usageIntent must be either 'decorative' or 'functional'.");
        }
        if (family == StructureBlockCatalog.ContentFamily.THIRD_PARTY && intent == null) {
            error(diagnostics, blueprint.source(), location + ".usageIntent",
                    "A direct third-party block requires an explicit decorative or functional usageIntent; "
                            + "prefer a semantic material role for automatic mod selection.");
        }

        StructureFunctionalBlockPolicy.requiredSystem(blockId).ifPresent(requiredSystem -> {
            if (!"functional".equals(intent)) {
                error(diagnostics, blueprint.source(), location + ".usageIntent",
                        "Wilderness Odyssey gameplay block '" + blockId
                                + "' requires functional usageIntent and system '" + requiredSystem + "'.");
            } else if (!requiredSystem.equals(block.requiredSystem())) {
                error(diagnostics, blueprint.source(), location + ".requiredSystem",
                        "Wilderness Odyssey gameplay block '" + blockId
                                + "' requires system '" + requiredSystem + "'.");
            }
        });

        if ("functional".equals(intent)) {
            if (!validResourceId(block.requiredSystem())) {
                error(diagnostics, blueprint.source(), location + ".requiredSystem",
                        "A functional literal block requires an explicit namespaced system ID.");
            } else if (!blueprint.contentPolicy().enabledFunctionalSystems().contains(block.requiredSystem())) {
                error(diagnostics, blueprint.source(), location + ".requiredSystem",
                        "Functional system '" + block.requiredSystem() + "' was not explicitly enabled in "
                                + "contentPolicy.enabledFunctionalSystems.");
            }
        } else if (block.requiredSystem() != null) {
            error(diagnostics, blueprint.source(), location + ".requiredSystem",
                    "Only a functional literal block may declare requiredSystem.");
        }

        if (block.blockEntitySnbt() != null
                && family != StructureBlockCatalog.ContentFamily.VANILLA
                && !"functional".equals(intent)) {
            error(diagnostics, blueprint.source(), location + ".usageIntent",
                    "A non-vanilla literal carrying blockEntitySnbt must declare functional usageIntent "
                            + "and an explicitly enabled requiredSystem.");
        }
    }

    private StructureBlockCatalog.ContentFamily familyFromNamespace(String namespace) {
        if ("minecraft".equals(namespace)) {
            return StructureBlockCatalog.ContentFamily.VANILLA;
        }
        if ("wildernessodysseyapi".equals(namespace)) {
            return StructureBlockCatalog.ContentFamily.WILDERNESS_ODYSSEY;
        }
        return StructureBlockCatalog.ContentFamily.THIRD_PARTY;
    }

    private boolean validResourceId(String value) {
        if (value == null || !value.contains(":")) {
            return false;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        return parsed != null && parsed.toString().equals(value);
    }

    private MaterialResolution resolveMaterials(
            BlueprintDocument blueprint,
            List<StructureDiagnostic> diagnostics
    ) {
        boolean requiresCatalog = !blueprint.materials().isEmpty()
                || !blueprint.contentPolicy().requiredMods().isEmpty()
                || !blueprint.contentPolicy().preferredDecorativeMods().isEmpty()
                || !blueprint.contentPolicy().enabledFunctionalSystems().isEmpty();
        if (contentCatalog == null) {
            if (requiresCatalog) {
                error(diagnostics, blueprint.source(), "contentPolicy",
                        "This Blueprint uses mod-aware content fields, but no verified StructureBlockCatalog was supplied.");
            }
            return null;
        }
        StructureGenResult<MaterialResolution> resolution = new SemanticMaterialResolver(contentCatalog)
                .resolve(blueprint);
        diagnostics.addAll(resolution.diagnostics());
        return resolution.value();
    }

    private StructureContentManifest manifestWithoutSelections(BlueprintDocument blueprint) {
        return new StructureContentManifest(
                blueprint.contentPolicy().allowInstalledModBlocks(),
                blueprint.contentPolicy().preferredDecorativeMods(),
                blueprint.contentPolicy().requiredMods(),
                blueprint.contentPolicy().enabledFunctionalSystems(),
                List.of()
        );
    }

    private List<StructureEntity> validateEntities(
            BlueprintDocument blueprint,
            List<StructureDiagnostic> diagnostics
    ) {
        List<StructureEntity> entities = new ArrayList<>();
        for (int index = 0; index < blueprint.entities().size(); index++) {
            BlueprintEntity entity = blueprint.entities().get(index);
            String location = "entities[" + index + "]";
            boolean finite = entity.position().stream().allMatch(Double::isFinite);
            if (!finite) {
                error(diagnostics, blueprint.source(), location + ".pos", "Entity coordinates must be finite numbers.");
            } else if (blueprint.size() != null && !containsEntity(blueprint.size(), entity.position())) {
                error(diagnostics, blueprint.source(), location + ".pos",
                        "Entity position " + entity.position() + " lies outside structure bounds "
                                + blueprint.size().display() + ".");
            }
            if (blueprint.size() != null && !blueprint.size().contains(entity.blockPosition())) {
                error(diagnostics, blueprint.source(), location + ".blockPos",
                        "Entity blockPos " + entity.blockPosition().display() + " lies outside structure bounds "
                                + blueprint.size().display() + ".");
            }
            validateSnbt(entity.entityNbtSnbt(), blueprint.source(), location + ".nbtSnbt", diagnostics);
            validateRawEntrySnbt(
                    entity.rawEntrySnbt(), ENTITY_ENTRY_FIELDS, blueprint.source(),
                    location + ".rawEntrySnbt", diagnostics
            );
            entities.add(new StructureEntity(
                    entity.position(), entity.blockPosition(), entity.entityNbtSnbt(), entity.rawEntrySnbt()
            ));
        }
        return entities;
    }

    private boolean containsEntity(StructureSize size, List<Double> position) {
        return position.get(0) >= 0.0D && position.get(0) < size.x()
                && position.get(1) >= 0.0D && position.get(1) < size.y()
                && position.get(2) >= 0.0D && position.get(2) < size.z();
    }

    private void validateMarkers(
            List<String> markers,
            Path source,
            String location,
            List<StructureDiagnostic> diagnostics
    ) {
        Set<String> unique = new HashSet<>();
        for (int index = 0; index < markers.size(); index++) {
            String marker = markers.get(index);
            if (!SAFE_MARKER.matcher(marker).matches()) {
                error(diagnostics, source, location + "[" + index + "]",
                        "Marker must be 1-128 lowercase resource-style characters.");
            } else if (!unique.add(marker)) {
                error(diagnostics, source, location + "[" + index + "]", "Duplicate marker '" + marker + "'.");
            }
        }
    }

    private void validateSnbt(
            String snbt,
            Path source,
            String location,
            List<StructureDiagnostic> diagnostics
    ) {
        if (snbt == null) {
            return;
        }
        try {
            TagParser.parseTag(snbt);
        } catch (CommandSyntaxException exception) {
            error(diagnostics, source, location, "Malformed compound SNBT: " + exception.getMessage());
        }
    }

    private void validateRawEntrySnbt(
            String snbt,
            Set<String> canonicalFields,
            Path source,
            String location,
            List<StructureDiagnostic> diagnostics
    ) {
        CompoundTag tag = parseSnbt(snbt, source, location, diagnostics);
        if (tag == null) {
            return;
        }
        canonicalFields.stream()
                .filter(tag::contains)
                .sorted()
                .forEach(field -> error(diagnostics, source, location + "." + field,
                        "Raw entry SNBT may contain only unknown fields; '" + field
                                + "' is owned by the canonical model."));
    }

    private void validateRawRootSnbt(
            String snbt,
            Path source,
            List<StructureDiagnostic> diagnostics
    ) {
        CompoundTag root = parseSnbt(snbt, source, "rawRootSnbt", diagnostics);
        if (root == null) {
            return;
        }
        ROOT_MODEL_FIELDS.stream()
                .filter(root::contains)
                .sorted()
                .forEach(field -> error(diagnostics, source, "rawRootSnbt." + field,
                        "Raw root SNBT may contain only extension fields; '" + field
                                + "' is owned by the canonical model."));

        if (!root.contains("structuregen")) {
            return;
        }
        if (!(root.get("structuregen") instanceof CompoundTag structureGen)) {
            error(diagnostics, source, "rawRootSnbt.structuregen", "Must be a compound tag when present.");
            return;
        }
        for (String canonical : List.of("formatVersion", "name", "markers", "contentManifest")) {
            if (structureGen.contains(canonical)) {
                error(diagnostics, source, "rawRootSnbt.structuregen." + canonical,
                        "This field is owned by the canonical Blueprint fields and may not be repeated in raw SNBT.");
            }
        }
        if (structureGen.contains("metadata")) {
            if (!(structureGen.get("metadata") instanceof CompoundTag metadata)) {
                error(diagnostics, source, "rawRootSnbt.structuregen.metadata", "Must be a compound tag.");
            } else {
                metadata.getAllKeys().stream()
                        .filter(key -> metadata.get(key) instanceof StringTag)
                        .sorted()
                        .forEach(key -> error(diagnostics, source,
                                "rawRootSnbt.structuregen.metadata." + key,
                                "String metadata belongs in the Blueprint metadata object; raw metadata is reserved "
                                        + "for preserved non-string extension values."));
            }
        }
        if (structureGen.contains("blockMarkers")) {
            Tag markerTag = structureGen.get("blockMarkers");
            if (!(markerTag instanceof ListTag markers)
                    || (!markers.isEmpty() && markers.getElementType() != Tag.TAG_COMPOUND)) {
                error(diagnostics, source, "rawRootSnbt.structuregen.blockMarkers",
                        "Must be a list of compound tags when preserved in raw SNBT.");
            }
        }
    }

    private CompoundTag parseSnbt(
            String snbt,
            Path source,
            String location,
            List<StructureDiagnostic> diagnostics
    ) {
        if (snbt == null) {
            return null;
        }
        try {
            return TagParser.parseTag(snbt);
        } catch (CommandSyntaxException exception) {
            error(diagnostics, source, location, "Malformed compound SNBT: " + exception.getMessage());
            return null;
        }
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
}
