package com.thunder.wildernessodysseyapi.debugoverlay.pages;

import com.thunder.wildernessodysseyapi.debugoverlay.provider.NetworkDebugDataProvider;
import net.minecraft.resources.ResourceLocation;

import java.time.Duration;

/** Integrated-server or multiplayer connection information available to this client. */
public final class NetworkDebugPage extends ProviderDebugPage {
    public NetworkDebugPage() {
        super(ResourceLocation.fromNamespaceAndPath("wildernessodysseyapi", "network"),
                "NETWORK / SERVER", Duration.ofMillis(500), new NetworkDebugDataProvider());
    }
}
