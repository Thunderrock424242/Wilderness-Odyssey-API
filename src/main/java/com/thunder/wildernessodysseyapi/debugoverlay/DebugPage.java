package com.thunder.wildernessodysseyapi.debugoverlay;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Public extension point for one categorized Wilderness debug HUD page.
 *
 * <p>Implementations should collect only data used by their page. Expensive or
 * slowly changing values should be cached by the page or its provider.</p>
 */
public interface DebugPage {
    /** Returns the stable registry ID used for navigation and contributions. */
    ResourceLocation id();

    /** Returns the concise header label. */
    String displayName();

    /** Collects ordered sections for the current context. */
    List<DebugSection> sections(DebugContext context);

    /** Reports whether this page applies to the current client session. */
    default boolean isAvailable(DebugContext context) {
        return true;
    }
}
