package com.thunder.wildernessodysseyapi.quest.client.workshop;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import java.util.List;

final class QuestWorkshopFindingsScreen extends Screen {
    private final Screen parent;
    private final QuestWorkshopModel model;
    private List<FormattedCharSequence> lines = List.of();
    private int offset;
    QuestWorkshopFindingsScreen(Screen parent, QuestWorkshopModel model) { super(QuestWorkshopScreen.tr("findings")); this.parent = parent; this.model = model; }
    @Override protected void init() { addRenderableWidget(Button.builder(QuestWorkshopScreen.tr("back"), button -> onClose()).bounds(width / 2 - 50, height - 28, 100, 20).build()); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, QuestWorkshopScreen.LEATHER); graphics.drawCenteredString(font, title, width / 2, 10, QuestWorkshopScreen.PAPER);
        lines = font.split(Component.literal(model.findings.isEmpty() ? QuestWorkshopScreen.tr("validate_first").getString() : String.join("\n\n", model.findings)), width - 24);
        offset = Math.clamp(offset, 0, Math.max(0, lines.size() - Math.max(1, (height - 70) / 12)));
        for (int i = offset, y = 34; i < lines.size() && y < height - 40; i++, y += 12) graphics.drawString(font, lines.get(i), 12, y, QuestWorkshopScreen.PAPER, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) { offset = Math.max(0, offset - (int) (sy * 3)); return true; }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
