package com.thunder.wildernessodysseyapi.debugoverlay.pages;

import com.thunder.wildernessodysseyapi.debugoverlay.provider.GeneralDebugDataProvider;
import net.minecraft.resources.ResourceLocation;

import java.time.Duration;

/** The default lightweight position, target, and frame summary. */
public final class GeneralDebugPage extends ProviderDebugPage {
    public GeneralDebugPage() {
        super(ResourceLocation.fromNamespaceAndPath("wildernessodysseyapi", "general"),
                "GENERAL", Duration.ofMillis(100), new GeneralDebugDataProvider());
    }
}
