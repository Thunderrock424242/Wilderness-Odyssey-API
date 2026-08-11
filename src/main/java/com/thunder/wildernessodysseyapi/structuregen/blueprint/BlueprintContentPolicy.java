package com.thunder.wildernessodysseyapi.structuregen.blueprint;

import java.util.List;

/**
 * Parsed content-availability policy for one Blueprint document.
 *
 * <p>This record describes author intent only. Installed-mod discovery, required-mod checks,
 * functional-system authorization, and material selection belong to semantic validation.</p>
 *
 * @param allowInstalledModBlocks whether verified installed-mod blocks may be considered
 * @param preferredDecorativeMods ordered mod IDs preferred for decorative material choices
 * @param requiredMods mod IDs that semantic validation must require
 * @param enabledFunctionalSystems explicitly requested namespaced gameplay-system IDs
 */
public record BlueprintContentPolicy(
        boolean allowInstalledModBlocks,
        List<String> preferredDecorativeMods,
        List<String> requiredMods,
        List<String> enabledFunctionalSystems
) {

    public BlueprintContentPolicy {
        preferredDecorativeMods = List.copyOf(preferredDecorativeMods);
        requiredMods = List.copyOf(requiredMods);
        enabledFunctionalSystems = List.copyOf(enabledFunctionalSystems);
    }

    /** Returns the backward-compatible policy used when a Blueprint omits {@code contentPolicy}. */
    public static BlueprintContentPolicy defaults() {
        return new BlueprintContentPolicy(true, List.of(), List.of(), List.of());
    }
}
