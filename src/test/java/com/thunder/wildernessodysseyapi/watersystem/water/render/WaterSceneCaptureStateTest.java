package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WaterSceneCaptureStateTest {

    @Test
    void neverReusesBeforeSuccessfulCapture() {
        assertFalse(new WaterSceneCaptureState().canReuse(0L, 0L, 1, 1));
    }

    @Test
    void reusesOneCompletedSceneForSurfaceAndUnderwaterPasses() {
        WaterSceneCaptureState state = capturedState();
        assertTrue(state.canReuse(40L, 2L, 1920, 1080));
        assertTrue(state.canReuse(40L, 2L, 1920, 1080));
    }

    @Test
    void nextRenderedFrameRequiresCaptureEvenWithPausedAnimation() {
        WaterSceneCaptureState state = capturedState();
        assertFalse(state.canReuse(41L, 2L, 1920, 1080));
        state.captured(41L, 2L, 1920, 1080);
        assertTrue(state.canReuse(41L, 2L, 1920, 1080));
        assertFalse(state.canReuse(40L, 2L, 1920, 1080));
    }

    @Test
    void backendReplacementRejectsOldTextureProvenance() {
        assertFalse(capturedState().canReuse(40L, 3L, 1920, 1080));
    }

    @Test
    void resizeRejectsPreviousViewport() {
        WaterSceneCaptureState state = capturedState();
        assertFalse(state.canReuse(40L, 2L, 1280, 1080));
        assertFalse(state.canReuse(40L, 2L, 1920, 720));
    }

    @Test
    void releaseOrReloadInvalidatesEvenTheSameFrame() {
        WaterSceneCaptureState state = capturedState();
        state.invalidate();
        assertFalse(state.canReuse(40L, 2L, 1920, 1080));
        state.captured(40L, 2L, 1920, 1080);
        assertTrue(state.canReuse(40L, 2L, 1920, 1080));
    }

    private WaterSceneCaptureState capturedState() {
        WaterSceneCaptureState state = new WaterSceneCaptureState();
        state.captured(40L, 2L, 1920, 1080);
        return state;
    }
}
