package com.thunder.wildernessodysseyapi.developmentstudio.client.screen;

import com.thunder.wildernessodysseyapi.developmentstudio.debug.client.StudioDebugRendererRegistry;
import com.thunder.wildernessodysseyapi.developmentstudio.debug.client.StudioRegionBoundsRenderer;
import com.thunder.wildernessodysseyapi.developmentstudio.debug.client.StudioStructurePreviewRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Independent local controls for disabled-by-default Studio world overlays. */
final class StudioDebugPage implements StudioPage {
    @Override
    public void init(StudioScreen screen) {
        StudioDebugRendererRegistry.bootstrapDefaults();
        int left = screen.contentLeft() + 12;
        int top = screen.contentTop() + 44;
        int width = Math.min(220, screen.contentWidth() - 24);
        screen.addStudioWidget(Button.builder(regionLabel(), ignored -> {
            StudioDebugRendererRegistry.toggle(StudioRegionBoundsRenderer.ID);
            screen.rebuildStudioWidgets();
        }).bounds(left, top, width, 20).build());
        screen.addStudioWidget(Button.builder(previewLabel(), ignored -> {
            StudioDebugRendererRegistry.toggle(StudioStructurePreviewRenderer.ID);
            screen.rebuildStudioWidgets();
        }).bounds(left, top + 25, width, 20).build());
    }

    @Override
    public void render(StudioScreen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = screen.contentLeft() + 12;
        int y = screen.contentTop() + 12;
        graphics.drawString(screen.font(), Component.literal("Debug Overlays"), x, y, 0xFF8ED7FF, false);
        screen.drawWrapped(graphics, Component.literal(
                        "Each overlay is local, independent, and off by default. Disabled overlays do not collect world data."
                ), x, y + 16, screen.contentWidth() - 24, 0xFFD8E2E8,
                screen.contentTop() + screen.contentHeight() - 10);
    }

    private static Component regionLabel() {
        return Component.literal("Test region bounds: " + state(StudioRegionBoundsRenderer.ID));
    }

    private static Component previewLabel() {
        return Component.literal("Structure preview: " + state(StudioStructurePreviewRenderer.ID));
    }

    private static String state(net.minecraft.resources.ResourceLocation id) {
        return StudioDebugRendererRegistry.isEnabled(id) ? "ON" : "OFF";
    }
}
