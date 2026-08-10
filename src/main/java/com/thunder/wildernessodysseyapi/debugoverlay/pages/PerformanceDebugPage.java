package com.thunder.wildernessodysseyapi.debugoverlay.pages;

import com.thunder.wildernessodysseyapi.debugoverlay.provider.PerformanceDebugDataProvider;
import net.minecraft.resources.ResourceLocation;

import java.time.Duration;

/** Frame, heap, chunk, entity, and particle performance information. */
public final class PerformanceDebugPage extends ProviderDebugPage {
    public PerformanceDebugPage() {
        super(ResourceLocation.fromNamespaceAndPath("wildernessodysseyapi", "performance"),
                "PERFORMANCE", Duration.ofMillis(100), new PerformanceDebugDataProvider());
    }
}
