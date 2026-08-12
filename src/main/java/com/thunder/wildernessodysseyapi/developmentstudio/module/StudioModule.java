package com.thunder.wildernessodysseyapi.developmentstudio.module;

import net.minecraft.resources.ResourceLocation;

/**
 * Metadata for one modular Development Studio category.
 *
 * @param id stable module id used by screens and network-open requests
 * @param titleKey translation key for the navigation label
 * @param descriptionKey translation key for the module summary
 * @param status current implementation status
 */
public record StudioModule(
        ResourceLocation id,
        String titleKey,
        String descriptionKey,
        StudioModuleStatus status
) {
    public StudioModule {
        if (id == null || titleKey == null || titleKey.isBlank()
                || descriptionKey == null || descriptionKey.isBlank() || status == null) {
            throw new IllegalArgumentException("Studio module metadata must be complete");
        }
    }
}
