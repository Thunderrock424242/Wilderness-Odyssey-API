package com.thunder.wildernessodysseyapi.quest.workshop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument;
import com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument.Kind;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** Pure native-authoring operations repair graph membership without activating content. */
public final class QuestWorkshopDocuments {
    private QuestWorkshopDocuments() { }
    public static QuestWorkshopEdit replace(QuestWorkshopDraft draft, Kind kind, ResourceLocation id, JsonObject content) {
        required(draft, kind, id);
        return new QuestWorkshopEdit(List.of(new QuestWorkshopEdit.Change(kind, id, content)), List.of());
    }
    public static QuestWorkshopEdit createChapter(QuestWorkshopDraft draft, ResourceLocation id, String title) {
        absent(draft, Kind.CHAPTER, id); var changes = new ArrayList<QuestWorkshopEdit.Change>();
        var campaign = draft.sources().stream().filter(source -> source.kind() == Kind.CAMPAIGN).findFirst();
        ResourceLocation campaignId = campaign.map(QuestSourceDocument::id).orElse(ResourceLocation.parse("wildernessodysseyapi:campaign"));
        var content = campaign.map(QuestSourceDocument::content).orElseGet(() -> basic("Expeditions"));
        var chapters = array(content, "chapters"); chapters.add(id.toString()); content.add("chapters", chapters);
        changes.add(new QuestWorkshopEdit.Change(Kind.CAMPAIGN, campaignId, content));
        var chapter = basic(title); chapter.addProperty("campaign", campaignId.toString()); chapter.add("quests", new JsonArray());
        changes.add(new QuestWorkshopEdit.Change(Kind.CHAPTER, id, chapter)); return new QuestWorkshopEdit(changes, List.of());
    }
    public static QuestWorkshopEdit createQuest(QuestWorkshopDraft draft, ResourceLocation chapterId, ResourceLocation id, String title) {
        absent(draft, Kind.QUEST, id); var chapter = required(draft, Kind.CHAPTER, chapterId).content();
        var ids = array(chapter, "quests"); ids.add(id.toString()); chapter.add("quests", ids);
        var quest = basic(title); quest.addProperty("chapter", chapterId.toString()); quest.addProperty("optional", true); quest.addProperty("icon", "minecraft:book");
        var prerequisites = new JsonObject(); prerequisites.addProperty("mode", "all"); prerequisites.add("quests", new JsonArray()); quest.add("prerequisites", prerequisites);
        var objectives = new JsonArray(); var objective = new JsonObject(); objective.addProperty("id", id.getNamespace() + ":" + id.getPath() + "/supplies");
        objective.addProperty("type", "possession"); objective.addProperty("goal", 1); var items = new JsonArray(); items.add("minecraft:oak_log"); objective.add("items", items); objectives.add(objective);
        quest.add("objectives", objectives); quest.add("rewards", new JsonArray());
        int index = (int) draft.sources().stream().filter(source -> source.kind() == Kind.QUEST).count();
        return new QuestWorkshopEdit(List.of(new QuestWorkshopEdit.Change(Kind.CHAPTER, chapterId, chapter), new QuestWorkshopEdit.Change(Kind.QUEST, id, quest)),
                List.of(new QuestWorkshopEdit.Move(id, new QuestWorkshopDraft.Position(index % 6 * 150, index / 6 * 75))));
    }
    public static QuestWorkshopEdit move(QuestWorkshopDraft draft, ResourceLocation id, int x, int y) {
        required(draft, Kind.QUEST, id);
        return new QuestWorkshopEdit(List.of(), List.of(new QuestWorkshopEdit.Move(id, new QuestWorkshopDraft.Position(x, y))));
    }
    public static QuestWorkshopEdit link(QuestWorkshopDraft draft, ResourceLocation prerequisite, ResourceLocation dependent, boolean add) {
        required(draft, Kind.QUEST, prerequisite); var content = required(draft, Kind.QUEST, dependent).content();
        if (prerequisite.equals(dependent)) throw new IllegalArgumentException("A quest cannot depend on itself.");
        var dependencies = content.has("prerequisites") ? content.getAsJsonObject("prerequisites") : new JsonObject();
        if (!dependencies.has("mode")) dependencies.addProperty("mode", "all");
        var ids = array(dependencies, "quests"); var changed = new JsonArray();
        for (var element : ids) if (!element.getAsString().equals(prerequisite.toString())) changed.add(element);
        if (add) changed.add(prerequisite.toString()); dependencies.add("quests", changed); content.add("prerequisites", dependencies);
        return replace(draft, Kind.QUEST, dependent, content);
    }
    public static QuestWorkshopEdit delete(QuestWorkshopDraft draft, Kind kind, ResourceLocation id) {
        required(draft, kind, id);
        if (kind == Kind.CAMPAIGN) throw new IllegalArgumentException("Delete chapters instead of the campaign.");
        var deleted = new HashSet<ResourceLocation>(); if (kind == Kind.QUEST) deleted.add(id);
        else for (var source : draft.sources()) if (source.kind() == Kind.QUEST && source.content().has("chapter")
                && source.content().get("chapter").getAsString().equals(id.toString())) deleted.add(source.id());
        var changes = new ArrayList<QuestWorkshopEdit.Change>(); var moves = new ArrayList<QuestWorkshopEdit.Move>();
        for (var source : draft.sources()) {
            if (source.kind() == kind && source.id().equals(id) || source.kind() == Kind.QUEST && deleted.contains(source.id())) {
                changes.add(new QuestWorkshopEdit.Change(source.kind(), source.id(), null));
                if (source.kind() == Kind.QUEST) moves.add(new QuestWorkshopEdit.Move(source.id(), null));
                continue;
            }
            var content = source.content(); var before = content.deepCopy();
            if (source.kind() == Kind.CAMPAIGN && kind == Kind.CHAPTER) content.add("chapters", without(array(content, "chapters"), Set.of(id)));
            if (source.kind() == Kind.CHAPTER) content.add("quests", without(array(content, "quests"), deleted));
            if (source.kind() == Kind.QUEST && content.has("prerequisites")) {
                var prerequisites = content.getAsJsonObject("prerequisites"); prerequisites.add("quests", without(array(prerequisites, "quests"), deleted));
            }
            if (!before.equals(content)) changes.add(new QuestWorkshopEdit.Change(source.kind(), source.id(), content));
        }
        return new QuestWorkshopEdit(changes, moves);
    }
    public static QuestWorkshopEdit duplicate(QuestWorkshopDraft draft, Kind kind, ResourceLocation id, ResourceLocation copyId) {
        var original = required(draft, kind, id); absent(draft, kind, copyId);
        if (kind == Kind.CAMPAIGN) throw new IllegalArgumentException("Duplicate a chapter or quest.");
        var content = original.content(); content.addProperty("title", text(content, "title", "Untitled") + " (copy)");
        if (kind == Kind.QUEST) {
            var chapterId = ResourceLocation.parse(content.get("chapter").getAsString());
            var chapter = required(draft, Kind.CHAPTER, chapterId).content(); var ids = array(chapter, "quests"); ids.add(copyId.toString()); chapter.add("quests", ids);
            var position = draft.positions().getOrDefault(id, new QuestWorkshopDraft.Position(0, 0));
            return new QuestWorkshopEdit(List.of(new QuestWorkshopEdit.Change(kind, copyId, content), new QuestWorkshopEdit.Change(Kind.CHAPTER, chapterId, chapter)),
                    List.of(new QuestWorkshopEdit.Move(copyId, new QuestWorkshopDraft.Position(Math.min(100_000, position.x() + 30), Math.min(100_000, position.y() + 30)))));
        }
        var changes = new ArrayList<QuestWorkshopEdit.Change>(); var moves = new ArrayList<QuestWorkshopEdit.Move>(); var mapping = new LinkedHashMap<ResourceLocation, ResourceLocation>();
        for (var element : array(content, "quests")) {
            var old = ResourceLocation.parse(element.getAsString());
            mapping.put(old, unused(draft, Kind.QUEST, copyId.getNamespace(), copyId.getPath() + "/" + old.getNamespace() + "/" + old.getPath(), mapping.values()));
        }
        var copiedIds = new JsonArray(); mapping.values().forEach(copy -> copiedIds.add(copy.toString())); content.add("quests", copiedIds);
        changes.add(new QuestWorkshopEdit.Change(kind, copyId, content));
        var campaignId = ResourceLocation.parse(content.get("campaign").getAsString()); var campaign = required(draft, Kind.CAMPAIGN, campaignId).content();
        var chapters = array(campaign, "chapters"); chapters.add(copyId.toString()); campaign.add("chapters", chapters); changes.add(new QuestWorkshopEdit.Change(Kind.CAMPAIGN, campaignId, campaign));
        for (var entry : mapping.entrySet()) {
            var quest = required(draft, Kind.QUEST, entry.getKey()).content(); quest.addProperty("chapter", copyId.toString());
            if (quest.has("prerequisites")) {
                var prerequisites = quest.getAsJsonObject("prerequisites"); var ids = new JsonArray();
                for (var element : array(prerequisites, "quests")) { var old = ResourceLocation.parse(element.getAsString()); ids.add(mapping.getOrDefault(old, old).toString()); }
                prerequisites.add("quests", ids);
            }
            changes.add(new QuestWorkshopEdit.Change(Kind.QUEST, entry.getValue(), quest)); var position = draft.positions().getOrDefault(entry.getKey(), new QuestWorkshopDraft.Position(0, 0));
            moves.add(new QuestWorkshopEdit.Move(entry.getValue(), new QuestWorkshopDraft.Position(Math.min(100_000, position.x() + 30), Math.min(100_000, position.y() + 30))));
        }
        return new QuestWorkshopEdit(changes, moves);
    }
    public static ResourceLocation unused(QuestWorkshopDraft draft, Kind kind, String namespace, String stem, Collection<ResourceLocation> reserved) {
        for (int number = 1; number <= 4000; number++) {
            var id = ResourceLocation.parse(namespace + ":" + stem + "_" + number);
            if (draft.source(kind, id).isEmpty() && !reserved.contains(id)) return id;
        }
        throw new IllegalArgumentException("No unused identifier is available.");
    }
    private static QuestSourceDocument required(QuestWorkshopDraft draft, Kind kind, ResourceLocation id) {
        return draft.source(kind, id).orElseThrow(() -> new IllegalArgumentException("The selected definition no longer exists."));
    }
    private static void absent(QuestWorkshopDraft draft, Kind kind, ResourceLocation id) {
        if (draft.source(kind, id).isPresent()) throw new IllegalArgumentException("That identifier already exists.");
    }
    private static JsonObject basic(String title) {
        var content = new JsonObject(); content.addProperty("schemaVersion", 1); content.addProperty("definitionVersion", 1); content.addProperty("title", title); return content;
    }
    private static JsonArray array(JsonObject content, String field) { return content.has(field) ? content.getAsJsonArray(field).deepCopy() : new JsonArray(); }
    private static JsonArray without(JsonArray ids, Set<ResourceLocation> removed) {
        var result = new JsonArray(); for (var element : ids) if (!removed.contains(ResourceLocation.parse(element.getAsString()))) result.add(element); return result;
    }
    public static String text(JsonObject content, String field, String fallback) {
        return content.has(field) && content.get(field).isJsonPrimitive() ? content.get(field).getAsString() : fallback;
    }
}
