package com.thunder.wildernessodysseyapi.developmentstudio.client.screen;

import com.thunder.wildernessodysseyapi.developmentstudio.module.StudioModule;
import com.thunder.wildernessodysseyapi.developmentstudio.module.StudioModuleRegistry;
import com.thunder.wildernessodysseyapi.developmentstudio.network.OpenStudioPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Central modular interface for Development Studio modules and server snapshots.
 *
 * <p>The screen owns only navigation and shared layout. Bookmarks, inspection,
 * and overview presentation live in focused {@link StudioPage} implementations.</p>
 */
public final class StudioScreen extends Screen {
    private static final int OUTER_MARGIN = 12;
    private static final int NAV_BUTTON_HEIGHT = 18;
    private static final int NAV_GAP = 3;
    private static final int PANEL_COLOR = 0xED111820;
    private static final int PANEL_BORDER = 0xFF4B7388;

    private OpenStudioPayload snapshot;
    private String selectedModule;
    private StudioPage page;
    private final StudioLocationsPage locationsPage = new StudioLocationsPage();
    private final StudioInspectorPage inspectorPage = new StudioInspectorPage();
    private int contentLeft;
    private int contentTop;
    private int contentWidth;
    private int contentHeight;

    public StudioScreen(OpenStudioPayload snapshot) {
        super(Component.translatable("screen.wildernessodysseyapi.studio.title"));
        this.snapshot = snapshot;
        this.selectedModule = snapshot.initialModule();
    }

    /** Replaces the server snapshot and rebuilds only this screen's widgets. */
    public void applySnapshot(OpenStudioPayload updated) {
        this.snapshot = updated;
        this.selectedModule = updated.initialModule();
        rebuildWidgets();
    }

    @Override
    protected void init() {
        List<StudioModule> modules = List.copyOf(StudioModuleRegistry.values());
        int navigationWidth = Math.max(180, this.width - OUTER_MARGIN * 2);
        int columns = Math.max(2, Math.min(8, navigationWidth / 96));
        int rows = (modules.size() + columns - 1) / columns;
        int buttonWidth = Math.max(68, (navigationWidth - (columns - 1) * NAV_GAP) / columns);
        int navigationLeft = (this.width - (buttonWidth * columns + (columns - 1) * NAV_GAP)) / 2;
        int navigationTop = 25;

        for (int index = 0; index < modules.size(); index++) {
            StudioModule module = modules.get(index);
            int column = index % columns;
            int row = index / columns;
            Button button = Button.builder(
                    Component.translatable(module.titleKey()),
                    ignored -> selectModule(module.id().getPath())
            ).bounds(
                    navigationLeft + column * (buttonWidth + NAV_GAP),
                    navigationTop + row * (NAV_BUTTON_HEIGHT + NAV_GAP),
                    buttonWidth,
                    NAV_BUTTON_HEIGHT
            ).build();
            button.active = !module.id().getPath().equals(selectedModule);
            addRenderableWidget(button);
        }

        contentLeft = OUTER_MARGIN;
        contentTop = navigationTop + rows * (NAV_BUTTON_HEIGHT + NAV_GAP) + 5;
        contentWidth = this.width - OUTER_MARGIN * 2;
        contentHeight = Math.max(80, this.height - contentTop - OUTER_MARGIN);
        page = pageFor(selectedModule);
        page.init(this);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, this.width, this.height, 0xD0080D12);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFE7F7FF);
        graphics.fill(contentLeft - 2, contentTop - 2,
                contentLeft + contentWidth + 2, contentTop + contentHeight + 2, PANEL_BORDER);
        graphics.fill(contentLeft, contentTop,
                contentLeft + contentWidth, contentTop + contentHeight, PANEL_COLOR);
        page.render(this, graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Adds a page widget while retaining Screen's protected ownership boundary. */
    public <T extends GuiEventListener & net.minecraft.client.gui.components.Renderable & NarratableEntry>
    T addStudioWidget(T widget) {
        return addRenderableWidget(widget);
    }

    /** Rebuilds navigation and current-page widgets after a local selection change. */
    public void rebuildStudioWidgets() {
        rebuildWidgets();
    }

    public OpenStudioPayload snapshot() {
        return snapshot;
    }

    public int contentLeft() {
        return contentLeft;
    }

    public int contentTop() {
        return contentTop;
    }

    public int contentWidth() {
        return contentWidth;
    }

    public int contentHeight() {
        return contentHeight;
    }

    /** Draws bounded wrapped text and returns the next line position. */
    public int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int width, int color, int maxY) {
        for (FormattedCharSequence line : this.font.split(text, Math.max(16, width))) {
            if (y + this.font.lineHeight > maxY) {
                break;
            }
            graphics.drawString(this.font, line, x, y, color, false);
            y += this.font.lineHeight + 2;
        }
        return y;
    }

    public net.minecraft.client.gui.Font font() {
        return this.font;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void selectModule(String modulePath) {
        if (!modulePath.equals(selectedModule)) {
            selectedModule = modulePath;
            rebuildWidgets();
        }
    }

    private StudioPage pageFor(String modulePath) {
        if ("locations".equals(modulePath)) {
            return locationsPage;
        }
        if ("inspector".equals(modulePath)) {
            return inspectorPage;
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID,
                modulePath
        );
        StudioModule module = StudioModuleRegistry.get(id)
                .orElseGet(() -> StudioModuleRegistry.get("world").orElseThrow());
        return new StudioOverviewPage(module);
    }
}
