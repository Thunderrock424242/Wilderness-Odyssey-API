package com.thunder.wildernessodysseyapi.rendering;

import java.util.Optional;

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

    /** Metadata only: does not register a provider or imply a supported presentation handoff. */
    default Reconstruction reconstruction() {
        return Reconstruction.SPATIAL;
    }

    /** Input validation is a prerequisite; providers must still validate backend ownership/lifetimes. */
    default Optional<String> inputUnavailableReason(RenderFrameContext context) {
        if (context == null) return Optional.of("No render frame");
        if (reconstruction() == Reconstruction.NATIVE) return Optional.empty();
        if (!context.temporalData().hasSpatialReconstructionInputs()) {
            return Optional.of("Scene color handoff unavailable or invalid");
        }
        if ((reconstruction() == Reconstruction.TEMPORAL || reconstruction() == Reconstruction.HARDWARE_AI)
                && !context.temporalData().hasTemporalReconstructionInputs()) {
            return Optional.of("Matching depth, motion vectors, jitter or valid exposure unavailable");
        }
        return Optional.empty();
    }

    enum Reconstruction { NATIVE, SPATIAL, TEMPORAL, HARDWARE_AI }
}
