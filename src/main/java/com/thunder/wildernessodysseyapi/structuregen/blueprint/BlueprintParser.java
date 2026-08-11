package com.thunder.wildernessodysseyapi.structuregen.blueprint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.thunder.wildernessodysseyapi.structuregen.diagnostic.DiagnosticSeverity;
import com.thunder.wildernessodysseyapi.structuregen.diagnostic.StructureDiagnostic;
import com.thunder.wildernessodysseyapi.structuregen.diagnostic.StructureGenResult;
import com.thunder.wildernessodysseyapi.structuregen.model.StructurePosition;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureSize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Strict JSON parser for StructureGen Blueprint Format v1.
 *
 * <p>This stage validates JSON shape and types only. Bounds, duplicate coordinates,
 * registry IDs, block states, and output safety are handled by the validator.</p>
 */
public final class BlueprintParser {

    private static final Set<String> ROOT_FIELDS = Set.of(
            "formatVersion", "name", "dataVersion", "size", "metadata", "markers",
            "blocks", "entities", "rawRootSnbt", "contentPolicy", "materials",
            "sourcePalettes", "unsupportedFields"
    );
    private static final Set<String> BLOCK_FIELDS = Set.of(
            "pos", "block", "properties", "blockEntitySnbt", "markers", "rawEntrySnbt",
            "sourcePaletteIndex", "usageIntent", "requiredSystem"
    );
    private static final Set<String> ENTITY_FIELDS = Set.of("pos", "blockPos", "nbtSnbt", "rawEntrySnbt");
    private static final Set<String> CONTENT_POLICY_FIELDS = Set.of(
            "allowInstalledModBlocks", "preferredDecorativeMods", "requiredMods", "enabledFunctionalSystems"
    );
    private static final Set<String> MATERIAL_FIELDS = Set.of(
            "intent", "requiredSystem", "preferred", "fallbacks"
    );
    private static final Set<String> MATERIAL_CANDIDATE_FIELDS = Set.of(
            "block", "properties", "requiresMod"
    );

    /** Reads and parses one UTF-8 blueprint file without producing any output files. */
    public StructureGenResult<BlueprintDocument> parse(Path source) {
        try {
            return parse(Files.readString(source, StandardCharsets.UTF_8), source);
        } catch (IOException exception) {
            return failure(source, "file", "Unable to read blueprint: " + exception.getMessage());
        }
    }

    /** Parses blueprint JSON text for tests and other non-file callers. */
    public StructureGenResult<BlueprintDocument> parse(String json, Path source) {
        List<StructureDiagnostic> diagnostics = new ArrayList<>();
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                return failure(source, "$", "Blueprint root must be a JSON object.");
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException exception) {
            return failure(source, "$", "Malformed JSON: " + exception.getMessage());
        }

        validateKnownFields(root, ROOT_FIELDS, "$", source, diagnostics);
        warnForExportOnlyExtensions(root, source, diagnostics);

        Integer formatVersion = requiredInt(root, "formatVersion", "$", source, diagnostics);
        String name = requiredString(root, "name", "$", source, diagnostics);
        StructureSize size = parseSize(root.get("size"), "size", source, diagnostics);
        Integer dataVersion = optionalInt(root, "dataVersion", "$", source, diagnostics);
        Map<String, String> metadata = parseStringMap(root.get("metadata"), "metadata", source, diagnostics, true);
        List<String> markers = parseStringList(root.get("markers"), "markers", source, diagnostics, true);
        List<BlueprintBlock> blocks = parseBlocks(root.get("blocks"), source, diagnostics);
        List<BlueprintEntity> entities = parseEntities(root.get("entities"), source, diagnostics);
        String rawRootSnbt = optionalString(root, "rawRootSnbt", "$", source, diagnostics);
        BlueprintContentPolicy contentPolicy = parseContentPolicy(
                root.get("contentPolicy"), source, diagnostics
        );
        Map<String, BlueprintMaterialDefinition> materials = parseMaterials(
                root.get("materials"), source, diagnostics
        );

        if (hasErrors(diagnostics)) {
            return new StructureGenResult<>(null, diagnostics);
        }
        BlueprintDocument document = new BlueprintDocument(
                source,
                formatVersion,
                name,
                size,
                dataVersion,
                metadata,
                markers,
                blocks,
                entities,
                rawRootSnbt,
                contentPolicy,
                materials
        );
        return new StructureGenResult<>(document, diagnostics);
    }

    // Content policy remains optional so every existing concrete-only Blueprint keeps its behavior.
    private BlueprintContentPolicy parseContentPolicy(
            JsonElement element,
            Path source,
            List<StructureDiagnostic> diagnostics
    ) {
        if (element == null || element.isJsonNull()) {
            return BlueprintContentPolicy.defaults();
        }
        if (!element.isJsonObject()) {
            error(diagnostics, source, "contentPolicy", "Must be an object when present.");
            return BlueprintContentPolicy.defaults();
        }

        JsonObject object = element.getAsJsonObject();
        validateKnownFields(object, CONTENT_POLICY_FIELDS, "contentPolicy", source, diagnostics);
        boolean allowInstalledModBlocks = optionalBoolean(
                object,
                "allowInstalledModBlocks",
                "contentPolicy",
                true,
                source,
                diagnostics
        );
        List<String> preferredDecorativeMods = parseStringList(
                object.get("preferredDecorativeMods"),
                "contentPolicy.preferredDecorativeMods",
                source,
                diagnostics,
                true
        );
        List<String> requiredMods = parseStringList(
                object.get("requiredMods"),
                "contentPolicy.requiredMods",
                source,
                diagnostics,
                true
        );
        List<String> enabledFunctionalSystems = parseStringList(
                object.get("enabledFunctionalSystems"),
                "contentPolicy.enabledFunctionalSystems",
                source,
                diagnostics,
                true
        );
        return new BlueprintContentPolicy(
                allowInstalledModBlocks,
                preferredDecorativeMods,
                requiredMods,
                enabledFunctionalSystems
        );
    }

    // Material keys are sorted in the parsed document so later resolution never depends on JSON object order.
    private Map<String, BlueprintMaterialDefinition> parseMaterials(
            JsonElement element,
            Path source,
            List<StructureDiagnostic> diagnostics
    ) {
        Map<String, BlueprintMaterialDefinition> materials = new TreeMap<>();
        if (element == null || element.isJsonNull()) {
            return materials;
        }
        if (!element.isJsonObject()) {
            error(diagnostics, source, "materials", "Must be an object when present.");
            return materials;
        }

        element.getAsJsonObject().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String location = "materials." + entry.getKey();
                    BlueprintMaterialDefinition definition = parseMaterialDefinition(
                            entry.getValue(), location, source, diagnostics
                    );
                    if (definition != null) {
                        materials.put(entry.getKey(), definition);
                    }
                });
        return materials;
    }

    private BlueprintMaterialDefinition parseMaterialDefinition(
            JsonElement element,
            String location,
            Path source,
            List<StructureDiagnostic> diagnostics
    ) {
        if (!element.isJsonObject()) {
            error(diagnostics, source, location, "Material definition must be an object.");
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        validateKnownFields(object, MATERIAL_FIELDS, location, source, diagnostics);
        String intent = optionalString(object, "intent", location, source, diagnostics);
        if (intent == null) {
            intent = BlueprintMaterialDefinition.DEFAULT_INTENT;
        }
        String requiredSystem = optionalString(object, "requiredSystem", location, source, diagnostics);
        List<BlueprintMaterialCandidate> preferred = parseMaterialCandidates(
                object.get("preferred"), location + ".preferred", source, diagnostics
        );
        List<BlueprintMaterialCandidate> fallbacks = parseMaterialCandidates(
                object.get("fallbacks"), location + ".fallbacks", source, diagnostics
        );
        return new BlueprintMaterialDefinition(intent, requiredSystem, preferred, fallbacks);
    }

    private List<BlueprintMaterialCandidate> parseMaterialCandidates(
            JsonElement element,
            String location,
            Path source,
            List<StructureDiagnostic> diagnostics
    ) {
        List<BlueprintMaterialCandidate> candidates = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return candidates;
        }
        if (!element.isJsonArray()) {
            error(diagnostics, source, location, "Must be an array of material candidate objects.");
            return candidates;
        }

        JsonArray array = element.getAsJsonArray();
        for (int index = 0; index < array.size(); index++) {
            String candidateLocation = location + "[" + index + "]";
            JsonElement candidateElement = array.get(index);
            if (!candidateElement.isJsonObject()) {
                error(diagnostics, source, candidateLocation, "Material candidate must be an object.");
                continue;
            }
            JsonObject candidate = candidateElement.getAsJsonObject();
            validateKnownFields(
                    candidate,
                    MATERIAL_CANDIDATE_FIELDS,
                    candidateLocation,
                    source,
                    diagnostics
            );
            String blockId = requiredString(candidate, "block", candidateLocation, source, diagnostics);
            Map<String, String> properties = parseStringMap(
                    candidate.get("properties"),
                    candidateLocation + ".properties",
                    source,
                    diagnostics,
                    true
            );
            String requiresMod = optionalString(
                    candidate, "requiresMod", candidateLocation, source, diagnostics
            );
            if (blockId != null) {
                candidates.add(new BlueprintMaterialCandidate(blockId, properties, requiresMod));
            }
        }
        return candidates;
    }

    // Parse each block independently so one malformed entry does not hide later diagnostics.
    private List<BlueprintBlock> parseBlocks(
            JsonElement element,
            Path source,
            List<StructureDiagnostic> diagnostics
    ) {
        List<BlueprintBlock> blocks = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            error(diagnostics, source, "blocks", "Required field is missing.");
            return blocks;
        }
        if (!element.isJsonArray()) {
            error(diagnostics, source, "blocks", "Must be an array.");
            return blocks;
        }
        JsonArray array = element.getAsJsonArray();
        for (int index = 0; index < array.size(); index++) {
            String location = "blocks[" + index + "]";
            JsonElement entry = array.get(index);
            if (!entry.isJsonObject()) {
                error(diagnostics, source, location, "Block entry must be an object.");
                continue;
            }
            JsonObject object = entry.getAsJsonObject();
            validateKnownFields(object, BLOCK_FIELDS, location, source, diagnostics);
            StructurePosition position = parsePosition(object.get("pos"), location + ".pos", source, diagnostics);
            String blockId = requiredString(object, "block", location, source, diagnostics);
            Map<String, String> properties = parseStringMap(
                    object.get("properties"), location + ".properties", source, diagnostics, true
            );
            String blockEntitySnbt = optionalString(object, "blockEntitySnbt", location, source, diagnostics);
            String usageIntent = optionalString(object, "usageIntent", location, source, diagnostics);
            String requiredSystem = optionalString(object, "requiredSystem", location, source, diagnostics);
            List<String> markers = parseStringList(
                    object.get("markers"), location + ".markers", source, diagnostics, true
            );
            String rawEntrySnbt = optionalString(object, "rawEntrySnbt", location, source, diagnostics);
            if (position != null && blockId != null) {
                blocks.add(new BlueprintBlock(
                        position, blockId, properties, blockEntitySnbt, markers, rawEntrySnbt,
                        usageIntent, requiredSystem
                ));
            }
        }
        return blocks;
    }

    // Entity support primarily serves loss-aware NBT exports; authored blueprints may omit it.
    private List<BlueprintEntity> parseEntities(
            JsonElement element,
            Path source,
            List<StructureDiagnostic> diagnostics
    ) {
        List<BlueprintEntity> entities = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return entities;
        }
        if (!element.isJsonArray()) {
            error(diagnostics, source, "entities", "Must be an array when present.");
            return entities;
        }
        JsonArray array = element.getAsJsonArray();
        for (int index = 0; index < array.size(); index++) {
            String location = "entities[" + index + "]";
            JsonElement entry = array.get(index);
            if (!entry.isJsonObject()) {
                error(diagnostics, source, location, "Entity entry must be an object.");
                continue;
            }
            JsonObject object = entry.getAsJsonObject();
            validateKnownFields(object, ENTITY_FIELDS, location, source, diagnostics);
            List<Double> position = parseDoubleTriple(object.get("pos"), location + ".pos", source, diagnostics);
            StructurePosition blockPosition = parsePosition(
                    object.get("blockPos"), location + ".blockPos", source, diagnostics
            );
            String nbt = requiredString(object, "nbtSnbt", location, source, diagnostics);
            String rawEntry = optionalString(object, "rawEntrySnbt", location, source, diagnostics);
            if (position != null && blockPosition != null && nbt != null) {
                entities.add(new BlueprintEntity(position, blockPosition, nbt, rawEntry));
            }
        }
        return entities;
    }

    private StructureSize parseSize(
            JsonElement element,
            String location,
            Path source,
            List<StructureDiagnostic> diagnostics
    ) {
        int[] values = parseIntTriple(element, location, source, diagnostics);
        return values == null ? null : new StructureSize(values[0], values[1], values[2]);
    }

    private StructurePosition parsePosition(
            JsonElement element,
            String location,
            Path source,
            List<StructureDiagnostic> diagnostics
    ) {
        int[] values = parseIntTriple(element, location, source, diagnostics);
        return values == null ? null : new StructurePosition(values[0], values[1], values[2]);
    }

    private int[] parseIntTriple(
            JsonElement element,
            String location,
            Path source,
            List<StructureDiagnostic> diagnostics
    ) {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != 3) {
            error(diagnostics, source, location, "Must be an array containing exactly three integers.");
            return null;
        }
        JsonArray array = element.getAsJsonArray();
        int[] values = new int[3];
        for (int index = 0; index < 3; index++) {
            Integer value = exactInt(array.get(index));
            if (value == null) {
                error(diagnostics, source, location + "[" + index + "]", "Must be an integer.");
                return null;
            }
            values[index] = value;
        }
        return values;
    }

    private List<Double> parseDoubleTriple(
            JsonElement element,
            String location,
            Path source,
            List<StructureDiagnostic> diagnostics
    ) {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != 3) {
            error(diagnostics, source, location, "Must be an array containing exactly three numbers.");
            return null;
        }
        List<Double> values = new ArrayList<>(3);
        for (int index = 0; index < 3; index++) {
            JsonElement value = element.getAsJsonArray().get(index);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                error(diagnostics, source, location + "[" + index + "]", "Must be a number.");
                return null;
            }
            try {
                values.add(value.getAsDouble());
            } catch (NumberFormatException exception) {
                error(diagnostics, source, location + "[" + index + "]", "Must be a finite number.");
                return null;
            }
        }
        return values;
    }

    private Map<String, String> parseStringMap(
            JsonElement element,
            String location,
            Path source,
            List<StructureDiagnostic> diagnostics,
            boolean optional
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        if (element == null || element.isJsonNull()) {
            if (!optional) {
                error(diagnostics, source, location, "Required object is missing.");
            }
            return values;
        }
        if (!element.isJsonObject()) {
            error(diagnostics, source, location, "Must be an object whose values are strings.");
            return values;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString()) {
                error(diagnostics, source, location + "." + entry.getKey(), "Must be a string.");
                continue;
            }
            values.put(entry.getKey(), entry.getValue().getAsString());
        }
        return values;
    }

    private List<String> parseStringList(
            JsonElement element,
            String location,
            Path source,
            List<StructureDiagnostic> diagnostics,
            boolean optional
    ) {
        List<String> values = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            if (!optional) {
                error(diagnostics, source, location, "Required array is missing.");
            }
            return values;
        }
        if (!element.isJsonArray()) {
            error(diagnostics, source, location, "Must be an array of strings.");
            return values;
        }
        JsonArray array = element.getAsJsonArray();
        for (int index = 0; index < array.size(); index++) {
            JsonElement value = array.get(index);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                error(diagnostics, source, location + "[" + index + "]", "Must be a string.");
                continue;
            }
            values.add(value.getAsString());
        }
        return values;
    }

    private Integer requiredInt(
            JsonObject object,
            String field,
            String location,
            Path source,
            List<StructureDiagnostic> diagnostics
    ) {
        if (!object.has(field)) {
            error(diagnostics, source, location + "." + field, "Required field is missing.");
            return null;
        }
        Integer value = exactInt(object.get(field));
        if (value == null) {
            error(diagnostics, source, location + "." + field, "Must be an integer.");
        }
        return value;
    }

    private Integer optionalInt(
            JsonObject object,
            String field,
            String location,
            Path source,
            List<StructureDiagnostic> diagnostics
    ) {
        if (!object.has(field) || object.get(field).isJsonNull()) {
            return null;
        }
        Integer value = exactInt(object.get(field));
        if (value == null) {
            error(diagnostics, source, location + "." + field, "Must be an integer.");
        }
        return value;
    }

    private boolean optionalBoolean(
            JsonObject object,
            String field,
            String location,
            boolean defaultValue,
            Path source,
            List<StructureDiagnostic> diagnostics
    ) {
        if (!object.has(field) || object.get(field).isJsonNull()) {
            return defaultValue;
        }
        JsonElement value = object.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            error(diagnostics, source, location + "." + field, "Must be a boolean.");
            return defaultValue;
        }
        return value.getAsBoolean();
    }

    private Integer exactInt(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        try {
            return element.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
    }

    private String requiredString(
            JsonObject object,
            String field,
            String location,
            Path source,
            List<StructureDiagnostic> diagnostics
    ) {
        if (!object.has(field)) {
            error(diagnostics, source, location + "." + field, "Required field is missing.");
            return null;
        }
        JsonElement value = object.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            error(diagnostics, source, location + "." + field, "Must be a string.");
            return null;
        }
        return value.getAsString();
    }

    private String optionalString(
            JsonObject object,
            String field,
            String location,
            Path source,
            List<StructureDiagnostic> diagnostics
    ) {
        if (!object.has(field) || object.get(field).isJsonNull()) {
            return null;
        }
        JsonElement value = object.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            error(diagnostics, source, location + "." + field, "Must be a string.");
            return null;
        }
        return value.getAsString();
    }

    private boolean hasErrors(List<StructureDiagnostic> diagnostics) {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
    }

    private void validateKnownFields(
            JsonObject object,
            Set<String> supported,
            String location,
            Path source,
            List<StructureDiagnostic> diagnostics
    ) {
        object.keySet().stream()
                .filter(field -> !supported.contains(field))
                .sorted()
                .forEach(field -> error(diagnostics, source, location + "." + field,
                        "Unknown Blueprint v1 field; check the spelling or update the format version."));
    }

    private void warnForExportOnlyExtensions(
            JsonObject root,
            Path source,
            List<StructureDiagnostic> diagnostics
    ) {
        if (root.has("sourcePalettes")) {
            warning(diagnostics, source, "sourcePalettes",
                    "Export-only palette data is retained for inspection but Blueprint v1 import does not "
                            + "reconstruct alternate palettes or raw palette-entry tags.");
        }
        if (root.has("unsupportedFields")) {
            warning(diagnostics, source, "unsupportedFields",
                    "Exported unsupported-field annotations are not model data; review them before regeneration.");
        }
        if (root.get("blocks") instanceof JsonArray blocks) {
            boolean hasSourceIndices = false;
            for (JsonElement block : blocks) {
                if (block instanceof JsonObject blockObject && blockObject.has("sourcePaletteIndex")) {
                    hasSourceIndices = true;
                    break;
                }
            }
            if (hasSourceIndices) {
                warning(diagnostics, source, "blocks[*].sourcePaletteIndex",
                        "Export-only source palette indices are not imported; each block uses its declared "
                                + "block ID and properties from the primary palette.");
            }
        }
    }

    private void error(List<StructureDiagnostic> diagnostics, Path source, String location, String message) {
        diagnostics.add(new StructureDiagnostic(DiagnosticSeverity.ERROR, source, location, message));
    }

    private void warning(List<StructureDiagnostic> diagnostics, Path source, String location, String message) {
        diagnostics.add(new StructureDiagnostic(DiagnosticSeverity.WARNING, source, location, message));
    }

    private StructureGenResult<BlueprintDocument> failure(Path source, String location, String message) {
        return new StructureGenResult<>(
                null,
                List.of(new StructureDiagnostic(DiagnosticSeverity.ERROR, source, location, message))
        );
    }
}
