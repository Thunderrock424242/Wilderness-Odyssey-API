package com.thunder.wildernessodysseyapi.quest.definition;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.thunder.wildernessodysseyapi.quest.validation.QuestContentLookup;
import com.thunder.wildernessodysseyapi.quest.validation.QuestValidationReport;
import com.thunder.wildernessodysseyapi.quest.validation.QuestValidator;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Strict schema v1 codec. Input byte/depth limits apply before constructing Gson trees. */
public final class QuestDefinitionCodec {
    public static final int MAX_SOURCE_BYTES = 256 * 1024;
    public static final int MAX_SNAPSHOT_BYTES = 16 * 1024 * 1024;
    public static final int MAX_QUESTS = 2000;
    public static final int MAX_CHAPTERS = 128;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public QuestValidationReport decode(List<QuestSourceDocument> resources, QuestContentLookup lookup) {
        List<QuestValidationReport.Finding> findings = new ArrayList<>();
        List<QuestCampaignSnapshot.Campaign> campaigns = new ArrayList<>();
        Map<ResourceLocation, QuestCampaignSnapshot.Chapter> chapters = new LinkedHashMap<>();
        Map<ResourceLocation, QuestDefinition> quests = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        long totalBytes = 0;
        if (resources.size() > MAX_QUESTS + MAX_CHAPTERS + 1) {
            QuestValidator.error(findings, null, "resources", "The campaign contains too many source files.");
            return new QuestValidationReport(Optional.empty(), findings);
        }
        for (var resource : resources) {
            if (!seen.add(resource.kind() + "/" + resource.id())) {
                QuestValidator.error(findings, resource.id(), "id", "This source identity is duplicated.");
                continue;
            }
            try {
                var json = resource.content();
                int sourceBytes = GSON.toJson(json).getBytes(StandardCharsets.UTF_8).length;
                totalBytes += sourceBytes;
                if (totalBytes > MAX_SNAPSHOT_BYTES) {
                    QuestValidator.error(findings, resource.id(), "resources", "The campaign input exceeds 16 MiB.");
                    return new QuestValidationReport(Optional.empty(), findings);
                }
                if (sourceBytes > MAX_SOURCE_BYTES) {
                    throw invalid("source", "Source JSON exceeds 256 KiB.");
                }
                integer(json, "schemaVersion", 1, 1);
                int version = (int) integer(json, "definitionVersion", 1, Integer.MAX_VALUE);
                switch (resource.kind()) {
                    case CAMPAIGN -> {
                        fields(json, "schemaVersion", "definitionVersion", "title", "description", "chapters");
                        campaigns.add(new QuestCampaignSnapshot.Campaign(resource.id(), version,
                                text(json, "title", 256), optionalText(json, "description", 4096), ids(json, "chapters", MAX_CHAPTERS, true)));
                    }
                    case CHAPTER -> {
                        fields(json, "schemaVersion", "definitionVersion", "campaign", "title", "quests", "requiredMods", "optionalMods");
                        List<String> required = modIds(json, "requiredMods");
                        List<String> optional = modIds(json, "optionalMods");
                        boolean disabled = optional.stream().anyMatch(mod -> !QuestValidator.hasMod(lookup, mod));
                        chapters.put(resource.id(), new QuestCampaignSnapshot.Chapter(resource.id(), version,
                                id(json, "campaign"), text(json, "title", 256), ids(json, "quests", MAX_QUESTS, true), required, optional, disabled));
                    }
                    case QUEST -> quests.put(resource.id(), quest(resource.id(), version, json));
                }
            } catch (FieldFailure failure) {
                QuestValidator.error(findings, resource.id(), failure.field, failure.getMessage());
            }
        }
        if (campaigns.size() != 1) {
            QuestValidator.error(findings, null, "campaign", "Exactly one campaign is required for a publication.");
        }
        if (chapters.size() > MAX_CHAPTERS || quests.size() > MAX_QUESTS) {
            QuestValidator.error(findings, null, "resources", "The chapter or quest count exceeds the campaign limit.");
        }
        if (!findings.isEmpty()) {
            return new QuestValidationReport(Optional.empty(), findings);
        }
        byte[] canonical = canonicalBytes(resources);
        if (canonical.length > MAX_SNAPSHOT_BYTES) {
            QuestValidator.error(findings, campaigns.getFirst().id(), "resources", "The campaign snapshot exceeds 16 MiB.");
            return new QuestValidationReport(Optional.empty(), findings);
        }
        var snapshot = new QuestCampaignSnapshot(1, campaigns.getFirst(), chapters, quests, resources, hash(canonical), lookup.generation());
        return new QuestValidator().validate(snapshot, lookup);
    }

    /** Produces detached source objects; source provenance does not contribute to content identity. */
    public List<QuestSourceDocument> encode(QuestCampaignSnapshot snapshot) {
        return snapshot.sources();
    }

    public byte[] encodeSnapshot(QuestCampaignSnapshot snapshot) {
        return canonicalBytes(encode(snapshot));
    }

    public QuestValidationReport decodeSnapshot(byte[] bytes, QuestContentLookup lookup) throws IOException {
        JsonObject root = parseBounded(bytes, MAX_SNAPSHOT_BYTES, 1_000_000);
        try {
            fields(root, "schemaVersion", "documents");
            integer(root, "schemaVersion", 1, 1);
            List<QuestSourceDocument> documents = new ArrayList<>();
            for (var element : array(root, "documents", MAX_QUESTS + MAX_CHAPTERS + 1, true)) {
                var entry = object(element, "documents");
                fields(entry, "kind", "id", "content");
                QuestSourceDocument.Kind kind;
                try {
                    kind = QuestSourceDocument.Kind.valueOf(text(entry, "kind", 16));
                } catch (IllegalArgumentException exception) {
                    throw invalid("documents.kind", "Unknown source kind.");
                }
                documents.add(new QuestSourceDocument(kind, id(entry, "id"), object(entry.get("content"), "content"), "stored"));
            }
            return decode(documents, lookup);
        } catch (FieldFailure failure) {
            throw new IOException("Invalid stored quest snapshot at " + failure.field + ": " + failure.getMessage(), failure);
        }
    }

    public JsonObject readBounded(InputStream stream) throws IOException {
        return parseBounded(stream.readNBytes(MAX_SOURCE_BYTES + 1), MAX_SOURCE_BYTES, 65_536);
    }

    /** Bounded read-only player projections may span several wire chunks. */
    public JsonObject readProjection(byte[] bytes) throws IOException {
        return parseBounded(bytes, MAX_SNAPSHOT_BYTES, 1_000_000);
    }

    private QuestDefinition quest(ResourceLocation id, int version, JsonObject json) {
        fields(json, "schemaVersion", "definitionVersion", "chapter", "title", "description", "icon", "optional", "hidden",
                "repeatable", "cooldownTicks", "prerequisites", "objectives", "rewards");
        var prerequisite = json.has("prerequisites") ? object(json.get("prerequisites"), "prerequisites") : new JsonObject();
        fields(prerequisite, "mode", "quests");
        String mode = prerequisite.has("mode") ? text(prerequisite, "mode", 8) : "all";
        var prerequisiteMode = switch (mode) {
            case "all" -> QuestDefinition.PrerequisiteMode.ALL;
            case "any" -> QuestDefinition.PrerequisiteMode.ANY;
            default -> throw invalid("prerequisites.mode", "Prerequisites must use all or any.");
        };
        List<ObjectiveDefinition> objectives = new ArrayList<>();
        for (var entry : array(json, "objectives", 32, true)) {
            objectives.add(objective(object(entry, "objectives")));
        }
        if (objectives.isEmpty()) {
            throw invalid("objectives", "A quest needs at least one objective.");
        }
        List<RewardDefinition> rewards = new ArrayList<>();
        for (var entry : array(json, "rewards", 16, false)) {
            rewards.add(reward(object(entry, "rewards")));
        }
        boolean repeatable = flag(json, "repeatable", false);
        long cooldown = json.has("cooldownTicks") ? integer(json, "cooldownTicks", 0, Long.MAX_VALUE) : 0;
        if (!repeatable && cooldown != 0) {
            throw invalid("cooldownTicks", "Only repeatable quests can have a cooldown.");
        }
        return new QuestDefinition(id, version, id(json, "chapter"), text(json, "title", 256), optionalText(json, "description", 4096),
                json.has("icon") ? id(json, "icon") : ResourceLocation.withDefaultNamespace("book"),
                flag(json, "optional", true), flag(json, "hidden", false), repeatable, cooldown, prerequisiteMode,
                ids(prerequisite, "quests", MAX_QUESTS, false), objectives, rewards);
    }

    private ObjectiveDefinition objective(JsonObject json) {
        String type = text(json, "type", 32);
        long goal = json.has("goal") ? integer(json, "goal", 1, Long.MAX_VALUE) : 1;
        ObjectiveDefinition.Parameters parameters = switch (type) {
            case "possession" -> {
                fields(json, "id", "type", "goal", "dependsOn", "items", "tags");
                var items = ids(json, "items", 256, false);
                var tags = ids(json, "tags", 256, false);
                if (items.isEmpty() && tags.isEmpty()) {
                    throw invalid("objectives.items", "Possession needs at least one item or tag.");
                }
                yield new ObjectiveDefinition.Possession(Set.copyOf(items), Set.copyOf(tags));
            }
            case "dimension" -> {
                fields(json, "id", "type", "goal", "dependsOn", "dimension");
                yield new ObjectiveDefinition.Dimension(id(json, "dimension"));
            }
            case "lore" -> {
                fields(json, "id", "type", "goal", "dependsOn", "lore");
                yield new ObjectiveDefinition.Lore(id(json, "lore"));
            }
            case "custom_event" -> {
                fields(json, "id", "type", "goal", "dependsOn", "producer");
                yield new ObjectiveDefinition.CustomEvent(id(json, "producer"));
            }
            default -> throw invalid("objectives.type", "This objective provider is unsupported.");
        };
        if ((parameters instanceof ObjectiveDefinition.Dimension || parameters instanceof ObjectiveDefinition.Lore) && goal != 1) {
            throw invalid("objectives.goal", "Presence and lore objectives must have a goal of one.");
        }
        return new ObjectiveDefinition(id(json, "id"), goal, parameters, ids(json, "dependsOn", 32, false));
    }

    public RewardDefinition reward(JsonObject json) {
        RewardDefinition.Value value = switch (text(json, "type", 32)) {
            case "item" -> {
                fields(json, "id", "type", "item", "count");
                yield new RewardDefinition.Item(id(json, "item"), (int) integer(json, "count", 1, 64));
            }
            case "xp" -> {
                fields(json, "id", "type", "amount");
                yield new RewardDefinition.Experience((int) integer(json, "amount", 1, 1_000_000));
            }
            case "lore" -> {
                fields(json, "id", "type", "lore");
                yield new RewardDefinition.Lore(id(json, "lore"));
            }
            default -> throw invalid("rewards.type", "This reward type is unsupported.");
        };
        return new RewardDefinition(id(json, "id"), value);
    }

    private static byte[] canonicalBytes(List<QuestSourceDocument> sources) {
        var root = new JsonObject();
        root.addProperty("schemaVersion", 1);
        var documents = new JsonArray();
        sources.stream().sorted(Comparator.comparing((QuestSourceDocument source) -> source.kind().name())
                        .thenComparing(source -> source.id().toString()))
                .forEach(source -> {
                    var document = new JsonObject();
                    document.addProperty("kind", source.kind().name());
                    document.addProperty("id", source.id().toString());
                    document.add("content", source.content());
                    documents.add(document);
                });
        root.add("documents", documents);
        return GSON.toJson(canonical(root)).getBytes(StandardCharsets.UTF_8);
    }

    private static JsonElement canonical(JsonElement element) {
        if (element.isJsonObject()) {
            var sorted = new JsonObject();
            element.getAsJsonObject().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> sorted.add(entry.getKey(), canonical(entry.getValue())));
            return sorted;
        }
        if (element.isJsonArray()) {
            var array = new JsonArray();
            element.getAsJsonArray().forEach(value -> array.add(canonical(value)));
            return array;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return new JsonPrimitive(element.getAsBigDecimal().stripTrailingZeros());
        }
        return element.deepCopy();
    }

    public static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK does not provide SHA-256", exception);
        }
    }

    private static JsonObject parseBounded(byte[] bytes, int maxBytes, int maxNodes) throws IOException {
        if (bytes.length > maxBytes) {
            throw new IOException("Quest JSON exceeds its byte limit.");
        }
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        try (var reader = new JsonReader(new StringReader(text))) {
            JsonElement root = readValue(reader, 0, new int[]{maxNodes});
            if (reader.peek() != JsonToken.END_DOCUMENT || !root.isJsonObject()) {
                throw new IOException("Quest JSON must contain exactly one object.");
            }
            return root.getAsJsonObject();
        } catch (IllegalStateException | NumberFormatException exception) {
            throw new IOException("Malformed quest JSON.", exception);
        }
    }

    private static JsonElement readValue(JsonReader reader, int depth, int[] remaining) throws IOException {
        if (depth > 64 || --remaining[0] < 0) {
            throw new IOException("Quest JSON exceeds its nesting or element limit.");
        }
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                var object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) {
                        throw new IOException("Quest JSON contains a duplicate field.");
                    }
                    object.add(name, readValue(reader, depth + 1, remaining));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                var array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) {
                    array.add(readValue(reader, depth + 1, remaining));
                }
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw new IOException("Malformed quest JSON value.");
        };
    }

    private static void fields(JsonObject json, String... allowed) {
        Set<String> names = Set.of(allowed);
        for (String key : json.keySet()) {
            if (!names.contains(key)) {
                throw invalid(key, "Unknown field for this definition type.");
            }
        }
    }

    private static JsonObject object(JsonElement element, String field) {
        if (element == null || !element.isJsonObject()) {
            throw invalid(field, "An object is required.");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonObject json, String field, int max, boolean required) {
        if (!json.has(field) && !required) {
            return new JsonArray();
        }
        if (!json.has(field) || !json.get(field).isJsonArray() || json.getAsJsonArray(field).size() > max) {
            throw invalid(field, "A list within the supported size limit is required.");
        }
        return json.getAsJsonArray(field);
    }

    private static List<ResourceLocation> ids(JsonObject json, String field, int max, boolean required) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (var element : array(json, field, max, required)) {
            ids.add(parseId(string(element, field, 256), field));
        }
        return ids;
    }

    private static List<String> modIds(JsonObject json, String field) {
        List<String> mods = new ArrayList<>();
        for (var element : array(json, field, 128, false)) {
            String mod = string(element, field, 64);
            if (!mod.matches("[a-z][a-z0-9_]{0,63}")) {
                throw invalid(field, "A lowercase mod ID is required.");
            }
            mods.add(mod);
        }
        return mods;
    }

    private static ResourceLocation id(JsonObject json, String field) {
        return parseId(text(json, field, 256), field);
    }

    private static ResourceLocation parseId(String text, String field) {
        var id = ResourceLocation.tryParse(text);
        if (id == null || !text.contains(":")) {
            throw invalid(field, "A valid namespaced resource ID is required.");
        }
        return id;
    }

    private static String text(JsonObject json, String field, int max) {
        return string(json.get(field), field, max);
    }

    private static String optionalText(JsonObject json, String field, int max) {
        if (!json.has(field)) {
            return "";
        }
        var value = json.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().length() > max) {
            throw invalid(field, "Text exceeds its supported length or has the wrong type.");
        }
        return value.getAsString();
    }

    private static String string(JsonElement value, String field, int max) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank() || value.getAsString().length() > max) {
            throw invalid(field, "Nonempty text within the supported length is required.");
        }
        return value.getAsString();
    }

    private static long integer(JsonObject json, String field, long min, long max) {
        var value = json.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw invalid(field, "An integer within the supported range is required.");
        }
        try {
            long number = value.getAsBigDecimal().longValueExact();
            if (number < min || number > max) {
                throw invalid(field, "An integer within the supported range is required.");
            }
            return number;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw invalid(field, "An integer within the supported range is required.");
        }
    }

    private static boolean flag(JsonObject json, String field, boolean fallback) {
        if (!json.has(field)) {
            return fallback;
        }
        var value = json.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw invalid(field, "A boolean is required.");
        }
        return value.getAsBoolean();
    }

    private static FieldFailure invalid(String field, String message) {
        return new FieldFailure(field, message);
    }

    private static final class FieldFailure extends IllegalArgumentException {
        private final String field;

        private FieldFailure(String field, String message) {
            super(message);
            this.field = field;
        }
    }
}
