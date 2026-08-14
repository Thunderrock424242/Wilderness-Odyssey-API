package com.thunder.wildernessodysseyapi.developmentstudio.client.screen;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.developmentstudio.inspection.StudioInspection;
import com.thunder.wildernessodysseyapi.developmentstudio.inspection.StudioInspectionLine;
import com.thunder.wildernessodysseyapi.developmentstudio.network.StudioEnvironmentActionPayload;
import com.thunder.wildernessodysseyapi.developmentstudio.network.StudioLocationTeleportPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** Shared data-row presentation for Phase 3 adapters with module-specific safe controls. */
final class StudioEnvironmentPage implements StudioPage {
    private final String modulePath;
    private final String title;
    private final StudioEnvironmentActionPayload.Action inspectAction;
    private final ResourceLocation campusLocation;
    private final String limitation;

    StudioEnvironmentPage(String modulePath,
                          String title,
                          StudioEnvironmentActionPayload.Action inspectAction,
                          String campusLocationPath,
                          String limitation) {
        this.modulePath = modulePath;
        this.title = title;
        this.inspectAction = inspectAction;
        this.campusLocation = campusLocationPath == null ? null : ResourceLocation.fromNamespaceAndPath(
                ModConstants.MOD_ID, campusLocationPath
        );
        this.limitation = limitation;
    }

    @Override
    public void init(StudioScreen screen) {
        int left = screen.contentLeft() + 12;
        int top = screen.contentTop() + 31;
        int width = screen.contentWidth() - 24;
        int gap = 4;
        if ("weather".equals(modulePath)) {
            int buttonWidth = Math.max(72, (width - gap * 2) / 3);
            addAction(screen, left, top, buttonWidth, "Refresh", inspectAction);
            addAction(screen, left + (buttonWidth + gap), top, buttonWidth, "Clear",
                    StudioEnvironmentActionPayload.Action.WEATHER_CLEAR);
            addAction(screen, left + (buttonWidth + gap) * 2, top, buttonWidth, "Rain",
                    StudioEnvironmentActionPayload.Action.WEATHER_RAIN);
            addAction(screen, left, top + 24, buttonWidth, "Snow",
                    StudioEnvironmentActionPayload.Action.WEATHER_SNOW);
            addAction(screen, left + (buttonWidth + gap), top + 24, buttonWidth, "Hail",
                    StudioEnvironmentActionPayload.Action.WEATHER_HAIL);
            addTeleport(screen, left + (buttonWidth + gap) * 2, top + 24, buttonWidth);
            return;
        }

        int half = Math.max(96, (width - gap) / 2);
        addAction(screen, left, top, half, "Inspect Current Position", inspectAction);
        addTeleport(screen, left + half + gap, top, half);
    }

    @Override
    public void render(StudioScreen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = screen.contentLeft() + 12;
        int y = screen.contentTop() + 12;
        int maxY = screen.contentTop() + screen.contentHeight() - 9;
        graphics.drawString(screen.font(), Component.literal(title), x, y, 0xFF8ED7FF, false);

        StudioInspection inspection = matchingInspection(screen);
        int rowY = screen.contentTop() + ("weather".equals(modulePath) ? 83 : 59);
        if (inspection == null) {
            rowY = screen.drawWrapped(graphics, Component.literal(
                            "Choose Inspect Current Position to sample the real server-owned subsystem at your current location."
                    ), x, rowY, screen.contentWidth() - 24, 0xFFD8E2E8, maxY);
        } else {
            graphics.drawString(screen.font(), Component.literal(inspection.title()), x, rowY,
                    0xFFFFD479, false);
            rowY += 15;
            for (StudioInspectionLine line : inspection.lines()) {
                if (rowY + screen.font().lineHeight >= maxY - 24) {
                    break;
                }
                graphics.drawString(screen.font(), Component.literal(line.label() + ":"),
                        x, rowY, 0xFF9FC8DE, false);
                rowY = screen.drawWrapped(graphics, Component.literal(line.value()), x + 118, rowY,
                        Math.max(80, screen.contentWidth() - 148), 0xFFE1E8EC, maxY - 24);
                rowY += 2;
            }
        }
        screen.drawWrapped(graphics, Component.literal(limitation), x,
                Math.max(rowY + 4, maxY - 20), screen.contentWidth() - 24, 0xFF91A5B0, maxY);
    }

    private StudioInspection matchingInspection(StudioScreen screen) {
        StudioInspection inspection = screen.snapshot().inspection();
        return inspection != null && modulePath.equals(inspection.providerId().getPath()) ? inspection : null;
    }

    private void addAction(StudioScreen screen,
                           int x,
                           int y,
                           int width,
                           String label,
                           StudioEnvironmentActionPayload.Action action) {
        screen.addStudioWidget(Button.builder(Component.literal(label), ignored ->
                PacketDistributor.sendToServer(new StudioEnvironmentActionPayload(action))
        ).bounds(x, y, width, 20).build());
    }

    private void addTeleport(StudioScreen screen, int x, int y, int width) {
        if (campusLocation == null) {
            return;
        }
        screen.addStudioWidget(Button.builder(Component.literal("Go to Campus Pad"), ignored ->
                PacketDistributor.sendToServer(new StudioLocationTeleportPayload(campusLocation))
        ).bounds(x, y, width, 20).build());
    }
}
