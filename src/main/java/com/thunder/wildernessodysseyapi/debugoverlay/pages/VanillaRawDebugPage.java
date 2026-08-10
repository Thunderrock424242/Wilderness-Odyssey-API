package com.thunder.wildernessodysseyapi.debugoverlay.pages;

import com.thunder.wildernessodysseyapi.debugoverlay.provider.VanillaDebugDataProvider;
import net.minecraft.resources.ResourceLocation;

import java.time.Duration;

/** Exact per-frame vanilla/NeoForge line collections, reorganized by the custom renderer. */
public final class VanillaRawDebugPage extends ProviderDebugPage {
    public VanillaRawDebugPage() {
        super(ResourceLocation.fromNamespaceAndPath("wildernessodysseyapi", "vanilla_raw"),
                "VANILLA RAW", Duration.ZERO, new VanillaDebugDataProvider());
    }
}
