package com.thunder.wildernessodysseyapi.quest.client.workshop;

import com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument.Kind;
import com.thunder.wildernessodysseyapi.quest.workshop.QuestWorkshopDocuments;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Identity is chosen at creation and remains stable after references exist. */
final class QuestWorkshopCreateScreen extends Screen {
    private final QuestWorkshopScreen parent;
    private final QuestWorkshopModel model;
    private final Kind kind;
    private final ResourceLocation original;
    private String identifier, name = "", error = "";
    QuestWorkshopCreateScreen(QuestWorkshopScreen parent, QuestWorkshopModel model, Kind kind, ResourceLocation original) {
        super(QuestWorkshopScreen.tr(original == null ? "create" : "duplicate")); this.parent = parent; this.model = model; this.kind = kind; this.original = original;
        identifier = QuestWorkshopDocuments.unused(model.draft(), kind, original == null ? "wildernessodysseyapi" : original.getNamespace(), original == null ? kind.name().toLowerCase(java.util.Locale.ROOT) : original.getPath() + "_copy", List.of()).toString();
    }
    @Override protected void init() {
        int x = Math.max(10, width / 2 - 140), size = Math.min(280, width - 20);
        var id = addRenderableWidget(new EditBox(font, x, 60, size, 20, QuestWorkshopScreen.tr("id"))); id.setMaxLength(256); id.setValue(identifier); id.setResponder(value -> identifier = value);
        if (original == null) { var title = addRenderableWidget(new EditBox(font, x, 106, size, 20, QuestWorkshopScreen.tr("name"))); title.setMaxLength(256); title.setValue(name); title.setResponder(value -> name = value); }
        addRenderableWidget(Button.builder(QuestWorkshopScreen.tr("apply"), button -> {
            try {
                var parsed = QuestWorkshopFields.id(identifier);
                if (original != null) model.edit(QuestWorkshopDocuments.duplicate(model.draft(), kind, original, parsed));
                else { if (name.isBlank()) throw new IllegalArgumentException("Enter a title."); model.edit(kind == Kind.CHAPTER ? QuestWorkshopDocuments.createChapter(model.draft(), parsed, name) : QuestWorkshopDocuments.createQuest(model.draft(), model.chapter, parsed, name)); }
                if (kind == Kind.CHAPTER) { model.chapter = parsed; model.selected = null; } else model.selected = parsed; onClose();
            } catch (IllegalArgumentException exception) { error = exception.getMessage(); }
        }).bounds(x, height - 30, size / 2 - 3, 20).build());
        addRenderableWidget(Button.builder(QuestWorkshopScreen.tr("cancel"), button -> onClose()).bounds(x + size / 2 + 3, height - 30, size / 2 - 3, 20).build());
        setInitialFocus(id);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, QuestWorkshopScreen.LEATHER); graphics.drawCenteredString(font, title, width / 2, 12, QuestWorkshopScreen.PAPER);
        int x = Math.max(10, width / 2 - 140); graphics.drawString(font, QuestWorkshopScreen.tr("id"), x, 45, QuestWorkshopScreen.PAPER, false);
        if (original == null) graphics.drawString(font, QuestWorkshopScreen.tr("name"), x, 91, QuestWorkshopScreen.PAPER, false);
        graphics.drawString(font, font.plainSubstrByWidth(error, width - 20), x, height - 46, 0xFFFFBC80, false); super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
