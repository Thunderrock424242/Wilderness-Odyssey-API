package com.thunder.wildernessodysseyapi.debugoverlay.pages;

import com.thunder.wildernessodysseyapi.debugoverlay.provider.SystemDebugDataProvider;
import net.minecraft.resources.ResourceLocation;

import java.time.Duration;

/** Minecraft, NeoForge, JVM, operating-system, CPU, GPU, and display identity. */
public final class SystemDebugPage extends ProviderDebugPage {
    public SystemDebugPage() {
        super(ResourceLocation.fromNamespaceAndPath("wildernessodysseyapi", "system"),
                "SYSTEM", Duration.ofSeconds(1), new SystemDebugDataProvider());
    }
}
