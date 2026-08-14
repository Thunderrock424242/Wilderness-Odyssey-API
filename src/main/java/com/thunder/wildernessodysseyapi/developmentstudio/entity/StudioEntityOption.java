package com.thunder.wildernessodysseyapi.developmentstudio.entity;

import com.thunder.wildernessodysseyapi.developmentstudio.StudioText;
import net.minecraft.resources.ResourceLocation;

/** Minimal allowlisted entity row sent to an authorized Studio screen. */
public record StudioEntityOption(ResourceLocation id, String displayName) {
    public StudioEntityOption {
        if (id == null) {
            throw new IllegalArgumentException("Studio entity option id is required");
        }
        displayName = StudioText.singleLine(displayName, 48);
    }
}
