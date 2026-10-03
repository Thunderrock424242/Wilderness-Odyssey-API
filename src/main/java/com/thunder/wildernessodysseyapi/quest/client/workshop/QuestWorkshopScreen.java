package com.thunder.wildernessodysseyapi.quest.client.workshop;

import com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument;
import com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument.Kind;
import com.thunder.wildernessodysseyapi.quest.workshop.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/** Client-only native authoring surface. Widgets never own the draft, revision or undo history. */
public final class QuestWorkshopScreen extends Screen {
    static final int INK = 0xFF38291F, PAPER = 0xFFE4D8BA, LEATHER = 0xFF423128, ACCENT = 0xFF8C662E;
    private record Node(ResourceLocation id, ResourceLocation chapter, String title, List<ResourceLocation> prerequisites, QuestWorkshopDraft.Position fallback) { }
    private final QuestWorkshopModel model;
    private QuestWorkshopLayout layout;
    private QuestWorkshopDraft cached;
    private List<QuestSourceDocument> chapters = List.of();
    private List<Node> nodes = List.of();
    private Map<ResourceLocation, Node> byId = Map.of();
    private EditBox search;
    private Button undo, redo, link;
    private ResourceLocation linking, dragging;
    private QuestWorkshopDraft.Position preview;
    private double grabX, grabY;
    private int chapterOffset;

    public QuestWorkshopScreen(QuestWorkshopModel model) { super(tr("title")); this.model = model; }
    static Component tr(String key, Object... values) { return Component.translatable("screen.wildernessodysseyapi.quest_workshop." + key, values); }
    @Override protected void init() {
        layout = QuestWorkshopLayout.create(width, height);
        int buttonWidth = (width - 17) / 6;
        action("chapter", 0, 0, buttonWidth, () -> create(Kind.CHAPTER));
        action("quest", 1, 0, buttonWidth, () -> create(Kind.QUEST));
        action("duplicate", 2, 0, buttonWidth, this::duplicate);
        action("delete", 3, 0, buttonWidth, this::delete);
        action("properties", 4, 0, buttonWidth, this::properties);
        link = action("link", 5, 0, buttonWidth, () -> { linking = linking == null ? model.selected : null; if (linking == null) model.message = tr("select_prerequisite").getString(); });
        undo = action("undo", 0, 1, buttonWidth, () -> attempt(model::undo));
        redo = action("redo", 1, 1, buttonWidth, () -> attempt(model::redo));
        action("save", 2, 1, buttonWidth, model::retry);
        action("validate", 3, 1, buttonWidth, () -> QuestWorkshopClientState.INSTANCE.validate());
        action("reload", 4, 1, buttonWidth, this::reload);
        action("findings", 5, 1, buttonWidth, () -> minecraft.setScreen(new QuestWorkshopFindingsScreen(this, model)));
        var side = layout.sidebar();
        search = addRenderableWidget(new EditBox(font, side.x() + 4, side.y() + 3, side.width() - 8, 18, tr("search")));
        search.setMaxLength(128); search.setValue(model.search); search.setResponder(value -> model.search = value);
        addRenderableWidget(Button.builder(tr("campaign"), button -> {
            model.draft().sources().stream().filter(source -> source.kind() == Kind.CAMPAIGN).findFirst()
                    .ifPresent(source -> minecraft.setScreen(new QuestWorkshopPropertiesScreen(this, model, source.kind(), source.id())));
        }).bounds(side.x() + 4, side.y() + 24, side.width() - 8, 18).build());
        refresh();
    }
    private Button action(String key, int column, int row, int size, Runnable action) {
        var button = Button.builder(tr(key), ignored -> action.run()).bounds(6 + column * (size + 1), 22 + row * 20, size, 18).build();
        button.setTooltip(Tooltip.create(tr(key + "_tip"))); return addRenderableWidget(button);
    }
    private void refresh() {
        if (cached == model.draft()) return;
        cached = model.draft(); chapters = cached.sources().stream().filter(source -> source.kind() == Kind.CHAPTER).toList();
        var list = new ArrayList<Node>(); var map = new HashMap<ResourceLocation, Node>();
        for (var source : cached.sources()) if (source.kind() == Kind.QUEST) {
            var content = source.content(); var dependencies = new ArrayList<ResourceLocation>();
            if (content.has("prerequisites") && content.get("prerequisites").isJsonObject()) {
                var prerequisite = content.getAsJsonObject("prerequisites");
                if (prerequisite.has("quests") && prerequisite.get("quests").isJsonArray()) for (var id : prerequisite.getAsJsonArray("quests")) {
                    try { dependencies.add(ResourceLocation.parse(id.getAsString())); } catch (RuntimeException ignored) { /* Invalid drafts stay editable. */ }
                }
            }
            var chapter = ResourceLocation.tryParse(QuestWorkshopDocuments.text(content, "chapter", ""));
            int index = list.size();
            var node = new Node(source.id(), chapter, QuestWorkshopDocuments.text(content, "title", source.id().toString()), List.copyOf(dependencies), new QuestWorkshopDraft.Position(index % 6 * 150, index / 6 * 75)); list.add(node); map.put(node.id(), node);
        }
        nodes = List.copyOf(list); byId = Map.copyOf(map);
        if (model.chapter == null || chapters.stream().noneMatch(source -> source.id().equals(model.chapter))) model.chapter = chapters.isEmpty() ? null : chapters.getFirst().id();
        if (model.selected != null && !byId.containsKey(model.selected)) model.selected = null;
        chapterOffset = Math.clamp(chapterOffset, 0, Math.max(0, chapters.size() - 1));
    }
    private boolean visible(Node node) {
        return Objects.equals(model.chapter, node.chapter()) && (model.search.isBlank() || (node.title() + " " + node.id()).toLowerCase(Locale.ROOT).contains(model.search.toLowerCase(Locale.ROOT)));
    }
    private QuestWorkshopDraft.Position position(Node node) {
        if (node.id().equals(dragging) && preview != null) return preview;
        return model.draft().positions().getOrDefault(node.id(), node.fallback());
    }
    private QuestWorkshopLayout.Rect rectangle(Node node) {
        var p = position(node); var canvas = layout.canvas();
        return new QuestWorkshopLayout.Rect((int) (canvas.x() + model.panX + p.x() * model.zoom), (int) (canvas.y() + model.panY + p.y() * model.zoom), (int) (120 * model.zoom), (int) (45 * model.zoom));
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refresh(); graphics.fill(0, 0, width, height, LEATHER); graphics.drawString(font, title, 8, 7, PAPER, false);
        panel(graphics, layout.sidebar(), PAPER); panel(graphics, layout.canvas(), 0xFFD0C4A6);
        if (layout.inspectorVisible()) panel(graphics, layout.inspector(), PAPER);
        var side = layout.sidebar(); graphics.enableScissor(side.x(), side.y() + 46, side.x() + side.width(), side.y() + side.height());
        for (int i = chapterOffset; i < chapters.size(); i++) {
            int y = side.y() + 48 + (i - chapterOffset) * 22; if (y >= side.y() + side.height()) break;
            var source = chapters.get(i); if (source.id().equals(model.chapter)) graphics.fill(side.x() + 2, y - 2, side.x() + side.width() - 2, y + 18, 0xFFC4AF85);
            graphics.drawString(font, font.plainSubstrByWidth(QuestWorkshopDocuments.text(source.content(), "title", source.id().toString()), side.width() - 10), side.x() + 5, y + 3, INK, false);
        }
        graphics.disableScissor(); var canvas = layout.canvas();
        graphics.enableScissor(canvas.x(), canvas.y(), canvas.x() + canvas.width(), canvas.y() + canvas.height());
        int edges = 0;
        graph: for (var node : nodes) if (visible(node)) for (var id : node.prerequisites()) {
            if (++edges > 1024) break graph;
            var previous = byId.get(id); if (previous == null || !visible(previous)) continue;
            var from = rectangle(previous); var to = rectangle(node);
            line(graphics, from.x() + from.width(), from.y() + from.height() / 2, to.x(), to.y() + to.height() / 2);
        }
        for (var node : nodes) if (visible(node)) {
            var r = rectangle(node); if (r.x() + r.width() <= canvas.x() || r.x() >= canvas.x() + canvas.width() || r.y() + r.height() <= canvas.y() || r.y() >= canvas.y() + canvas.height()) continue;
            panel(graphics, r, node.id().equals(model.selected) ? 0xFFF1E7CB : PAPER);
            if (node.id().equals(linking)) graphics.fill(r.x(), r.y(), r.x() + r.width(), r.y() + 3, ACCENT);
            graphics.drawString(font, font.plainSubstrByWidth(node.title(), r.width() - 8), r.x() + 4, r.y() + 8, INK, false);
            graphics.drawString(font, font.plainSubstrByWidth(node.id().getPath(), r.width() - 8), r.x() + 4, r.y() + 21, ACCENT, false);
        }
        if (nodes.stream().noneMatch(this::visible)) graphics.drawString(font, tr("empty"), canvas.x() + 8, canvas.y() + 12, INK, false);
        graphics.disableScissor();
        if (layout.inspectorVisible()) {
            var inspector = layout.inspector(); int y = inspector.y() + 10;
            List<Component> details = new ArrayList<>(); details.add(tr("properties"));
            var selected = model.selected == null ? Optional.<QuestSourceDocument>empty() : model.draft().source(Kind.QUEST, model.selected);
            selected.ifPresent(source -> {
                details.add(Component.literal(source.id().toString())); details.add(Component.literal(QuestWorkshopDocuments.text(source.content(), "title", "")));
                details.add(tr("inspector_help"));
            });
            if (selected.isEmpty()) details.add(tr("select_quest"));
            if (linking != null) details.add(tr("link_help"));
            for (var detail : details) for (var wrapped : font.split(detail, inspector.width() - 16)) { if (y + 10 >= inspector.y() + inspector.height()) break; graphics.drawString(font, wrapped, inspector.x() + 8, y, INK, false); y += 12; }
        }
        undo.active = model.canUndo(); redo.active = model.canRedo(); link.setMessage(tr(linking == null ? "link" : "linking"));
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, font.plainSubstrByWidth(model.message, width - 12), 6, height - 28, model.conflicted() ? 0xFFFFBC80 : PAPER, false);
        graphics.drawString(font, tr("revision", model.draft().revision(), model.dirty() ? tr("unsaved") : tr("saved")), 6, height - 15, PAPER, false);
    }
    private static void panel(GuiGraphics graphics, QuestWorkshopLayout.Rect r, int color) { graphics.fill(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), INK); graphics.fill(r.x() + 1, r.y() + 1, r.x() + r.width() - 1, r.y() + r.height() - 1, color); }
    private void line(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        // Clip the segment before rasterizing; distant off-screen nodes cannot create long render loops.
        var r = layout.canvas(); double start = 0, end = 1, dx = (double) x2 - x1, dy = (double) y2 - y1;
        double[] p = {-dx, dx, -dy, dy}, q = {x1 - r.x(), r.x() + r.width() - x1, y1 - r.y(), r.y() + r.height() - y1};
        for (int i = 0; i < 4; i++) { if (p[i] == 0 && q[i] < 0) return; if (p[i] == 0) continue; double t = q[i] / p[i]; if (p[i] < 0) start = Math.max(start, t); else end = Math.min(end, t); }
        if (start > end) return; int steps = Math.max(1, (int) (Math.max(Math.abs(dx), Math.abs(dy)) * (end - start)));
        for (int i = 0; i <= steps; i++) { double t = start + (end - start) * i / steps; int x = (int) (x1 + dx * t), y = (int) (y1 + dy * t); graphics.fill(x, y, x + 1, y + 1, ACCENT); }
        graphics.fill(x2 - 4, y2 - 2, x2 + 1, y2 + 3, ACCENT);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        var side = layout.sidebar(); if (button == 0 && side.contains(x, y) && y >= side.y() + 46) {
            int index = chapterOffset + (int) ((y - side.y() - 48) / 22);
            if (index >= 0 && index < chapters.size()) { model.chapter = chapters.get(index).id(); model.selected = null; linking = null; } return true;
        }
        if (!layout.canvas().contains(x, y)) return false;
        setFocused(null);
        for (int i = nodes.size() - 1; i >= 0; i--) {
            var node = nodes.get(i); if (!visible(node) || !rectangle(node).contains(x, y)) continue;
            if (linking != null && !linking.equals(node.id()) && (button == 0 || button == 1)) { attempt(() -> model.edit(QuestWorkshopDocuments.link(model.draft(), linking, node.id(), button == 0))); return true; }
            model.selected = node.id();
            if (button == 0 && !model.conflicted()) { dragging = node.id(); preview = position(node); var world = layout.toWorld(x, y, model.panX, model.panY, model.zoom); grabX = world.x() - preview.x(); grabY = world.y() - preview.y(); }
            return true;
        }
        if (button == 0) model.selected = null; return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging != null && button == 0) { var p = layout.toWorld(x, y, model.panX, model.panY, model.zoom); preview = new QuestWorkshopDraft.Position((int) Math.clamp(p.x() - grabX, -100_000, 100_000), (int) Math.clamp(p.y() - grabY, -100_000, 100_000)); return true; }
        if (button == 2 && layout.canvas().contains(x, y)) { model.panX += dx; model.panY += dy; return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (dragging != null && button == 0) {
            var id = dragging; var p = preview; dragging = null; preview = null;
            if (!p.equals(position(byId.get(id)))) attempt(() -> model.edit(QuestWorkshopDocuments.move(model.draft(), id, p.x(), p.y()))); return true;
        }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (layout.sidebar().contains(x, y)) { chapterOffset = Math.clamp(chapterOffset - (int) scrollY, 0, Math.max(0, chapters.size() - 1)); return true; }
        if (layout.canvas().contains(x, y)) { double old = model.zoom; model.zoom = Math.clamp(old * Math.pow(1.1, scrollY), 0.75, 2); model.panX = x - layout.canvas().x() - (x - layout.canvas().x() - model.panX) * model.zoom / old; model.panY = y - layout.canvas().y() - (y - layout.canvas().y() - model.panY) * model.zoom / old; return true; }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (search.isFocused()) return super.keyPressed(key, scan, modifiers);
        if (hasControlDown()) {
            if (key == GLFW.GLFW_KEY_Z) { attempt(hasShiftDown() ? model::redo : model::undo); return true; }
            if (key == GLFW.GLFW_KEY_Y) { attempt(model::redo); return true; }
            if (key == GLFW.GLFW_KEY_S) { model.retry(); return true; }
        }
        if (getFocused() != null) return super.keyPressed(key, scan, modifiers);
        if (key == GLFW.GLFW_KEY_ESCAPE && linking != null) { linking = null; return true; }
        if (key == GLFW.GLFW_KEY_ENTER) { properties(); return true; }
        if (key == GLFW.GLFW_KEY_DELETE) { delete(); return true; }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            var ids = nodes.stream().filter(this::visible).map(Node::id).toList(); if (!ids.isEmpty()) { int current = ids.indexOf(model.selected); int delta = key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_UP ? -1 : 1; model.selected = ids.get(Math.floorMod(current + delta, ids.size())); } return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    private void create(Kind kind) {
        if (kind == Kind.QUEST && model.chapter == null) { model.message = tr("chapter_first").getString(); return; }
        minecraft.setScreen(new QuestWorkshopCreateScreen(this, model, kind, null));
    }
    private void duplicate() { ResourceLocation id = model.selected != null ? model.selected : model.chapter; if (id != null) minecraft.setScreen(new QuestWorkshopCreateScreen(this, model, model.selected == null ? Kind.CHAPTER : Kind.QUEST, id)); }
    private void delete() {
        ResourceLocation id = model.selected != null ? model.selected : model.chapter; if (id == null) return; Kind kind = model.selected == null ? Kind.CHAPTER : Kind.QUEST;
        minecraft.setScreen(new ConfirmScreen(yes -> { if (yes) attempt(() -> model.edit(QuestWorkshopDocuments.delete(model.draft(), kind, id))); minecraft.setScreen(this); }, tr("delete"), tr("delete_confirm", id)));
    }
    private void properties() { ResourceLocation id = model.selected != null ? model.selected : model.chapter; if (id != null) minecraft.setScreen(new QuestWorkshopPropertiesScreen(this, model, model.selected == null ? Kind.CHAPTER : Kind.QUEST, id)); }
    private void reload() {
        if (!model.dirty()) { QuestWorkshopClientState.INSTANCE.reload(); return; }
        minecraft.setScreen(new ConfirmScreen(yes -> { minecraft.setScreen(this); if (yes) QuestWorkshopClientState.INSTANCE.reload(); }, tr("reload"), tr("reload_confirm")));
    }
    void attempt(Runnable operation) { try { operation.run(); } catch (IllegalArgumentException exception) { model.message = exception.getMessage(); } }
    @Override public void onClose() { QuestWorkshopClientState.INSTANCE.releaseWhenSaved(); super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }
}
