package com.thunder.wildernessodysseyapi.developmentstudio.client.screen;

import com.thunder.wildernessodysseyapi.developmentstudio.module.StudioModule;
import com.thunder.wildernessodysseyapi.developmentstudio.module.StudioModuleRegistry;
import com.thunder.wildernessodysseyapi.developmentstudio.module.StudioModuleStatus;
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
    private static final int HEADER_HEIGHT = 28;
    private static final int SIDEBAR_GAP = 6;
    private static final int NAV_GAP = 2;
    private static final int PANEL_COLOR = 0xED111820;
    private static final int PANEL_BORDER = 0xFF4B7388;
    private static final int SIDEBAR_COLOR = 0xF017222B;

    private OpenStudioPayload snapshot;
    private String selectedModule;
    private StudioPage page;
    private final StudioLocationsPage locationsPage = new StudioLocationsPage();
    private final StudioInspectorPage inspectorPage = new StudioInspectorPage();
    private final StudioStructuresPage structuresPage = new StudioStructuresPage();
    private final StudioEntitiesPage entitiesPage = new StudioEntitiesPage();
    private final StudioDebugPage debugPage = new StudioDebugPage();
    private final StudioEnvironmentPage waterPage = new StudioEnvironmentPage(
            "water",
            "Water Debug",
            com.thunder.wildernessodysseyapi.developmentstudio.network.StudioEnvironmentActionPayload.Action.INSPECT_WATER,
            "water_lab",
            "The Water Lab is inspection-only in Phase 3: block snapshots never rewrite custom water attachments."
    );
    private final StudioEnvironmentPage ecosystemPage = new StudioEnvironmentPage(
            "ecosystem",
            "Ecosystem Inspection",
            com.thunder.wildernessodysseyapi.developmentstudio.network.StudioEnvironmentActionPayload.Action.INSPECT_ECOSYSTEM,
            "ecosystem_lab",
            "Inspection is bounded to 32 blocks. Accelerated stepping remains deferred and never changes the global tick rate."
    );
    private final StudioEnvironmentPage weatherPage = new StudioEnvironmentPage(
            "weather",
            "Weather Testing",
            com.thunder.wildernessodysseyapi.developmentstudio.network.StudioEnvironmentActionPayload.Action.INSPECT_WEATHER,
            "weather_lab",
            "Controls call the real Weather authority over its existing local 3x3-cell scope; no synthetic severe storm is created."
    );
    private final StudioEnvironmentPage worldgenPage = new StudioEnvironmentPage(
            "worldgen",
            "Worldgen Lab",
            com.thunder.wildernessodysseyapi.developmentstudio.network.StudioEnvironmentActionPayload.Action.INSPECT_WORLDGEN,
            "worldgen_lab",
            "Inspection never scans or generates distant chunks; safe terrain discovery remains a later phase."
    );
    private int contentLeft;
    private int contentTop;
    private int contentWidth;
    private int contentHeight;
    private int navigationLeft;
    private int navigationTop;
    private int navigationWidth;
    private int navigationHeight;
    private int deferredModuleCount;

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
        List<StudioModule> registered = List.copyOf(StudioModuleRegistry.values());
        List<StudioModule> modules = registered.stream()
                .filter(module -> module.status() == StudioModuleStatus.AVAILABLE
                        || module.id().getPath().equals(selectedModule))
                .toList();
        deferredModuleCount = (int) registered.stream()
                .filter(module -> module.status() == StudioModuleStatus.DEFERRED)
                .count();

        navigationLeft = OUTER_MARGIN;
        navigationTop = HEADER_HEIGHT;
        navigationWidth = Math.max(104, Math.min(136, this.width / 4));
        navigationHeight = Math.max(80, this.height - navigationTop - OUTER_MARGIN);
        int navigationLabelHeight = 18;
        int footerReserve = navigationHeight >= 240 && deferredModuleCount > 0 ? 22 : 6;
        int availableButtonSpace = navigationHeight - navigationLabelHeight - footerReserve;
        int buttonHeight = Math.max(14, Math.min(20,
                (availableButtonSpace - NAV_GAP * Math.max(0, modules.size() - 1)) / Math.max(1, modules.size())));
        int buttonTop = navigationTop + navigationLabelHeight;

        for (int index = 0; index < modules.size(); index++) {
            StudioModule module = modules.get(index);
            Button button = Button.builder(
                    Component.translatable(module.titleKey()),
                    ignored -> selectModule(module.id().getPath())
            ).bounds(
                    navigationLeft + 4,
                    buttonTop + index * (buttonHeight + NAV_GAP),
                    navigationWidth - 8,
                    buttonHeight
            ).build();
            button.active = !module.id().getPath().equals(selectedModule);
            addRenderableWidget(button);
        }

        contentLeft = navigationLeft + navigationWidth + SIDEBAR_GAP;
        contentTop = navigationTop;
        contentWidth = Math.max(160, this.width - contentLeft - OUTER_MARGIN);
        contentHeight = navigationHeight;
        page = pageFor(selectedModule);
        page.init(this);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // A nearly opaque application surface keeps text and controls crisp even
        // when the game has an aggressive menu-background blur configured.
        graphics.fill(0, 0, this.width, this.height, 0xFA080D12);
        graphics.drawString(this.font, this.title, OUTER_MARGIN, 9, 0xFFE7F7FF, false);

        graphics.fill(navigationLeft - 2, navigationTop - 2,
                navigationLeft + navigationWidth + 2, navigationTop + navigationHeight + 2, PANEL_BORDER);
        graphics.fill(navigationLeft, navigationTop,
                navigationLeft + navigationWidth, navigationTop + navigationHeight, SIDEBAR_COLOR);
        graphics.drawString(this.font, Component.literal("ACTIVE MODULES"),
                navigationLeft + 6, navigationTop + 6, 0xFF8ED7FF, false);
        if (deferredModuleCount > 0 && navigationHeight >= 240) {
            String roadmap = deferredModuleCount + " future modules reserved";
            graphics.drawString(this.font, Component.literal(roadmap), navigationLeft + 6,
                    navigationTop + navigationHeight - 12, 0xFF71838D, false);
        }

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
        if ("structures".equals(modulePath)) {
            return structuresPage;
        }
        if ("entities".equals(modulePath)) {
            return entitiesPage;
        }
        if ("debug".equals(modulePath)) {
            return debugPage;
        }
        if ("water".equals(modulePath)) {
            return waterPage;
        }
        if ("ecosystem".equals(modulePath)) {
            return ecosystemPage;
        }
        if ("weather".equals(modulePath)) {
            return weatherPage;
        }
        if ("worldgen".equals(modulePath)) {
            return worldgenPage;
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
