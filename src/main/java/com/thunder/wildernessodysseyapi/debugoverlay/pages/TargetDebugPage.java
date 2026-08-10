package com.thunder.wildernessodysseyapi.debugoverlay.pages;

import com.thunder.wildernessodysseyapi.debugoverlay.provider.TargetDebugDataProvider;
import net.minecraft.resources.ResourceLocation;

import java.time.Duration;

/** Verbose block, fluid, and entity registry/state/tag inspection. */
public final class TargetDebugPage extends ProviderDebugPage {
    public TargetDebugPage() {
        super(ResourceLocation.fromNamespaceAndPath("wildernessodysseyapi", "target"),
                "TARGET DETAILS", Duration.ofMillis(100), new TargetDebugDataProvider());
    }
}
