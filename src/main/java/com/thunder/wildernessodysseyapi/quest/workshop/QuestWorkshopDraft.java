package com.thunder.wildernessodysseyapi.quest.workshop;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.quest.definition.QuestDefinitionCodec;
import com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Detached authoring state. Revision belongs to durable acknowledgement; layout never changes live definitions. */
public record QuestWorkshopDraft(UUID id, long revision, List<QuestSourceDocument> sources,
                                 Map<ResourceLocation, Position> positions) {
    static final Gson GSON = new Gson();
    public static final int MAX_BYTES = QuestDefinitionCodec.MAX_SNAPSHOT_BYTES;
    public record Key(QuestSourceDocument.Kind kind, ResourceLocation id) { }
    public record Position(int x, int y) {
        public Position {
            if (Math.abs((long) x) > 100_000 || Math.abs((long) y) > 100_000) throw new IllegalArgumentException("Node position exceeds the Workshop bounds.");
        }
    }

    public QuestWorkshopDraft {
        Objects.requireNonNull(id);
        if (revision < 0) throw new IllegalArgumentException("Invalid draft revision.");
        // A draft is an independent authored copy; datapack provenance is not persisted as its authority.
        sources = sources.stream().map(source -> source.sourcePack().equals("workshop") ? source
                : new QuestSourceDocument(source.kind(), source.id(), source.content(), "workshop")).toList();
        positions = Map.copyOf(positions);
        var keys = new HashSet<Key>(); int campaigns = 0, chapters = 0, quests = 0;
        for (var source : sources) {
            checkId(source.id());
            if (!keys.add(new Key(source.kind(), source.id()))) throw new IllegalArgumentException("Duplicate draft source.");
            switch (source.kind()) { case CAMPAIGN -> campaigns++; case CHAPTER -> chapters++; case QUEST -> quests++; }
            checkContent(source.content());
        }
        if (campaigns > 1 || chapters > QuestDefinitionCodec.MAX_CHAPTERS || quests > QuestDefinitionCodec.MAX_QUESTS) throw new IllegalArgumentException("Draft source count exceeds its limit.");
        for (var entry : positions.entrySet()) {
            if (!keys.contains(new Key(QuestSourceDocument.Kind.QUEST, entry.getKey()))) throw new IllegalArgumentException("Layout references an absent quest.");
            Objects.requireNonNull(entry.getValue());
        }
        if (bytes(json(id, revision, sources, positions)).length > MAX_BYTES - 4096) throw new IllegalArgumentException("Draft exceeds 16 MiB.");
    }

    public static QuestWorkshopDraft create(List<QuestSourceDocument> sources) {
        return new QuestWorkshopDraft(UUID.randomUUID(), 0, sources, Map.of());
    }
    public Optional<QuestSourceDocument> source(QuestSourceDocument.Kind kind, ResourceLocation sourceId) {
        return sources.stream().filter(source -> source.kind() == kind && source.id().equals(sourceId)).findFirst();
    }
    public QuestWorkshopDraft withRevision(long next) { return new QuestWorkshopDraft(id, next, sources, positions); }

    public QuestWorkshopDraft apply(QuestWorkshopEdit edit) {
        var documents = new LinkedHashMap<Key, QuestSourceDocument>();
        for (var source : sources) documents.put(new Key(source.kind(), source.id()), source);
        for (var change : edit.changes()) {
            var key = new Key(change.kind(), change.id());
            if (change.content() == null) documents.remove(key);
            else documents.put(key, new QuestSourceDocument(change.kind(), change.id(), change.content(), "workshop"));
        }
        var layout = new HashMap<>(positions);
        for (var move : edit.moves()) {
            if (move.position() == null) layout.remove(move.id()); else layout.put(move.id(), move.position());
        }
        layout.keySet().removeIf(quest -> !documents.containsKey(new Key(QuestSourceDocument.Kind.QUEST, quest)));
        return new QuestWorkshopDraft(id, revision, List.copyOf(documents.values()), layout);
    }

    public byte[] encode() {
        var root = json(id, revision, sources, positions);
        root.addProperty("checksum", QuestDefinitionCodec.hash(bytes(root)));
        return bytes(root);
    }
    public static QuestWorkshopDraft decode(byte[] bytes) throws IOException {
        try {
            var root = new QuestDefinitionCodec().readProjection(bytes);
            fields(root, "formatVersion", "id", "revision", "documents", "positions", "checksum");
            if (number(root, "formatVersion") != 1) throw new IllegalArgumentException("Unsupported Workshop draft version.");
            String checksum = root.get("checksum").getAsString(); root.remove("checksum");
            if (!QuestDefinitionCodec.hash(bytes(root)).equals(checksum)) throw new IllegalArgumentException("Workshop draft checksum mismatch.");
            var sources = new ArrayList<QuestSourceDocument>();
            var documents = root.getAsJsonArray("documents");
            if (documents.size() > 2129) throw new IllegalArgumentException("Too many sources.");
            for (var element : documents) {
                var entry = element.getAsJsonObject(); fields(entry, "kind", "id", "content");
                sources.add(new QuestSourceDocument(QuestSourceDocument.Kind.valueOf(entry.get("kind").getAsString()),
                        parseId(entry.get("id").getAsString()), entry.getAsJsonObject("content"), "workshop"));
            }
            var positions = new HashMap<ResourceLocation, Position>();
            var layout = root.getAsJsonArray("positions");
            if (layout.size() > QuestDefinitionCodec.MAX_QUESTS) throw new IllegalArgumentException("Too many positions.");
            for (var element : layout) {
                var entry = element.getAsJsonObject(); fields(entry, "id", "x", "y");
                var previous = positions.put(parseId(entry.get("id").getAsString()), new Position(Math.toIntExact(number(entry, "x")), Math.toIntExact(number(entry, "y"))));
                if (previous != null) throw new IllegalArgumentException("Duplicate position.");
            }
            return new QuestWorkshopDraft(UUID.fromString(root.get("id").getAsString()), number(root, "revision"), sources, positions);
        } catch (RuntimeException exception) { throw new IOException("Invalid or unsupported Workshop draft; preserve the stored file.", exception); }
    }

    private static JsonObject json(UUID id, long revision, List<QuestSourceDocument> sources, Map<ResourceLocation, Position> positions) {
        var root = new JsonObject(); root.addProperty("formatVersion", 1); root.addProperty("id", id.toString()); root.addProperty("revision", revision);
        var documents = new JsonArray();
        for (var source : sources) {
            var entry = new JsonObject(); entry.addProperty("kind", source.kind().name()); entry.addProperty("id", source.id().toString()); entry.add("content", source.content()); documents.add(entry);
        }
        root.add("documents", documents); var layout = new JsonArray();
        positions.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            var node = new JsonObject(); node.addProperty("id", entry.getKey().toString()); node.addProperty("x", entry.getValue().x()); node.addProperty("y", entry.getValue().y()); layout.add(node);
        });
        root.add("positions", layout); return root;
    }
    static byte[] bytes(JsonObject root) { return GSON.toJson(root).getBytes(StandardCharsets.UTF_8); }
    static void fields(JsonObject object, String... expected) {
        if (!object.keySet().equals(Set.of(expected))) throw new IllegalArgumentException("Unknown or missing Workshop fields.");
    }
    static long number(JsonObject object, String key) {
        var value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected an integer.");
        return value.getAsBigDecimal().longValueExact();
    }
    static ResourceLocation parseId(String value) {
        if (!value.contains(":") || value.length() > 256) throw new IllegalArgumentException("Expected a namespaced identifier.");
        var id = ResourceLocation.parse(value); checkId(id); return id;
    }
    static void checkId(ResourceLocation id) {
        if (id == null || id.toString().length() > 256) throw new IllegalArgumentException("Invalid Workshop identifier.");
    }
    static void checkContent(JsonObject content) {
        try { new QuestDefinitionCodec().readBounded(new ByteArrayInputStream(bytes(content))); }
        catch (IOException exception) { throw new IllegalArgumentException("Definition is too large or deeply nested.", exception); }
    }
}
