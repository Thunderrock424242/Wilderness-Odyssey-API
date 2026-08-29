package com.thunder.wildernessodysseyapi.rendering;

/**
 * Extension point for a future backend-specific spatial or temporal upscaler.
 *
 * <p>No provider is registered today: normal Minecraft rendering is the native
 * path. Implementations must reject incomplete {@link TemporalFrameData}
 * rather than assuming buffers or motion vectors exist.</p>
 */
public interface UpscalingProvider {

    String id();

    boolean isAvailable(RenderFrameContext context);

    void upscale(RenderFrameContext context);
}
