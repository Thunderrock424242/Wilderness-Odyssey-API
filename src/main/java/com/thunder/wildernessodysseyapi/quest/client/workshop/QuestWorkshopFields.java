package com.thunder.wildernessodysseyapi.quest.client.workshop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument.Kind;
import net.minecraft.resources.ResourceLocation;

/** Typed form conversion uses exactly the schema-v1 fields; registry availability remains server validated. */
public final class QuestWorkshopFields {
    private QuestWorkshopFields() { }
    public static java.util.Optional<JsonObject> entry(JsonObject content, String collection, int index) {
        var value = content.get(collection);
        if (value == null || !value.isJsonArray() || index < 0 || index >= value.getAsJsonArray().size()) return java.util.Optional.empty();
        var entry = value.getAsJsonArray().get(index);
        return entry.isJsonObject() ? java.util.Optional.of(entry.getAsJsonObject().deepCopy()) : java.util.Optional.empty();
    }
    public static JsonObject removeEntry(JsonObject content, String collection, int index) {
        var result = content.deepCopy(); var value = result.get(collection);
        if (value == null || !value.isJsonArray() || index < 0 || index >= value.getAsJsonArray().size()) throw new IllegalArgumentException("The entry no longer exists.");
        var array = value.getAsJsonArray(); var removed = array.remove(index);
        String removedId = removed.isJsonObject() ? com.thunder.wildernessodysseyapi.quest.workshop.QuestWorkshopDocuments.text(removed.getAsJsonObject(), "id", "") : "";
        if (collection.equals("objectives") && !removedId.isEmpty()) for (var remaining : array) if (remaining.isJsonObject()) {
            var objective = remaining.getAsJsonObject(); var dependencies = objective.get("dependsOn");
            if (dependencies == null || !dependencies.isJsonArray()) continue;
            var repaired = new JsonArray(); for (var dependency : dependencies.getAsJsonArray())
                if (!dependency.isJsonPrimitive() || !dependency.getAsString().equals(removedId)) repaired.add(dependency);
            objective.add("dependsOn", repaired);
        }
        return result;
    }
    public static JsonObject basics(Kind kind, JsonObject original, String title, String description, String icon) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Enter a title.");
        var result = original.deepCopy(); result.addProperty("title", title);
        if (kind != Kind.CHAPTER) result.addProperty("description", description);
        if (kind == Kind.QUEST) result.addProperty("icon", id(icon).toString()); return result;
    }
    public static ResourceLocation id(String text) {
        if (!text.contains(":")) throw new IllegalArgumentException("Use a namespaced identifier, such as minecraft:book.");
        var id = ResourceLocation.tryParse(text.strip());
        if (id == null) throw new IllegalArgumentException("Identifier must use lowercase namespaced letters, numbers, /, _ or -."); return id;
    }
    public static long number(String text, long min, long max) {
        try { long value = Long.parseLong(text.strip()); if (value >= min && value <= max) return value; }
        catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Enter a whole number from " + min + " to " + max + ".");
    }
    public static JsonArray ids(String text) {
        var array = new JsonArray(); if (!text.isBlank()) for (var token : text.split(",", -1)) array.add(id(token.strip()).toString()); return array;
    }
    public static JsonObject objective(String identifier, String type, String goal, String targets, String dependsOn) {
        var json = new JsonObject(); json.addProperty("id", id(identifier).toString()); json.addProperty("type", type);
        json.addProperty("goal", number(goal, 1, type.equals("dimension") || type.equals("lore") ? 1 : Long.MAX_VALUE)); json.add("dependsOn", ids(dependsOn));
        switch (type) {
            case "possession" -> {
                var items = new JsonArray(); var tags = new JsonArray();
                if (targets.isBlank()) throw new IllegalArgumentException("Add at least one item or #tag.");
                for (var token : targets.split(",", -1)) { String value = token.strip(); boolean tag = value.startsWith("#"); (tag ? tags : items).add(id(tag ? value.substring(1) : value).toString()); }
                json.add("items", items); json.add("tags", tags);
            }
            case "dimension" -> json.addProperty("dimension", id(targets).toString());
            case "lore" -> json.addProperty("lore", id(targets).toString());
            case "custom_event" -> json.addProperty("producer", id(targets).toString());
            default -> throw new IllegalArgumentException("Unsupported objective provider.");
        }
        return json;
    }
    public static JsonObject reward(String identifier, String type, String target, String amount) {
        var json = new JsonObject(); json.addProperty("id", id(identifier).toString()); json.addProperty("type", type);
        switch (type) {
            case "item" -> { json.addProperty("item", id(target).toString()); json.addProperty("count", number(amount, 1, 64)); }
            case "xp" -> json.addProperty("amount", number(amount, 1, 1_000_000));
            case "lore" -> json.addProperty("lore", id(target).toString());
            default -> throw new IllegalArgumentException("Unsupported reward type.");
        }
        return json;
    }
    static String join(JsonObject json, String field) {
        if (!json.has(field) || !json.get(field).isJsonArray()) return "";
        var values = new java.util.ArrayList<String>(); for (var value : json.getAsJsonArray(field)) if (value.isJsonPrimitive()) values.add(value.getAsString()); return String.join(", ", values);
    }
}
