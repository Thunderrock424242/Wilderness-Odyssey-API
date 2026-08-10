package com.thunder.wildernessodysseyapi.debugoverlay.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugContext;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugPage;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugPageRegistry;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugSection;
import com.thunder.wildernessodysseyapi.debugoverlay.DebugValue;
import com.thunder.wildernessodysseyapi.debugoverlay.config.DebugOverlayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Owns page selection and the compatibility-safe render fallback.
 *
 * <p>This manager never changes Minecraft's debug-enabled state. Vanilla F3
 * continues to toggle the {@code DebugScreenOverlay}; this class is active only
 * while that overlay says it should be shown.</p>
 */
public final class WildernessDebugManager {
    private static final WildernessDebugManager INSTANCE = new WildernessDebugManager();

    private final WildernessDebugOverlay overlay = new WildernessDebugOverlay();
    private final Set<ResourceLocation> loggedProviderFailures = new HashSet<>();
    private int selectedPage;
    private int scrollOffset;
    private boolean wasVisible;
    private boolean loggedRenderFailure;

    private WildernessDebugManager() {
    }

    /** Returns the client singleton that owns page selection. */
    public static WildernessDebugManager get() {
        return INSTANCE;
    }

    /** Tracks F3 open/close transitions without taking ownership of vanilla state. */
    public void syncVisibility(Minecraft minecraft) {
        boolean visible = DebugOverlayConfig.ENABLE_CUSTOM_DEBUG_HUD.get()
                && minecraft.getDebugOverlay().showDebugScreen();
        if (visible && !wasVisible) {
            scrollOffset = 0;
            if (!DebugOverlayConfig.REMEMBER_LAST_DEBUG_PAGE.get()) {
                selectedPage = 0;
            }
        }
        wasVisible = visible;
    }

    /** Selects the previous registered page with wraparound. */
    public void previousPage() {
        movePage(-1);
    }

    /** Selects the next registered page with wraparound. */
    public void nextPage() {
        movePage(1);
    }

    /** Scrolls the active page up by one flattened display row. */
    public void scrollUp() {
        moveScroll(-1);
    }

    /** Scrolls the active page down by one flattened display row. */
    public void scrollDown() {
        moveScroll(1);
    }

    /** Returns the zero-based selected page index for diagnostics and tests. */
    public int selectedPageIndex() {
        return selectedPage;
    }

    /**
     * Renders the current page and reports whether vanilla text can be cleared.
     * Returning false intentionally leaves vanilla's lists untouched as a fallback.
     */
    public boolean render(GuiGraphics graphics, List<String> vanillaLeft, List<String> vanillaRight) {
        List<DebugPage> pages = DebugPageRegistry.pages();
        if (pages.isEmpty()) {
            return false;
        }
        selectedPage = Math.floorMod(selectedPage, pages.size());
        DebugPage page = pages.get(selectedPage);
        DebugContext context = new DebugContext(
                Minecraft.getInstance(), vanillaLeft, vanillaRight, System.nanoTime()
        );

        List<DebugSection> sections;
        try {
            sections = page.isAvailable(context)
                    ? page.sections(context)
                    : List.of(DebugSection.builder("PAGE")
                    .add("State", DebugValue.unavailable("Not available in this session"))
                    .build());
        } catch (RuntimeException exception) {
            if (loggedProviderFailures.add(page.id())) {
                ModConstants.LOGGER.warn("[Wilderness Debug HUD] Provider failed for page {}; showing fallback", page.id(), exception);
            }
            sections = List.of(DebugSection.builder("PAGE DATA")
                    .add("Provider", DebugValue.unavailable(exception.getClass().getSimpleName()))
                    .build());
        }

        try {
            scrollOffset = overlay.render(
                    graphics, page, selectedPage, pages.size(), sections, scrollOffset
            );
            return true;
        } catch (RuntimeException exception) {
            if (!loggedRenderFailure) {
                loggedRenderFailure = true;
                ModConstants.LOGGER.warn("[Wilderness Debug HUD] Custom renderer failed; retaining vanilla F3 text", exception);
            }
            return false;
        }
    }

    private void movePage(int delta) {
        int pageCount = DebugPageRegistry.size();
        if (pageCount > 0) {
            selectedPage = Math.floorMod(selectedPage + delta, pageCount);
            scrollOffset = 0;
        }
    }

    private void moveScroll(int delta) {
        scrollOffset = Math.max(0, scrollOffset + delta);
    }
}
