package com.thunder.wildernessodysseyapi.debugoverlay.pages;

import com.thunder.wildernessodysseyapi.debugoverlay.provider.RenderingDebugDataProvider;

import java.time.Duration;

/** Blaze3D, OpenGL, framebuffer, and optional renderer-integration information. */
public final class RenderingDebugPage extends ProviderDebugPage {
    public RenderingDebugPage() {
        super(RenderingDebugDataProvider.PAGE_ID,
                "RENDERING", Duration.ofMillis(500), new RenderingDebugDataProvider());
    }
}
