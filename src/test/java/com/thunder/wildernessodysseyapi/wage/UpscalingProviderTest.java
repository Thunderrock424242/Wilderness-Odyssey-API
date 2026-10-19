package com.thunder.wildernessodysseyapi.wage;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UpscalingProviderTest {
    @Test
    void originalProviderImplementationRemainsCompatibleAndRejectsMissingInputs() {
        UpscalingProvider provider = new UpscalingProvider() {
            @Override public String id() { return "test-spatial"; }
            @Override public boolean isAvailable(RenderFrameContext context) { return false; }
            @Override public void upscale(RenderFrameContext context) { fail("No execution handoff exists"); }
        };
        assertEquals(UpscalingProvider.Reconstruction.SPATIAL, provider.reconstruction());
        assertTrue(provider.inputUnavailableReason(RenderFrameContext.EMPTY).isPresent());
        assertTrue(provider.inputUnavailableReason(null).isPresent());
    }
}
