package com.thunder.wildernessodysseyapi.quest.client.workshop;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument.Kind;
import com.thunder.wildernessodysseyapi.quest.workshop.QuestWorkshopDocuments;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** Typed local form transaction. Apply records one undoable patch; cancel does not change the shared model. */
final class QuestWorkshopPropertiesScreen extends Screen {
    private enum Page { BASICS, RULES, OBJECTIVES, REWARDS }
    private record Label(Component text, int x, int y) { }
    private final QuestWorkshopScreen parent;
    private final QuestWorkshopModel model;
    private final Kind kind;
    private final ResourceLocation id;
    private JsonObject content;
    private final Map<String, String> raw = new HashMap<>();
    private final List<Label> labels = new ArrayList<>();
    private Page page = Page.BASICS;
    private int entry;
    private String error = "";
    private boolean changed;

    QuestWorkshopPropertiesScreen(QuestWorkshopScreen parent, QuestWorkshopModel model, Kind kind, ResourceLocation id) {
        super(QuestWorkshopScreen.tr("properties")); this.parent = parent; this.model = model; this.kind = kind; this.id = id;
        content = model.draft().source(kind, id).orElseThrow().content();
    }
    @Override protected void init() {
        labels.clear(); int x = 10, size = width - 20;
        if (kind == Kind.QUEST) {
            int tab = (size - 6) / 4;
            for (var value : Page.values()) addRenderableWidget(Button.builder(QuestWorkshopScreen.tr(value.name().toLowerCase(Locale.ROOT)), button -> {
                if (commitForm()) { page = value; entry = 0; raw.clear(); rebuildWidgets(); }
            }).bounds(x + value.ordinal() * (tab + 2), 28, tab, 18).build());
        }
        switch (page) {
            case BASICS -> {
                field("title", "name", x, 66, size, 256, text(content, "title", ""));
                if (kind == Kind.QUEST) field("icon", "icon", x, 103, size, 256, text(content, "icon", "minecraft:book"));
                if (kind == Kind.CHAPTER) {
                    field("requiredMods", "required_mods", x, 103, size, 4096, QuestWorkshopFields.join(content, "requiredMods"));
                    field("optionalMods", "optional_mods", x, 140, size, 4096, QuestWorkshopFields.join(content, "optionalMods"));
                    break;
                }
                labels.add(new Label(QuestWorkshopScreen.tr("description"), x, 127));
                var description = addRenderableWidget(new MultiLineEditBox(font, x, 139, size, Math.max(28, height - 190), Component.empty(), QuestWorkshopScreen.tr("description")));
                description.setCharacterLimit(4096); description.setValue(raw.computeIfAbsent("description", ignored -> text(content, "description", "")));
                description.setValueListener(value -> { if (!value.equals(raw.get("description"))) changed = true; raw.put("description", value); });
            }
            case RULES -> {
                var prerequisites = content.has("prerequisites") && content.get("prerequisites").isJsonObject() ? content.getAsJsonObject("prerequisites") : new JsonObject();
                field("prerequisites", "prerequisites", x, 66, size, 65_536, QuestWorkshopFields.join(prerequisites, "quests"));
                field("cooldownTicks", "cooldown", x, 103, size / 2 - 3, 20, text(content, "cooldownTicks", "0"));
                toggle("mode", x + size / 2 + 3, 103, size / 2 - 3, List.of("all", "any"), text(prerequisites, "mode", "all"));
                int part = (size - 4) / 3;
                toggle("optional", x, 140, part, List.of("true", "false"), text(content, "optional", "true"));
                toggle("hidden", x + part + 2, 140, part, List.of("false", "true"), text(content, "hidden", "false"));
                toggle("repeatable", x + 2 * (part + 2), 140, part, List.of("false", "true"), text(content, "repeatable", "false"));
                labels.add(new Label(QuestWorkshopScreen.tr("rules_help"), x, 168));
            }
            case OBJECTIVES, REWARDS -> entryForm(x, size);
        }
        addRenderableWidget(Button.builder(QuestWorkshopScreen.tr("apply"), button -> {
            if (!commitForm()) return;
            try { model.edit(QuestWorkshopDocuments.replace(model.draft(), kind, id, content)); minecraft.setScreen(parent); }
            catch (IllegalArgumentException exception) { error = exception.getMessage(); }
        }).bounds(x, height - 28, size / 2 - 3, 20).build());
        addRenderableWidget(Button.builder(QuestWorkshopScreen.tr("cancel"), button -> onClose()).bounds(x + size / 2 + 3, height - 28, size / 2 - 3, 20).build());
    }
    private void field(String key, String label, int x, int y, int size, int limit, String fallback) {
        labels.add(new Label(QuestWorkshopScreen.tr(label), x, y - 12));
        var field = addRenderableWidget(new EditBox(font, x, y, size, 18, QuestWorkshopScreen.tr(label)));
        field.setMaxLength(limit); field.setValue(raw.computeIfAbsent(key, ignored -> fallback));
        field.setResponder(value -> { if (!value.equals(raw.get(key))) changed = true; raw.put(key, value); });
    }
    private void toggle(String key, int x, int y, int size, List<String> values, String fallback) {
        raw.putIfAbsent(key, fallback);
        addRenderableWidget(Button.builder(QuestWorkshopScreen.tr("value", QuestWorkshopScreen.tr(key), raw.get(key)), button -> {
            raw.put(key, values.get(Math.floorMod(values.indexOf(raw.get(key)) + 1, values.size()))); changed = true;
            button.setMessage(QuestWorkshopScreen.tr("value", QuestWorkshopScreen.tr(key), raw.get(key)));
        }).bounds(x, y, size, 18).build());
    }
    private String collection() { return page == Page.OBJECTIVES ? "objectives" : "rewards"; }
    private JsonArray entries() {
        if (!content.has(collection()) || !content.get(collection()).isJsonArray()) content.add(collection(), new JsonArray());
        return content.getAsJsonArray(collection());
    }
    private void entryForm(int x, int size) {
        var array = entries(); entry = Math.clamp(entry, 0, Math.max(0, array.size() - 1)); int part = (size - 6) / 4;
        addRenderableWidget(Button.builder(Component.literal("<"), button -> navigate(-1)).bounds(x, 52, part, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> navigate(1)).bounds(x + part + 2, 52, part, 18).build());
        addRenderableWidget(Button.builder(QuestWorkshopScreen.tr("add"), button -> {
            if (!commitForm()) return;
            int limit = page == Page.OBJECTIVES ? 32 : 16;
            if (array.size() >= limit) { error = "A quest supports at most " + limit + " entries of this kind."; return; }
            var used = new HashSet<String>(); for (var element : array) if (element.isJsonObject()) used.add(text(element.getAsJsonObject(), "id", ""));
            String identifier; int number = 1; do { identifier = id + "/" + collection() + "_" + number++; } while (used.contains(identifier));
            array.add(page == Page.OBJECTIVES ? QuestWorkshopFields.objective(identifier, "possession", "1", "minecraft:oak_log", "") : QuestWorkshopFields.reward(identifier, "item", "minecraft:book", "1"));
            entry = array.size() - 1; changed = true; raw.clear(); rebuildWidgets();
        }).bounds(x + 2 * (part + 2), 52, part, 18).build());
        addRenderableWidget(Button.builder(QuestWorkshopScreen.tr("remove"), button -> {
            if (array.isEmpty()) return; content = QuestWorkshopFields.removeEntry(content, collection(), entry);
            entry = Math.max(0, entry - 1); changed = true; raw.clear(); rebuildWidgets();
        }).bounds(x + 3 * (part + 2), 52, part, 18).build());
        if (array.isEmpty()) { labels.add(new Label(QuestWorkshopScreen.tr("no_entries"), x, 90)); return; }
        var editable = QuestWorkshopFields.entry(content, collection(), entry);
        if (editable.isEmpty()) { labels.add(new Label(QuestWorkshopScreen.tr("invalid_entry", entry + 1, array.size()), x, 90)); return; }
        var value = editable.get(); String type = raw.computeIfAbsent("type", ignored -> text(value, "type", page == Page.OBJECTIVES ? "possession" : "item"));
        var types = page == Page.OBJECTIVES ? List.of("possession", "dimension", "lore", "custom_event") : List.of("item", "xp", "lore");
        addRenderableWidget(Button.builder(QuestWorkshopScreen.tr("type", type, entry + 1, array.size()), button -> {
            try {
            // Changing a provider is an explicit replacement of that entry's typed parameters.
            String next = types.get(Math.floorMod(types.indexOf(type) + 1, types.size())); String identifier = text(value, "id", id + "/entry_" + (entry + 1));
            String target = switch (next) { case "dimension" -> "minecraft:overworld"; case "lore" -> "wildernessodysseyapi:lore_001"; case "custom_event" -> "wildernessodysseyapi:event"; default -> "minecraft:oak_log"; };
            array.set(entry, page == Page.OBJECTIVES ? QuestWorkshopFields.objective(identifier, next, "1", target, raw.getOrDefault("dependsOn", QuestWorkshopFields.join(value, "dependsOn"))) : QuestWorkshopFields.reward(identifier, next, target, "1"));
            changed = true; raw.clear(); rebuildWidgets();
            } catch (IllegalArgumentException exception) { error = exception.getMessage(); }
        }).bounds(x, 74, size, 18).build());
        labels.add(new Label(Component.literal(text(value, "id", "")), x, 97));
        String target = switch (type) {
            case "possession" -> { String items = QuestWorkshopFields.join(value, "items"), tags = QuestWorkshopFields.join(value, "tags"); String tagged = tags.isBlank() ? "" : "#" + tags.replace(", ", ", #"); yield items.isBlank() ? tagged : tagged.isBlank() ? items : items + ", " + tagged; }
            case "dimension" -> text(value, "dimension", ""); case "lore" -> text(value, "lore", ""); case "custom_event" -> text(value, "producer", ""); case "item" -> text(value, "item", ""); default -> "";
        };
        field("target", type.equals("possession") ? "items_tags" : "target", x, 122, size, 65_536, target);
        if (page == Page.OBJECTIVES) {
            field("goal", "goal", x, 161, size / 3 - 2, 20, text(value, "goal", "1"));
            field("dependsOn", "depends_on", x + size / 3 + 2, 161, size - size / 3 - 2, 8192, QuestWorkshopFields.join(value, "dependsOn"));
        } else field("amount", "amount", x, 161, size, 8, text(value, type.equals("item") ? "count" : "amount", "1"));
    }
    private void navigate(int delta) { if (commitForm() && !entries().isEmpty()) { entry = Math.floorMod(entry + delta, entries().size()); raw.clear(); rebuildWidgets(); } }
    private boolean commitForm() {
        try {
            switch (page) {
                case BASICS -> {
                    content = QuestWorkshopFields.basics(kind, content, raw.get("title"), raw.get("description"), raw.get("icon"));
                    if (kind == Kind.CHAPTER) for (var field : List.of("requiredMods", "optionalMods")) {
                        var mods = new JsonArray(); if (!raw.get(field).isBlank()) for (var mod : raw.get(field).split(",", -1)) mods.add(mod.strip()); content.add(field, mods);
                    }
                }
                case RULES -> {
                    var prerequisites = new JsonObject(); prerequisites.addProperty("mode", raw.get("mode")); prerequisites.add("quests", QuestWorkshopFields.ids(raw.get("prerequisites"))); content.add("prerequisites", prerequisites);
                    for (var flag : List.of("optional", "hidden", "repeatable")) content.addProperty(flag, Boolean.parseBoolean(raw.get(flag)));
                    long cooldown = QuestWorkshopFields.number(raw.get("cooldownTicks"), 0, Long.MAX_VALUE);
                    if (!Boolean.parseBoolean(raw.get("repeatable")) && cooldown != 0) throw new IllegalArgumentException("Enable repeatable before adding a cooldown."); content.addProperty("cooldownTicks", cooldown);
                }
                case OBJECTIVES, REWARDS -> {
                    var array = entries(); var editable = QuestWorkshopFields.entry(content, collection(), entry);
                    if (editable.isPresent()) {
                        String identifier = text(editable.get(), "id", "");
                        array.set(entry, page == Page.OBJECTIVES ? QuestWorkshopFields.objective(identifier, raw.get("type"), raw.get("goal"), raw.get("target"), raw.get("dependsOn")) : QuestWorkshopFields.reward(identifier, raw.get("type"), raw.get("target"), raw.get("amount")));
                    }
                }
            }
            error = ""; return true;
        } catch (IllegalArgumentException exception) { error = exception.getMessage(); return false; }
    }
    private static String text(JsonObject json, String key, String fallback) { return QuestWorkshopDocuments.text(json, key, fallback); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, QuestWorkshopScreen.LEATHER);
        graphics.drawString(font, font.plainSubstrByWidth(id.toString(), width - 20), 10, 10, QuestWorkshopScreen.PAPER, false);
        for (var label : labels) graphics.drawString(font, font.plainSubstrByWidth(label.text().getString(), width - label.x() - 10), label.x(), label.y(), QuestWorkshopScreen.PAPER, false);
        graphics.drawString(font, font.plainSubstrByWidth(error, width - 20), 10, height - 43, 0xFFFFBC80, false); super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void onClose() {
        if (!changed) { minecraft.setScreen(parent); return; }
        minecraft.setScreen(new ConfirmScreen(discard -> minecraft.setScreen(discard ? parent : this), QuestWorkshopScreen.tr("cancel"), QuestWorkshopScreen.tr("discard_form")));
    }
    @Override public boolean isPauseScreen() { return false; }
}
