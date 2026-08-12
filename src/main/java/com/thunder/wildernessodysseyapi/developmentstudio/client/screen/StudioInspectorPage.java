package com.thunder.wildernessodysseyapi.developmentstudio.client.screen;

import com.thunder.wildernessodysseyapi.developmentstudio.inspection.StudioInspection;
import com.thunder.wildernessodysseyapi.developmentstudio.inspection.StudioInspectionLine;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Displays bounded facts produced by a registered server-side inspector. */
final class StudioInspectorPage implements StudioPage {
    @Override
    public void render(StudioScreen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = screen.contentLeft() + 12;
        int y = screen.contentTop() + 12;
        int maxY = screen.contentTop() + screen.contentHeight() - 10;
        StudioInspection inspection = screen.snapshot().inspection();
        if (inspection == null) {
            graphics.drawString(screen.font(), Component.literal("Inspector"), x, y, 0xFF8ED7FF, false);
            y += 18;
            screen.drawWrapped(graphics, Component.literal(
                            "Use the Wilderness Developer Tool on a block or living entity. The target is resolved and inspected on the server."
                    ), x, y, screen.contentWidth() - 24, 0xFFD8E2E8, maxY);
            return;
        }

        graphics.drawString(screen.font(), Component.literal(inspection.title()), x, y, 0xFF8ED7FF, false);
        y += 16;
        graphics.drawString(screen.font(), Component.literal("Provider: " + inspection.providerId()),
                x, y, 0xFF8497A3, false);
        y += 17;
        for (StudioInspectionLine line : inspection.lines()) {
            if (y >= maxY) {
                break;
            }
            graphics.drawString(screen.font(), Component.literal(line.label() + ":"), x, y, 0xFF9FC8DE, false);
            y = screen.drawWrapped(graphics, Component.literal(line.value()), x + 112, y,
                    Math.max(60, screen.contentWidth() - 140), 0xFFE1E8EC, maxY);
            y += 3;
        }
    }
}
