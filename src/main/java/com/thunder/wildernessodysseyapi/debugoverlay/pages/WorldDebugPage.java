package com.thunder.wildernessodysseyapi.debugoverlay.pages;

import com.thunder.wildernessodysseyapi.debugoverlay.provider.WorldDebugDataProvider;
import net.minecraft.resources.ResourceLocation;

import java.time.Duration;

/** Detailed world, position, light, chunk, heightmap, and session information. */
public final class WorldDebugPage extends ProviderDebugPage {
    public WorldDebugPage() {
        super(ResourceLocation.fromNamespaceAndPath("wildernessodysseyapi", "world"),
                "WORLD", Duration.ofMillis(250), new WorldDebugDataProvider());
    }
}
