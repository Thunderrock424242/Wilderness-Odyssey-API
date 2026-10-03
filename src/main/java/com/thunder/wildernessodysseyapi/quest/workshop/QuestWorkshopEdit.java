package com.thunder.wildernessodysseyapi.quest.workshop;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.quest.definition.QuestDefinitionCodec;
import com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument.Kind;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.*;

/** A bounded atomic authoring patch. Null content/position means deletion; no live state is accepted. */
public record QuestWorkshopEdit(List<Change> changes, List<Move> moves) {
    public record Change(Kind kind, ResourceLocation id, JsonObject content) {
        public Change {
            Objects.requireNonNull(kind); QuestWorkshopDraft.checkId(id);
            if (content != null) { QuestWorkshopDraft.checkContent(content); content = content.deepCopy(); }
        }
        @Override public JsonObject content() { return content == null ? null : content.deepCopy(); }
    }
    public record Move(ResourceLocation id, QuestWorkshopDraft.Position position) {
        public Move { QuestWorkshopDraft.checkId(id); }
    }
    public QuestWorkshopEdit {
        changes = List.copyOf(changes); moves = List.copyOf(moves);
        if (changes.size() > 2129 || moves.size() > 2000) throw new IllegalArgumentException("Edit exceeds its source/position limit.");
        var keys = new HashSet<QuestWorkshopDraft.Key>();
        for (var change : changes) if (!keys.add(new QuestWorkshopDraft.Key(change.kind(), change.id()))) throw new IllegalArgumentException("Duplicate edit identity.");
        var ids = new HashSet<ResourceLocation>();
        for (var move : moves) if (!ids.add(move.id())) throw new IllegalArgumentException("Duplicate moved node.");
    }
    public byte[] encode() {
        var root = new JsonObject(); var documents = new JsonArray(); var positions = new JsonArray();
        for (var change : changes) {
            var entry = new JsonObject(); entry.addProperty("kind", change.kind().name()); entry.addProperty("id", change.id().toString());
            entry.add("content", change.content() == null ? JsonNull.INSTANCE : change.content()); documents.add(entry);
        }
        for (var move : moves) {
            var entry = new JsonObject(); entry.addProperty("id", move.id().toString());
            if (move.position() == null) { entry.add("x", JsonNull.INSTANCE); entry.add("y", JsonNull.INSTANCE); }
            else { entry.addProperty("x", move.position().x()); entry.addProperty("y", move.position().y()); }
            positions.add(entry);
        }
        root.add("changes", documents); root.add("moves", positions); byte[] bytes = QuestWorkshopDraft.bytes(root);
        if (bytes.length > QuestWorkshopDraft.MAX_BYTES) throw new IllegalArgumentException("Edit exceeds 16 MiB.");
        return bytes;
    }
    public static QuestWorkshopEdit decode(byte[] bytes) throws IOException {
        try {
            var root = new QuestDefinitionCodec().readProjection(bytes); QuestWorkshopDraft.fields(root, "changes", "moves");
            var documents = root.getAsJsonArray("changes"); var positions = root.getAsJsonArray("moves");
            if (documents.size() > 2129 || positions.size() > 2000) throw new IllegalArgumentException("Edit exceeds its limit.");
            var changes = new ArrayList<Change>(); var moves = new ArrayList<Move>();
            for (var element : documents) {
                var entry = element.getAsJsonObject(); QuestWorkshopDraft.fields(entry, "kind", "id", "content");
                changes.add(new Change(Kind.valueOf(entry.get("kind").getAsString()), QuestWorkshopDraft.parseId(entry.get("id").getAsString()),
                        entry.get("content").isJsonNull() ? null : entry.getAsJsonObject("content")));
            }
            for (var element : positions) {
                var entry = element.getAsJsonObject(); QuestWorkshopDraft.fields(entry, "id", "x", "y");
                boolean removed = entry.get("x").isJsonNull();
                if (removed != entry.get("y").isJsonNull()) throw new IllegalArgumentException("Partial position.");
                moves.add(new Move(QuestWorkshopDraft.parseId(entry.get("id").getAsString()), removed ? null :
                        new QuestWorkshopDraft.Position(Math.toIntExact(QuestWorkshopDraft.number(entry, "x")), Math.toIntExact(QuestWorkshopDraft.number(entry, "y")))));
            }
            return new QuestWorkshopEdit(changes, moves);
        } catch (RuntimeException exception) { throw new IOException("Invalid Workshop edit.", exception); }
    }
    public static QuestWorkshopEdit difference(QuestWorkshopDraft from, QuestWorkshopDraft to) {
        var keys = new LinkedHashSet<QuestWorkshopDraft.Key>();
        from.sources().forEach(source -> keys.add(new QuestWorkshopDraft.Key(source.kind(), source.id())));
        to.sources().forEach(source -> keys.add(new QuestWorkshopDraft.Key(source.kind(), source.id())));
        var changes = new ArrayList<Change>();
        for (var key : keys) {
            var before = from.source(key.kind(), key.id()).map(source -> source.content()).orElse(null);
            var after = to.source(key.kind(), key.id()).map(source -> source.content()).orElse(null);
            if (!Objects.equals(before, after)) changes.add(new Change(key.kind(), key.id(), after));
        }
        var ids = new LinkedHashSet<>(from.positions().keySet()); ids.addAll(to.positions().keySet()); var moves = new ArrayList<Move>();
        for (var id : ids) if (!Objects.equals(from.positions().get(id), to.positions().get(id))) moves.add(new Move(id, to.positions().get(id)));
        return new QuestWorkshopEdit(changes, moves);
    }
}
