package com.thunder.wildernessodysseyapi.rendering;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalFrameDataTest {

    @Test
    void spatialAndTemporalInputsValidateDimensionsAndBackendIdentity() {
        assertTrue(frame("vk", 1920).hasSpatialReconstructionInputs());
        assertTrue(frame("vk", 1920).hasTemporalReconstructionInputs());
        assertFalse(frame("gl", 1920).hasTemporalReconstructionInputs());
        assertFalse(frame("vk", 1280).hasTemporalReconstructionInputs());
        TemporalFrameData nativeFrame = TemporalFrameData.unavailable(null, null, 0);
        assertFalse(nativeFrame.hasSpatialReconstructionInputs());
    }

    private static TemporalFrameData frame(String motionBackend, int motionWidth) {
        var resolution = new TemporalFrameData.Resolution(1920, 1080);
        return new TemporalFrameData(Optional.of(image("vk", 1920)), Optional.of(image("vk", 1920)),
                Optional.of(image(motionBackend, motionWidth)), Optional.empty(),
                Optional.of(new TemporalFrameData.Jitter(0, 0)), OptionalDouble.empty(),
                resolution, resolution, 16_000_000);
    }

    private static TemporalFrameData.ImageHandle image(String backend, int width) {
        return new TemporalFrameData.ImageHandle() {
            public String backendId() { return backend; }
            public int width() { return width; }
            public int height() { return 1080; }
        };
    }

    @Test
    void nativeFrameDoesNotFabricateTemporalInputs() {
        TemporalFrameData frame = TemporalFrameData.unavailable(
                new TemporalFrameData.Resolution(1_920, 1_080),
                new TemporalFrameData.Resolution(1_920, 1_080),
                16_000_000L
        );

        assertFalse(frame.hasTemporalReconstructionInputs());
        assertFalse(frame.color().isPresent());
        assertFalse(frame.depth().isPresent());
        assertFalse(frame.motionVectors().isPresent());
    }
}
