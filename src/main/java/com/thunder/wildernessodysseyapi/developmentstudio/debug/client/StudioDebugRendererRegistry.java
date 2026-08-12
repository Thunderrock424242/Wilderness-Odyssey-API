package com.thunder.wildernessodysseyapi.developmentstudio.debug.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client registry for optional Studio overlays; every renderer defaults off.
 *
 * <p>When no renderer is enabled the render event performs one boolean check
 * and returns without collecting overlay data.</p>
 */
public final class StudioDebugRendererRegistry {
    private static final Map<ResourceLocation, Entry> RENDERERS = new LinkedHashMap<>();
    private static boolean anyEnabled;

    private StudioDebugRendererRegistry() {
    }

    /** Registers one unique renderer in the disabled state. */
    public static synchronized void register(StudioDebugRenderer renderer) {
        if (RENDERERS.putIfAbsent(renderer.id(), new Entry(renderer, false)) != null) {
            throw new IllegalStateException("Duplicate Studio debug renderer: " + renderer.id());
        }
    }

    /** Enables or disables one renderer without changing other overlays. */
    public static synchronized boolean setEnabled(ResourceLocation id, boolean enabled) {
        Entry entry = RENDERERS.get(id);
        if (entry == null) {
            return false;
        }
        RENDERERS.put(id, new Entry(entry.renderer(), enabled));
        anyEnabled = RENDERERS.values().stream().anyMatch(Entry::enabled);
        return true;
    }

    public static boolean hasEnabledRenderers() {
        return anyEnabled;
    }

    /** Invokes only enabled renderers registered for the current render stage. */
    public static void render(Minecraft minecraft, RenderLevelStageEvent event) {
        if (!anyEnabled) {
            return;
        }
        for (Entry entry : RENDERERS.values()) {
            if (entry.enabled() && entry.renderer().stage() == event.getStage()) {
                entry.renderer().render(minecraft, event);
            }
        }
    }

    private record Entry(StudioDebugRenderer renderer, boolean enabled) {
    }
}
